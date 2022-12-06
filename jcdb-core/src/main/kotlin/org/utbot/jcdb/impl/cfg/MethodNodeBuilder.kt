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

package org.utbot.jcdb.impl.cfg

import org.objectweb.asm.Handle
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.IntInsnNode
import org.objectweb.asm.tree.InvokeDynamicInsnNode
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.LookupSwitchInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.MultiANewArrayInsnNode
import org.objectweb.asm.tree.ParameterNode
import org.objectweb.asm.tree.TableSwitchInsnNode
import org.objectweb.asm.tree.TryCatchBlockNode
import org.objectweb.asm.tree.TypeInsnNode
import org.objectweb.asm.tree.VarInsnNode
import org.utbot.jcdb.api.BsmDoubleArg
import org.utbot.jcdb.api.BsmFloatArg
import org.utbot.jcdb.api.BsmHandle
import org.utbot.jcdb.api.BsmIntArg
import org.utbot.jcdb.api.BsmLongArg
import org.utbot.jcdb.api.BsmMethodTypeArg
import org.utbot.jcdb.api.BsmStringArg
import org.utbot.jcdb.api.BsmTypeArg
import org.utbot.jcdb.api.JcMethod
import org.utbot.jcdb.api.JcRawAddExpr
import org.utbot.jcdb.api.JcRawAndExpr
import org.utbot.jcdb.api.JcRawArgument
import org.utbot.jcdb.api.JcRawArrayAccess
import org.utbot.jcdb.api.JcRawAssignInst
import org.utbot.jcdb.api.JcRawBool
import org.utbot.jcdb.api.JcRawByte
import org.utbot.jcdb.api.JcRawCallExpr
import org.utbot.jcdb.api.JcRawCallInst
import org.utbot.jcdb.api.JcRawCastExpr
import org.utbot.jcdb.api.JcRawCatchInst
import org.utbot.jcdb.api.JcRawChar
import org.utbot.jcdb.api.JcRawClassConstant
import org.utbot.jcdb.api.JcRawCmpExpr
import org.utbot.jcdb.api.JcRawCmpgExpr
import org.utbot.jcdb.api.JcRawCmplExpr
import org.utbot.jcdb.api.JcRawComplexValue
import org.utbot.jcdb.api.JcRawDivExpr
import org.utbot.jcdb.api.JcRawDouble
import org.utbot.jcdb.api.JcRawDynamicCallExpr
import org.utbot.jcdb.api.JcRawEnterMonitorInst
import org.utbot.jcdb.api.JcRawEqExpr
import org.utbot.jcdb.api.JcRawExitMonitorInst
import org.utbot.jcdb.api.JcRawExpr
import org.utbot.jcdb.api.JcRawFieldRef
import org.utbot.jcdb.api.JcRawFloat
import org.utbot.jcdb.api.JcRawGeExpr
import org.utbot.jcdb.api.JcRawGotoInst
import org.utbot.jcdb.api.JcRawGtExpr
import org.utbot.jcdb.api.JcRawIfInst
import org.utbot.jcdb.api.JcRawInstList
import org.utbot.jcdb.api.JcRawInstanceOfExpr
import org.utbot.jcdb.api.JcRawInt
import org.utbot.jcdb.api.JcRawInterfaceCallExpr
import org.utbot.jcdb.api.JcRawLabelInst
import org.utbot.jcdb.api.JcRawLabelRef
import org.utbot.jcdb.api.JcRawLeExpr
import org.utbot.jcdb.api.JcRawLengthExpr
import org.utbot.jcdb.api.JcRawLocal
import org.utbot.jcdb.api.JcRawLong
import org.utbot.jcdb.api.JcRawLtExpr
import org.utbot.jcdb.api.JcRawMethodConstant
import org.utbot.jcdb.api.JcRawMulExpr
import org.utbot.jcdb.api.JcRawNegExpr
import org.utbot.jcdb.api.JcRawNeqExpr
import org.utbot.jcdb.api.JcRawNewArrayExpr
import org.utbot.jcdb.api.JcRawNewExpr
import org.utbot.jcdb.api.JcRawNullConstant
import org.utbot.jcdb.api.JcRawOrExpr
import org.utbot.jcdb.api.JcRawRemExpr
import org.utbot.jcdb.api.JcRawReturnInst
import org.utbot.jcdb.api.JcRawShlExpr
import org.utbot.jcdb.api.JcRawShort
import org.utbot.jcdb.api.JcRawShrExpr
import org.utbot.jcdb.api.JcRawSpecialCallExpr
import org.utbot.jcdb.api.JcRawStaticCallExpr
import org.utbot.jcdb.api.JcRawStringConstant
import org.utbot.jcdb.api.JcRawSubExpr
import org.utbot.jcdb.api.JcRawSwitchInst
import org.utbot.jcdb.api.JcRawThis
import org.utbot.jcdb.api.JcRawThrowInst
import org.utbot.jcdb.api.JcRawUshrExpr
import org.utbot.jcdb.api.JcRawValue
import org.utbot.jcdb.api.JcRawVirtualCallExpr
import org.utbot.jcdb.api.JcRawXorExpr
import org.utbot.jcdb.api.PredefinedPrimitives
import org.utbot.jcdb.api.TypeName
import org.utbot.jcdb.api.cfg.JcRawExprVisitor
import org.utbot.jcdb.api.cfg.JcRawInstVisitor
import org.utbot.jcdb.api.isStatic
import org.utbot.jcdb.api.jvmName
import org.utbot.jcdb.impl.cfg.util.baseElementType
import org.utbot.jcdb.impl.cfg.util.internalDesc
import org.utbot.jcdb.impl.cfg.util.isDWord
import org.utbot.jcdb.impl.cfg.util.isPrimitive
import org.utbot.jcdb.impl.cfg.util.jvmClassName
import org.utbot.jcdb.impl.cfg.util.jvmTypeName
import org.utbot.jcdb.impl.cfg.util.typeName

