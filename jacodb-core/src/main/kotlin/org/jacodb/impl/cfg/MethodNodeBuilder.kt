/*
 *  Copyright 2022 UnitTestBot contributors (utbot.org)
 * <p>
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * <p>
 *  http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.jacodb.impl.cfg

import org.jacodb.api.JcMethod
import org.jacodb.api.PredefinedPrimitives
import org.jacodb.api.TypeName
import org.jacodb.api.cfg.*
import org.jacodb.impl.cfg.util.*
import org.objectweb.asm.Handle
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*

private val PredefinedPrimitives.smallIntegers get() = setOf(Boolean, Byte, Char, Short, Int)

private val TypeName.shortInt
    get() = when (this.typeName) {
        PredefinedPrimitives.Long -> 1
        in PredefinedPrimitives.smallIntegers -> 0
        PredefinedPrimitives.Float -> 2
        PredefinedPrimitives.Double -> 3
        else -> 4
    }
private val TypeName.longInt
    get() = when (this.typeName) {
        PredefinedPrimitives.Boolean -> 5
        PredefinedPrimitives.Byte -> 5
        PredefinedPrimitives.Short -> 7
        PredefinedPrimitives.Char -> 6
        PredefinedPrimitives.Int -> 0
        PredefinedPrimitives.Long -> 1
        PredefinedPrimitives.Float -> 2
        PredefinedPrimitives.Double -> 3
        else -> 4
    }

private val TypeName.typeInt
    get() = when (typeName) {
        PredefinedPrimitives.Char -> Opcodes.T_CHAR
        PredefinedPrimitives.Boolean -> Opcodes.T_BOOLEAN
        PredefinedPrimitives.Byte -> Opcodes.T_BYTE
        PredefinedPrimitives.Double -> Opcodes.T_DOUBLE
        PredefinedPrimitives.Float -> Opcodes.T_FLOAT
        PredefinedPrimitives.Int -> Opcodes.T_INT
        PredefinedPrimitives.Long -> Opcodes.T_LONG
        PredefinedPrimitives.Short -> Opcodes.T_SHORT
        else -> error("$typeName is not primitive type")
    }

class MethodNodeBuilder(
    val method: JcMethod,
    val instList: JcInstList<JcRawInst>
) : JcRawInstVisitor<Unit>, JcRawExprVisitor<Unit> {
    private var localIndex = 0
    private var stackSize = 0
    private var maxStack = 0
    private val locals = mutableMapOf<JcRawValue, Int>()
    private val labelRefMap = instList.instructions.filterIsInstance<JcRawLabelInst>().associateBy { it.ref }
    private val labels = mutableMapOf<JcRawLabelInst, LabelNode>()
    private val currentInsnList = InsnList()
    private val tryCatchNodeList = mutableListOf<TryCatchBlockNode>()

    fun build(): MethodNode {
        initializeFrame(method)
        buildInstructionList()
        val mn = MethodNode()
        mn.name = method.name
        mn.desc = method.description
        mn.access = method.access
        mn.parameters = method.parameters.map {
            ParameterNode(
                if (it.name == it.type.typeName) null else it.name,
                if (it.access == Opcodes.ACC_PUBLIC) 0 else it.access
            )
        }
        mn.exceptions = method.exceptions.map { it.jvmClassName }
        mn.instructions = currentInsnList
        mn.tryCatchBlocks = tryCatchNodeList
        mn.maxLocals = localIndex
        mn.maxStack = maxStack + 1
        return mn
    }

    private fun initializeFrame(method: JcMethod) {
        if (!method.isStatic) {
            val thisRef = JcRawThis(method.enclosingClass.name.typeName())
            locals[thisRef] = localIndex++
        }
        for (parameter in method.parameters) {
            val argument = JcRawArgument.of(parameter.index, parameter.name, parameter.type)
            locals[argument] = localIndex
            if (argument.typeName.isDWord) localIndex += 2
            else localIndex++
        }
    }

    private fun buildInstructionList() {
        for (inst in instList.instructions) {
            inst.accept(this)
        }
    }

    private fun local(jcRawValue: JcRawValue): Int = locals.getOrPut(jcRawValue) {
        val index = localIndex
        localIndex += when {
            jcRawValue.typeName.isDWord -> 2
            else -> 1
        }
        index
    }

    private fun label(jcRawLabelInst: JcRawLabelInst): LabelNode = labels.getOrPut(jcRawLabelInst) { LabelNode() }
    private fun label(jcRawLabelRef: JcRawLabelRef): LabelNode = label(labelRefMap.getValue(jcRawLabelRef))

    private fun updateStackInfo(inc: Int) {
        stackSize += inc
        assert(stackSize >= 0)
        if (stackSize > maxStack) maxStack = stackSize
    }

    private fun loadValue(jcRawValue: JcRawValue): AbstractInsnNode {
        val local = local(jcRawValue)
        val opcode = Opcodes.ILOAD + jcRawValue.typeName.shortInt
        updateStackInfo(1)
        return VarInsnNode(opcode, local)
    }

    private fun storeValue(jcRawValue: JcRawValue): AbstractInsnNode {
        val local = local(jcRawValue)
        val opcode = Opcodes.ISTORE + jcRawValue.typeName.shortInt
        updateStackInfo(-1)
        return VarInsnNode(opcode, local)
    }

    private fun complexStore(lhv: JcRawComplexValue, value: JcRawExpr) = when (lhv) {
        is JcRawFieldRef -> {
            lhv.instance?.accept(this)
            value.accept(this)
            val opcode = if (lhv.instance == null) Opcodes.PUTSTATIC else Opcodes.PUTFIELD
            currentInsnList.add(
                FieldInsnNode(
                    opcode,
                    lhv.declaringClass.jvmClassName,
                    lhv.fieldName,
                    lhv.typeName.jvmTypeName
                )
            )
            val stackChange = 1 + if (lhv.instance == null) 0 else 1
            updateStackInfo(-stackChange)
        }

        is JcRawArrayAccess -> {
            lhv.array.accept(this)
            lhv.index.accept(this)
            value.accept(this)
            val opcode = Opcodes.IASTORE + lhv.typeName.longInt
            currentInsnList.add(InsnNode(opcode))
            updateStackInfo(-2)
        }

        else -> error("Unexpected complex value: ${lhv::class}")
    }

    override fun visitJcRawAssignInst(inst: JcRawAssignInst) {
        when (val lhv = inst.lhv) {
            is JcRawComplexValue -> complexStore(lhv, inst.rhv)
            else -> {
                inst.rhv.accept(this)
                currentInsnList.add(storeValue(lhv))
            }
        }
    }

    override fun visitJcRawEnterMonitorInst(inst: JcRawEnterMonitorInst) {
        currentInsnList.add(loadValue(inst.monitor))
        currentInsnList.add(InsnNode(Opcodes.MONITORENTER))
        updateStackInfo(-1)
    }

    override fun visitJcRawExitMonitorInst(inst: JcRawExitMonitorInst) {
        currentInsnList.add(loadValue(inst.monitor))
        currentInsnList.add(InsnNode(Opcodes.MONITOREXIT))
        updateStackInfo(-1)
    }

    override fun visitJcRawCallInst(inst: JcRawCallInst) {
        inst.callExpr.accept(this)
    }

    override fun visitJcRawLabelInst(inst: JcRawLabelInst) {
        currentInsnList.add(label(inst))
    }

    override fun visitJcRawLineNumberInst(inst: JcRawLineNumberInst) {
        currentInsnList.add(LineNumberNode(inst.lineNumber, label(inst.start)))
    }

    override fun visitJcRawReturnInst(inst: JcRawReturnInst) {
        inst.returnValue?.accept(this)
        val opcode = when (inst.returnValue) {
            null -> Opcodes.RETURN
            else -> Opcodes.IRETURN + inst.returnValue!!.typeName.shortInt
        }
        currentInsnList.add(InsnNode(opcode))
        updateStackInfo(-stackSize)
    }

    override fun visitJcRawThrowInst(inst: JcRawThrowInst) {
        currentInsnList.add(loadValue(inst.throwable))
        currentInsnList.add(InsnNode(Opcodes.ATHROW))
        updateStackInfo(-stackSize)
    }

    override fun visitJcRawCatchInst(inst: JcRawCatchInst) {
        tryCatchNodeList += inst.entries.map {
            TryCatchBlockNode(
                label(it.startInclusive),
                label(it.endExclusive),
                label(inst.handler),
                it.acceptedThrowable.internalDesc
            )
        }
        updateStackInfo(1)
        currentInsnList.add(storeValue(inst.throwable))
    }

    override fun visitJcRawGotoInst(inst: JcRawGotoInst) {
        currentInsnList.add(JumpInsnNode(Opcodes.GOTO, label(inst.target)))
        updateStackInfo(-stackSize)
    }

    override fun visitJcRawIfInst(inst: JcRawIfInst) {
        val trueTarget = label(inst.trueBranch)
        val falseTarget = label(inst.falseBranch)
        val cond = inst.condition
        val (zeroValue, zeroCmpOpcode, defaultOpcode) = when (cond) {
            is JcRawEqExpr -> when {
                cond.lhv.typeName.isPrimitive -> Triple(JcRawInt(0), Opcodes.IFEQ, Opcodes.IF_ICMPEQ)
                else -> Triple(JcRawNull(), Opcodes.IFNULL, Opcodes.IF_ACMPEQ)
            }

            is JcRawNeqExpr -> when {
                cond.lhv.typeName.isPrimitive -> Triple(JcRawInt(0), Opcodes.IFNE, Opcodes.IF_ICMPNE)
                else -> Triple(JcRawNull(), Opcodes.IFNONNULL, Opcodes.IF_ACMPNE)
            }

            is JcRawGeExpr -> Triple(JcRawInt(0), Opcodes.IFGE, Opcodes.IF_ICMPGE)
            is JcRawGtExpr -> Triple(JcRawInt(0), Opcodes.IFGT, Opcodes.IF_ICMPGT)
            is JcRawLeExpr -> Triple(JcRawInt(0), Opcodes.IFLE, Opcodes.IF_ICMPLE)
            is JcRawLtExpr -> Triple(JcRawInt(0), Opcodes.IFLT, Opcodes.IF_ICMPLT)
            else -> error("Unknown condition expr: $cond")
        }
        currentInsnList.add(
            when {
                cond.lhv == zeroValue -> {
                    cond.rhv.accept(this)
                    JumpInsnNode(zeroCmpOpcode, trueTarget)
                }

                cond.rhv == zeroValue -> {
                    cond.lhv.accept(this)
                    JumpInsnNode(zeroCmpOpcode, trueTarget)
                }

                else -> {
                    cond.lhv.accept(this)
                    cond.rhv.accept(this)
                    JumpInsnNode(defaultOpcode, trueTarget)
                }
            }
        )
        currentInsnList.add(JumpInsnNode(Opcodes.GOTO, falseTarget))
        updateStackInfo(-stackSize)
    }

    override fun visitJcRawSwitchInst(inst: JcRawSwitchInst) {
        currentInsnList.add(loadValue(inst.key))

        val branches = inst.branches
        val keys = inst.branches.keys.map { (it as JcRawInt).value }.sorted().toIntArray()
        val default = label(inst.default)
        val labels = keys.map { label(branches[JcRawInt(it)]!!) }.toTypedArray()

        val isConsecutive = keys.withIndex().all { (index, value) ->
            if (index > 0) value == keys[index - 1] + 1
            else true
        } && keys.size > 1

        currentInsnList.add(
            when {
                isConsecutive -> TableSwitchInsnNode(keys.first(), keys.last(), default, *labels)
                else -> LookupSwitchInsnNode(default, keys, labels)
            }
        )
        updateStackInfo(-stackSize)
    }

    override fun visitJcRawAddExpr(expr: JcRawAddExpr) {
        expr.lhv.accept(this)
        expr.rhv.accept(this)
        val opcode = Opcodes.IADD + expr.typeName.shortInt
        currentInsnList.add(InsnNode(opcode))
        updateStackInfo(-1)
    }

    override fun visitJcRawAndExpr(expr: JcRawAndExpr) {
        expr.lhv.accept(this)
        expr.rhv.accept(this)
        val opcode = Opcodes.IAND + expr.typeName.shortInt
        currentInsnList.add(InsnNode(opcode))
        updateStackInfo(-1)
    }

    override fun visitJcRawCmpExpr(expr: JcRawCmpExpr) {
        expr.lhv.accept(this)
        expr.rhv.accept(this)
        currentInsnList.add(InsnNode(Opcodes.LCMP))
        updateStackInfo(-1)
    }

    override fun visitJcRawCmpgExpr(expr: JcRawCmpgExpr) {
        expr.lhv.accept(this)
        expr.rhv.accept(this)
        val opcode = when (expr.lhv.typeName.typeName) {
            PredefinedPrimitives.Float -> Opcodes.FCMPG
            else -> Opcodes.DCMPG
        }
        currentInsnList.add(InsnNode(opcode))
        updateStackInfo(-1)
    }

    override fun visitJcRawCmplExpr(expr: JcRawCmplExpr) {
        expr.lhv.accept(this)
        expr.rhv.accept(this)
        val opcode = when (expr.lhv.typeName.typeName) {
            PredefinedPrimitives.Float -> Opcodes.FCMPL
            else -> Opcodes.DCMPL
        }
        currentInsnList.add(InsnNode(opcode))
        updateStackInfo(-1)
    }

    override fun visitJcRawDivExpr(expr: JcRawDivExpr) {
        expr.lhv.accept(this)
        expr.rhv.accept(this)
        val opcode = Opcodes.IDIV + expr.typeName.shortInt
        currentInsnList.add(InsnNode(opcode))
        updateStackInfo(-1)
    }

    override fun visitJcRawMulExpr(expr: JcRawMulExpr) {
        expr.lhv.accept(this)
        expr.rhv.accept(this)
        val opcode = Opcodes.IMUL + expr.typeName.shortInt
        currentInsnList.add(InsnNode(opcode))
        updateStackInfo(-1)
    }

    override fun visitJcRawEqExpr(expr: JcRawEqExpr) {
        error("$expr should not be visited during IR to ASM conversion")
    }

    override fun visitJcRawNeqExpr(expr: JcRawNeqExpr) {
        error("$expr should not be visited during IR to ASM conversion")
    }

    override fun visitJcRawGeExpr(expr: JcRawGeExpr) {
        error("$expr should not be visited during IR to ASM conversion")
    }

    override fun visitJcRawGtExpr(expr: JcRawGtExpr) {
        error("$expr should not be visited during IR to ASM conversion")
    }

    override fun visitJcRawLeExpr(expr: JcRawLeExpr) {
        error("$expr should not be visited during IR to ASM conversion")
    }

    override fun visitJcRawLtExpr(expr: JcRawLtExpr) {
        error("$expr should not be visited during IR to ASM conversion")
    }

    override fun visitJcRawOrExpr(expr: JcRawOrExpr) {
        expr.lhv.accept(this)
        expr.rhv.accept(this)
        val opcode = Opcodes.IOR + expr.typeName.shortInt
        currentInsnList.add(InsnNode(opcode))
        updateStackInfo(-1)
    }

    override fun visitJcRawRemExpr(expr: JcRawRemExpr) {
        expr.lhv.accept(this)
        expr.rhv.accept(this)
        val opcode = Opcodes.IREM + expr.typeName.shortInt
        currentInsnList.add(InsnNode(opcode))
        updateStackInfo(-1)
    }

    override fun visitJcRawShlExpr(expr: JcRawShlExpr) {
        expr.lhv.accept(this)
        expr.rhv.accept(this)
        val opcode = Opcodes.ISHL + expr.typeName.shortInt
        currentInsnList.add(InsnNode(opcode))
        updateStackInfo(-1)
    }

    override fun visitJcRawShrExpr(expr: JcRawShrExpr) {
        expr.lhv.accept(this)
        expr.rhv.accept(this)
        val opcode = Opcodes.ISHR + expr.typeName.shortInt
        currentInsnList.add(InsnNode(opcode))
        updateStackInfo(-1)
    }

    override fun visitJcRawSubExpr(expr: JcRawSubExpr) {
        expr.lhv.accept(this)
        expr.rhv.accept(this)
        val opcode = Opcodes.ISUB + expr.typeName.shortInt
        currentInsnList.add(InsnNode(opcode))
        updateStackInfo(-1)
    }

    override fun visitJcRawUshrExpr(expr: JcRawUshrExpr) {
        expr.lhv.accept(this)
        expr.rhv.accept(this)
        val opcode = Opcodes.IUSHR + expr.typeName.shortInt
        currentInsnList.add(InsnNode(opcode))
        updateStackInfo(-1)
    }

    override fun visitJcRawXorExpr(expr: JcRawXorExpr) {
        expr.lhv.accept(this)
        expr.rhv.accept(this)
        val opcode = Opcodes.IXOR + expr.typeName.shortInt
        currentInsnList.add(InsnNode(opcode))
        updateStackInfo(-1)
    }

    override fun visitJcRawLengthExpr(expr: JcRawLengthExpr) {
        expr.array.accept(this)
        currentInsnList.add(InsnNode(Opcodes.ARRAYLENGTH))
    }

    override fun visitJcRawNegExpr(expr: JcRawNegExpr) {
        expr.operand.accept(this)
        currentInsnList.add(InsnNode(Opcodes.INEG + expr.typeName.shortInt))
    }

    override fun visitJcRawCastExpr(expr: JcRawCastExpr) {
        expr.operand.accept(this)

        val originalType = expr.operand.typeName
        val targetType = expr.typeName

        currentInsnList.add(
            when {
                originalType.isPrimitive && targetType.isPrimitive -> {
                    val opcode = when (originalType.typeName) {
                        PredefinedPrimitives.Long -> when (targetType.typeName) {
                            PredefinedPrimitives.Int -> Opcodes.L2I
                            PredefinedPrimitives.Float -> Opcodes.L2F
                            PredefinedPrimitives.Double -> Opcodes.L2D
                            else -> error("Impossible cast from $originalType to $targetType")
                        }

                        in PredefinedPrimitives.smallIntegers -> when (targetType.typeName) {
                            PredefinedPrimitives.Long -> Opcodes.I2L
                            PredefinedPrimitives.Float -> Opcodes.I2F
                            PredefinedPrimitives.Double -> Opcodes.I2D
                            PredefinedPrimitives.Byte -> Opcodes.I2B
                            PredefinedPrimitives.Char -> Opcodes.I2C
                            PredefinedPrimitives.Short -> Opcodes.I2S
                            PredefinedPrimitives.Boolean -> Opcodes.NOP
                            else -> error("Impossible cast from $originalType to $targetType")
                        }

                        PredefinedPrimitives.Float -> when (targetType.typeName) {
                            PredefinedPrimitives.Int -> Opcodes.F2I
                            PredefinedPrimitives.Long -> Opcodes.F2L
                            PredefinedPrimitives.Double -> Opcodes.F2D
                            else -> error("Impossible cast from $originalType to $targetType")
                        }

                        PredefinedPrimitives.Double -> when (targetType.typeName) {
                            PredefinedPrimitives.Int -> Opcodes.D2I
                            PredefinedPrimitives.Long -> Opcodes.D2L
                            PredefinedPrimitives.Float -> Opcodes.D2F
                            else -> error("Impossible cast from $originalType to $targetType")
                        }

                        else -> error("Impossible cast from $originalType to $targetType")
                    }
                    InsnNode(opcode)
                }

                else -> TypeInsnNode(Opcodes.CHECKCAST, targetType.internalDesc)
            }
        )
    }

    override fun visitJcRawNewExpr(expr: JcRawNewExpr) {
        currentInsnList.add(TypeInsnNode(Opcodes.NEW, expr.typeName.internalDesc))
        updateStackInfo(1)
    }

    override fun visitJcRawNewArrayExpr(expr: JcRawNewArrayExpr) {
        val component = run {
            var type = expr.typeName
            repeat(expr.dimensions.size) {
                type = type.elementType()
            }
            type
        }
        expr.dimensions.map { it.accept(this) }
        currentInsnList.add(
            when {
                expr.dimensions.size > 1 -> MultiANewArrayInsnNode(expr.typeName.jvmTypeName, expr.dimensions.size)
                component.isPrimitive -> IntInsnNode(Opcodes.NEWARRAY, component.typeInt)
                else -> TypeInsnNode(Opcodes.ANEWARRAY, component.internalDesc)
            }
        )
        updateStackInfo(1)
    }

    override fun visitJcRawInstanceOfExpr(expr: JcRawInstanceOfExpr) {
        expr.operand.accept(this)
        currentInsnList.add(TypeInsnNode(Opcodes.INSTANCEOF, expr.typeName.internalDesc))
    }


    private val BsmHandle.asAsmHandle: Handle
        get() = Handle(
            tag,
            declaringClass.jvmClassName,
            name,
            "(${argTypes.joinToString("") { it.jvmTypeName }})${returnType.jvmTypeName}",
            isInterface
        )
    private val JcRawMethodConstant.asAsmType: Type
        get() = Type.getType(
            argumentTypes.joinToString(
                prefix = "(",
                postfix = ")${returnType.jvmTypeName}",
                separator = ""
            ) { it.jvmTypeName }
        )

    private val TypeName.asAsmType: Type get() = Type.getType(this.jvmTypeName)

    override fun visitJcRawDynamicCallExpr(expr: JcRawDynamicCallExpr) {
        expr.args.forEach { it.accept(this) }
        currentInsnList.add(
            InvokeDynamicInsnNode(
                expr.callSiteMethodName,
                "(${expr.callSiteArgTypes.joinToString("") { it.jvmTypeName }})${expr.callSiteReturnType.jvmTypeName}",
                expr.bsm.asAsmHandle,
                *expr.bsmArgs.map {
                    when (it) {
                        is BsmIntArg -> it.value
                        is BsmFloatArg -> it.value
                        is BsmLongArg -> it.value
                        is BsmDoubleArg -> it.value
                        is BsmStringArg -> it.value
                        is BsmMethodTypeArg -> Type.getMethodType(
                            it.returnType.asAsmType,
                            *it.argumentTypes.map { arg -> arg.asAsmType }.toTypedArray()
                        )

                        is BsmTypeArg -> it.typeName.asAsmType
                        is BsmHandle -> it.asAsmHandle
                        else -> error("Unknown arg of bsm: $it")
                    }
                }.toTypedArray()
            )
        )
        updateStackInfo(-expr.args.size + 1)
    }

    private val JcRawCallExpr.methodDesc get() = "(${argumentTypes.joinToString("") { it.jvmTypeName }})${returnType.jvmTypeName}"

    override fun visitJcRawVirtualCallExpr(expr: JcRawVirtualCallExpr) {
        expr.instance.accept(this)
        expr.args.forEach { it.accept(this) }
        currentInsnList.add(
            MethodInsnNode(
                Opcodes.INVOKEVIRTUAL,
                expr.declaringClass.jvmClassName,
                expr.methodName,
                expr.methodDesc
            )
        )
        updateStackInfo(-(expr.args.size + 1))
        if (expr.returnType != PredefinedPrimitives.Void.typeName())
            updateStackInfo(1)
    }

    override fun visitJcRawInterfaceCallExpr(expr: JcRawInterfaceCallExpr) {
        expr.instance.accept(this)
        expr.args.forEach { it.accept(this) }
        currentInsnList.add(
            MethodInsnNode(
                Opcodes.INVOKEINTERFACE,
                expr.declaringClass.jvmClassName,
                expr.methodName,
                expr.methodDesc
            )
        )
        updateStackInfo(-(expr.args.size + 1))
        if (expr.returnType != PredefinedPrimitives.Void.typeName())
            updateStackInfo(1)
    }

    override fun visitJcRawStaticCallExpr(expr: JcRawStaticCallExpr) {
        expr.args.forEach { it.accept(this) }
        currentInsnList.add(
            MethodInsnNode(
                Opcodes.INVOKESTATIC,
                expr.declaringClass.jvmClassName,
                expr.methodName,
                expr.methodDesc
            )
        )
        updateStackInfo(-expr.args.size)
        if (expr.returnType != PredefinedPrimitives.Void.typeName())
            updateStackInfo(1)
    }

    override fun visitJcRawSpecialCallExpr(expr: JcRawSpecialCallExpr) {
        expr.instance.accept(this)
        expr.args.forEach { it.accept(this) }
        currentInsnList.add(
            MethodInsnNode(
                Opcodes.INVOKESPECIAL,
                expr.declaringClass.jvmClassName,
                expr.methodName,
                expr.methodDesc
            )
        )
        updateStackInfo(-(expr.args.size + 1))
        if (expr.returnType != PredefinedPrimitives.Void.typeName())
            updateStackInfo(1)
    }

    override fun visitJcRawThis(value: JcRawThis) {
        currentInsnList.add(loadValue(value))
    }

    override fun visitJcRawArgument(value: JcRawArgument) {
        currentInsnList.add(loadValue(value))
    }

    override fun visitJcRawLocalVar(value: JcRawLocalVar) {
        currentInsnList.add(loadValue(value))
    }

    override fun visitJcRawFieldRef(value: JcRawFieldRef) {
        value.instance?.accept(this)
        val opcode = if (value.instance == null) Opcodes.GETSTATIC else Opcodes.GETFIELD
        currentInsnList.add(
            FieldInsnNode(
                opcode,
                value.declaringClass.jvmClassName,
                value.fieldName,
                value.typeName.jvmTypeName
            )
        )
        val stackChange = if (value.instance == null) 1 else 0
        updateStackInfo(stackChange)
    }

    override fun visitJcRawArrayAccess(value: JcRawArrayAccess) {
        value.array.accept(this)
        value.index.accept(this)
        val opcode = Opcodes.IALOAD + value.typeName.longInt
        currentInsnList.add(InsnNode(opcode))
        updateStackInfo(-1)
    }

    override fun visitJcRawBool(value: JcRawBool) {
        currentInsnList.add(InsnNode(if (value.value) Opcodes.ICONST_1 else Opcodes.ICONST_0))
        updateStackInfo(1)
    }

    override fun visitJcRawByte(value: JcRawByte) {
        currentInsnList.add(IntInsnNode(Opcodes.BIPUSH, value.value.toInt()))
        updateStackInfo(1)
    }

    override fun visitJcRawChar(value: JcRawChar) {
        currentInsnList.add(LdcInsnNode(value.value.code))
        updateStackInfo(1)
    }

    override fun visitJcRawShort(value: JcRawShort) {
        currentInsnList.add(IntInsnNode(Opcodes.SIPUSH, value.value.toInt()))
        updateStackInfo(1)
    }

    override fun visitJcRawInt(value: JcRawInt) {
        currentInsnList.add(
            when (value.value) {
                in -1..5 -> InsnNode(Opcodes.ICONST_0 + value.value)
                in Byte.MIN_VALUE..Byte.MAX_VALUE -> IntInsnNode(Opcodes.BIPUSH, value.value)
                in Short.MIN_VALUE..Short.MAX_VALUE -> IntInsnNode(Opcodes.SIPUSH, value.value)
                else -> LdcInsnNode(value.value)
            }
        )
        updateStackInfo(1)
    }

    override fun visitJcRawLong(value: JcRawLong) {
        currentInsnList.add(
            when (value.value) {
                in 0..1 -> InsnNode(Opcodes.LCONST_0 + value.value.toInt())
                else -> LdcInsnNode(value.value)
            }
        )
        updateStackInfo(1)
    }

    override fun visitJcRawFloat(value: JcRawFloat) {
        currentInsnList.add(
            when (value.value) {
                0.0F -> InsnNode(Opcodes.FCONST_0)
                1.0F -> InsnNode(Opcodes.FCONST_1)
                2.0F -> InsnNode(Opcodes.FCONST_2)
                else -> LdcInsnNode(value.value)
            }
        )
        updateStackInfo(1)
    }

    override fun visitJcRawDouble(value: JcRawDouble) {
        currentInsnList.add(
            when (value.value) {
                0.0 -> InsnNode(Opcodes.DCONST_0)
                1.0 -> InsnNode(Opcodes.DCONST_1)
                else -> LdcInsnNode(value.value)
            }
        )
        updateStackInfo(1)
    }

    override fun visitJcRawNullConstant(value: JcRawNullConstant) {
        currentInsnList.add(InsnNode(Opcodes.ACONST_NULL))
        updateStackInfo(1)
    }

    override fun visitJcRawStringConstant(value: JcRawStringConstant) {
        currentInsnList.add(LdcInsnNode(value.value))
        updateStackInfo(1)
    }

    override fun visitJcRawClassConstant(value: JcRawClassConstant) {
        currentInsnList.add(LdcInsnNode(Type.getType(value.className.jvmTypeName)))
        updateStackInfo(1)
    }

    override fun visitJcRawMethodConstant(value: JcRawMethodConstant) {
        error("Could not load method constant $value")
    }

    override fun visitJcRawMethodType(value: JcRawMethodType) {
        error("Could not load method constant $value")
    }

}