private val PredefinedPrimitives.smallIntegers get() = setOf(boolean, byte, char, short, int)

private val TypeName.shortInt
    get() = when (this.typeName) {
        PredefinedPrimitives.long -> 1
        in PredefinedPrimitives.smallIntegers -> 0
        PredefinedPrimitives.float -> 2
        PredefinedPrimitives.double -> 3
        else -> 4
    }
private val TypeName.longInt
    get() = when (this.typeName) {
        PredefinedPrimitives.boolean -> 5
        PredefinedPrimitives.byte -> 5
        PredefinedPrimitives.short -> 7
        PredefinedPrimitives.char -> 6
        PredefinedPrimitives.int -> 0
        PredefinedPrimitives.long -> 1
        PredefinedPrimitives.float -> 2
        PredefinedPrimitives.double -> 3
        else -> 4
    }

private val TypeName.typeInt
    get() = when (typeName) {
        PredefinedPrimitives.char -> Opcodes.T_CHAR
        PredefinedPrimitives.boolean -> Opcodes.T_BOOLEAN
        PredefinedPrimitives.byte -> Opcodes.T_BYTE
        PredefinedPrimitives.double -> Opcodes.T_DOUBLE
        PredefinedPrimitives.float -> Opcodes.T_FLOAT
        PredefinedPrimitives.int -> Opcodes.T_INT
        PredefinedPrimitives.long -> Opcodes.T_LONG
        PredefinedPrimitives.short -> Opcodes.T_SHORT
        else -> error("$typeName is not primitive type")
    }

class MethodNodeBuilder(
    val method: JcMethod,
    val instList: JcRawInstList
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
        mn.exceptions = method.exceptions.map { it.simpleName.jvmName() }
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
            val argument = JcRawArgument(parameter.index, null, parameter.type)
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
        tryCatchNodeList += TryCatchBlockNode(
            label(inst.startInclusive),
            label(inst.endExclusive),
            label(inst.handler),
            inst.throwable.typeName.internalDesc
        )
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
            PredefinedPrimitives.float -> Opcodes.FCMPG
            else -> Opcodes.DCMPG
        }
        currentInsnList.add(InsnNode(opcode))
        updateStackInfo(-1)
    }

    override fun visitJcRawCmplExpr(expr: JcRawCmplExpr) {
        expr.lhv.accept(this)
        expr.rhv.accept(this)
        val opcode = when (expr.lhv.typeName.typeName) {
            PredefinedPrimitives.float -> Opcodes.FCMPL
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
                        PredefinedPrimitives.long -> when (targetType.typeName) {
                            PredefinedPrimitives.int -> Opcodes.L2I
                            PredefinedPrimitives.float -> Opcodes.L2F
                            PredefinedPrimitives.double -> Opcodes.L2D
                            else -> error("Impossible cast from $originalType to $targetType")
                        }

                        in PredefinedPrimitives.smallIntegers -> when (targetType.typeName) {
                            PredefinedPrimitives.long -> Opcodes.I2L
                            PredefinedPrimitives.float -> Opcodes.I2F
                            PredefinedPrimitives.double -> Opcodes.I2D
                            PredefinedPrimitives.byte -> Opcodes.I2B
                            PredefinedPrimitives.char -> Opcodes.I2C
                            PredefinedPrimitives.short -> Opcodes.I2S
                            PredefinedPrimitives.boolean -> Opcodes.NOP
                            else -> error("Impossible cast from $originalType to $targetType")
                        }

                        PredefinedPrimitives.float -> when (targetType.typeName) {
                            PredefinedPrimitives.int -> Opcodes.F2I
                            PredefinedPrimitives.long -> Opcodes.F2L
                            PredefinedPrimitives.double -> Opcodes.F2D
                            else -> error("Impossible cast from $originalType to $targetType")
                        }

                        PredefinedPrimitives.double -> when (targetType.typeName) {
                            PredefinedPrimitives.int -> Opcodes.D2I
                            PredefinedPrimitives.long -> Opcodes.D2L
                            PredefinedPrimitives.float -> Opcodes.D2F
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
        val component = expr.typeName.baseElementType()
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
                expr.callCiteMethodName,
                "(${expr.callCiteArgTypes.joinToString("") { it.jvmTypeName }})${expr.callCiteReturnType.jvmTypeName}",
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
        if (expr.returnType != PredefinedPrimitives.void.typeName())
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
        if (expr.returnType != PredefinedPrimitives.void.typeName())
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
        if (expr.returnType != PredefinedPrimitives.void.typeName())
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
        if (expr.returnType != PredefinedPrimitives.void.typeName())
            updateStackInfo(1)
    }

    override fun visitJcRawThis(value: JcRawThis) {
        currentInsnList.add(loadValue(value))
    }

    override fun visitJcRawArgument(value: JcRawArgument) {
        currentInsnList.add(loadValue(value))
    }

    override fun visitJcRawLocal(value: JcRawLocal) {
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
        currentInsnList.add(LdcInsnNode(value.className.jvmClassName))
        updateStackInfo(1)
    }

    override fun visitJcRawMethodConstant(value: JcRawMethodConstant) {
        error("Could not load method constant $value")
    }
}
