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

package org.utbot.jacodb.impl.cfg

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap
import org.objectweb.asm.Handle
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.FrameNode
import org.objectweb.asm.tree.IincInsnNode
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.IntInsnNode
import org.objectweb.asm.tree.InvokeDynamicInsnNode
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.LineNumberNode
import org.objectweb.asm.tree.LookupSwitchInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.MultiANewArrayInsnNode
import org.objectweb.asm.tree.TableSwitchInsnNode
import org.objectweb.asm.tree.TryCatchBlockNode
import org.objectweb.asm.tree.TypeInsnNode
import org.objectweb.asm.tree.VarInsnNode
import org.utbot.jacodb.api.JcMethod
import org.utbot.jacodb.api.PredefinedPrimitives
import org.utbot.jacodb.api.TypeName
import org.utbot.jacodb.api.cfg.BsmArg
import org.utbot.jacodb.api.cfg.BsmDoubleArg
import org.utbot.jacodb.api.cfg.BsmFloatArg
import org.utbot.jacodb.api.cfg.BsmHandle
import org.utbot.jacodb.api.cfg.BsmIntArg
import org.utbot.jacodb.api.cfg.BsmLongArg
import org.utbot.jacodb.api.cfg.BsmMethodTypeArg
import org.utbot.jacodb.api.cfg.BsmStringArg
import org.utbot.jacodb.api.cfg.BsmTypeArg
import org.utbot.jacodb.api.cfg.JcRawAddExpr
import org.utbot.jacodb.api.cfg.JcRawAndExpr
import org.utbot.jacodb.api.cfg.JcRawArgument
import org.utbot.jacodb.api.cfg.JcRawArrayAccess
import org.utbot.jacodb.api.cfg.JcRawAssignInst
import org.utbot.jacodb.api.cfg.JcRawCallInst
import org.utbot.jacodb.api.cfg.JcRawCastExpr
import org.utbot.jacodb.api.cfg.JcRawCatchInst
import org.utbot.jacodb.api.cfg.JcRawClassConstant
import org.utbot.jacodb.api.cfg.JcRawCmpExpr
import org.utbot.jacodb.api.cfg.JcRawCmpgExpr
import org.utbot.jacodb.api.cfg.JcRawCmplExpr
import org.utbot.jacodb.api.cfg.JcRawDivExpr
import org.utbot.jacodb.api.cfg.JcRawDynamicCallExpr
import org.utbot.jacodb.api.cfg.JcRawEnterMonitorInst
import org.utbot.jacodb.api.cfg.JcRawEqExpr
import org.utbot.jacodb.api.cfg.JcRawExitMonitorInst
import org.utbot.jacodb.api.cfg.JcRawFieldRef
import org.utbot.jacodb.api.cfg.JcRawGeExpr
import org.utbot.jacodb.api.cfg.JcRawGotoInst
import org.utbot.jacodb.api.cfg.JcRawGtExpr
import org.utbot.jacodb.api.cfg.JcRawIfInst
import org.utbot.jacodb.api.cfg.JcRawInst
import org.utbot.jacodb.api.cfg.JcRawInstanceOfExpr
import org.utbot.jacodb.api.cfg.JcRawInterfaceCallExpr
import org.utbot.jacodb.api.cfg.JcRawLabelInst
import org.utbot.jacodb.api.cfg.JcRawLabelRef
import org.utbot.jacodb.api.cfg.JcRawLeExpr
import org.utbot.jacodb.api.cfg.JcRawLengthExpr
import org.utbot.jacodb.api.cfg.JcRawLocal
import org.utbot.jacodb.api.cfg.JcRawLtExpr
import org.utbot.jacodb.api.cfg.JcRawMethodConstant
import org.utbot.jacodb.api.cfg.JcRawMulExpr
import org.utbot.jacodb.api.cfg.JcRawNegExpr
import org.utbot.jacodb.api.cfg.JcRawNeqExpr
import org.utbot.jacodb.api.cfg.JcRawNewArrayExpr
import org.utbot.jacodb.api.cfg.JcRawNewExpr
import org.utbot.jacodb.api.cfg.JcRawOrExpr
import org.utbot.jacodb.api.cfg.JcRawRemExpr
import org.utbot.jacodb.api.cfg.JcRawReturnInst
import org.utbot.jacodb.api.cfg.JcRawShlExpr
import org.utbot.jacodb.api.cfg.JcRawShrExpr
import org.utbot.jacodb.api.cfg.JcRawSimpleValue
import org.utbot.jacodb.api.cfg.JcRawSpecialCallExpr
import org.utbot.jacodb.api.cfg.JcRawStaticCallExpr
import org.utbot.jacodb.api.cfg.JcRawStringConstant
import org.utbot.jacodb.api.cfg.JcRawSubExpr
import org.utbot.jacodb.api.cfg.JcRawSwitchInst
import org.utbot.jacodb.api.cfg.JcRawThis
import org.utbot.jacodb.api.cfg.JcRawThrowInst
import org.utbot.jacodb.api.cfg.JcRawUshrExpr
import org.utbot.jacodb.api.cfg.JcRawValue
import org.utbot.jacodb.api.cfg.JcRawVirtualCallExpr
import org.utbot.jacodb.api.cfg.JcRawXorExpr
import org.utbot.jacodb.api.ext.isStatic
import org.utbot.jacodb.impl.cfg.util.CLASS_CLASS
import org.utbot.jacodb.impl.cfg.util.ExprMapper
import org.utbot.jacodb.impl.cfg.util.METHOD_HANDLE_CLASS
import org.utbot.jacodb.impl.cfg.util.NULL
import org.utbot.jacodb.impl.cfg.util.OBJECT_CLASS
import org.utbot.jacodb.impl.cfg.util.STRING_CLASS
import org.utbot.jacodb.impl.cfg.util.THROWABLE_CLASS
import org.utbot.jacodb.impl.cfg.util.asArray
import org.utbot.jacodb.impl.cfg.util.elementType
import org.utbot.jacodb.impl.cfg.util.isArray
import org.utbot.jacodb.impl.cfg.util.isDWord
import org.utbot.jacodb.impl.cfg.util.typeName
import java.util.*

private fun Int.toPrimitiveType(): TypeName = when (this) {
    Opcodes.T_CHAR -> PredefinedPrimitives.char
    Opcodes.T_BOOLEAN -> PredefinedPrimitives.boolean
    Opcodes.T_BYTE -> PredefinedPrimitives.byte
    Opcodes.T_DOUBLE -> PredefinedPrimitives.double
    Opcodes.T_FLOAT -> PredefinedPrimitives.float
    Opcodes.T_INT -> PredefinedPrimitives.int
    Opcodes.T_LONG -> PredefinedPrimitives.long
    Opcodes.T_SHORT -> PredefinedPrimitives.short
    else -> error("Unknown primitive type opcode: $this")
}.typeName()

private val TOP = "TOP".typeName()
private val UNINIT_THIS = "UNINIT_THIS".typeName()

private fun parsePrimitiveType(opcode: Int) = when (opcode) {
    0 -> TOP
    1 -> PredefinedPrimitives.int.typeName()
    2 -> PredefinedPrimitives.float.typeName()
    3 -> PredefinedPrimitives.double.typeName()
    4 -> PredefinedPrimitives.long.typeName()
    5 -> NULL
    6 -> UNINIT_THIS
    else -> error("Unknown opcode in primitive type parsing: $opcode")
}

private fun parseType(any: Any): TypeName = when (any) {
    is String -> any.typeName()
    is Int -> parsePrimitiveType(any)
    is LabelNode -> {
        val newNode: TypeInsnNode = any.run {
            var cur: AbstractInsnNode = this
            var typeInsnNode: TypeInsnNode?
            do {
                typeInsnNode = cur.next as? TypeInsnNode
                cur = cur.next
            } while (typeInsnNode == null)
            typeInsnNode
        }
        newNode.desc.typeName()
    }

    else -> error("Unexpected local type $any")
}

private fun List<*>?.parseLocals(): SortedMap<Int, TypeName> {
    if (this == null) return sortedMapOf()
    val result = mutableMapOf<Int, TypeName>()
    var index = 0
    for (any in this) {
        val type = parseType(any!!)
        result[index] = type
        when {
            type.isDWord -> index += 2
            else -> ++index
        }
    }
    return result.toSortedMap()
}

private fun List<*>?.parseStack(): SortedMap<Int, TypeName> {
    if (this == null) return sortedMapOf()
    val result = mutableMapOf<Int, TypeName>()
    for ((index, any) in this.withIndex()) {
        val type = parseType(any!!)
        result[index] = type
    }
    return result.toSortedMap()
}

private val Type.asTypeName: BsmArg
    get() = when (this.sort) {
        Type.VOID -> BsmTypeArg(PredefinedPrimitives.void.typeName())
        Type.BOOLEAN -> BsmTypeArg(PredefinedPrimitives.boolean.typeName())
        Type.CHAR -> BsmTypeArg(PredefinedPrimitives.char.typeName())
        Type.BYTE -> BsmTypeArg(PredefinedPrimitives.byte.typeName())
        Type.SHORT -> BsmTypeArg(PredefinedPrimitives.short.typeName())
        Type.INT -> BsmTypeArg(PredefinedPrimitives.int.typeName())
        Type.FLOAT -> BsmTypeArg(PredefinedPrimitives.float.typeName())
        Type.LONG -> BsmTypeArg(PredefinedPrimitives.long.typeName())
        Type.DOUBLE -> BsmTypeArg(PredefinedPrimitives.double.typeName())
        Type.ARRAY -> BsmTypeArg((elementType.asTypeName as BsmTypeArg).typeName.asArray())
        Type.OBJECT -> BsmTypeArg(className.typeName())
        Type.METHOD -> BsmMethodTypeArg(
            this.argumentTypes.map { (it.asTypeName as BsmTypeArg).typeName },
            (this.returnType.asTypeName as BsmTypeArg).typeName
        )

        else -> error("Unknown type: $this")
    }

private val AbstractInsnNode.isBranchingInst
    get() = when (this) {
        is JumpInsnNode -> true
        is TableSwitchInsnNode -> true
        is LookupSwitchInsnNode -> true
        is InsnNode -> opcode == Opcodes.ATHROW
        else -> false
    }

private val AbstractInsnNode.isTerminateInst
    get() = this is InsnNode && (this.opcode == Opcodes.ATHROW || this.opcode in Opcodes.IRETURN..Opcodes.RETURN)

private val TryCatchBlockNode.typeOrDefault get() = this.type ?: THROWABLE_CLASS

class RawInstListBuilder(
    val method: JcMethod,
    private val methodNode: MethodNode
) {
    private val frames = mutableMapOf<AbstractInsnNode, Frame>()
    private val labels = mutableMapOf<LabelNode, JcRawLabelInst>()
    private lateinit var lastFrameState: FrameState
    private lateinit var currentFrame: Frame
    private val ENTRY = InsnNode(-1)

    private val predecessors = mutableMapOf<AbstractInsnNode, MutableList<AbstractInsnNode>>()
    private val instructions = mutableMapOf<AbstractInsnNode, MutableList<JcRawInst>>()
    private val laterAssignments = mutableMapOf<AbstractInsnNode, MutableMap<Int, JcRawValue>>()
    private val laterStackAssignments = mutableMapOf<AbstractInsnNode, MutableMap<Int, JcRawValue>>()
    private val localTypeRefinement = mutableMapOf<JcRawLocal, JcRawLocal>()
    private var labelCounter = 0
    private var localCounter = 0
    private var argCounter = 0

    fun build(): JcRawInstListImpl {
        buildGraph()

        buildInstructions()
        buildRequiredAssignments()
        buildRequiredGotos()

        val originalInstructionList = JcRawInstListImpl(methodNode.instructions.flatMap { instructionList(it) })

        // after all the frame info resolution we can refine type info for some local variables,
        // so we replace all the old versions of the variables with the type refined ones
        val localsNormalizedInstructionList = originalInstructionList.map(ExprMapper(localTypeRefinement.toMap()))
        return Simplifier().simplify(method.enclosingClass.classpath, localsNormalizedInstructionList)
    }

    private fun buildInstructions() {
        currentFrame = createInitialFrame()
        frames[ENTRY] = currentFrame
        for (insn in methodNode.instructions) {
            when (insn) {
                is InsnNode -> buildInsnNode(insn)
                is FieldInsnNode -> buildFieldInsnNode(insn)
                is FrameNode -> buildFrameNode(insn)
                is IincInsnNode -> buildIincInsnNode(insn)
                is IntInsnNode -> buildIntInsnNode(insn)
                is InvokeDynamicInsnNode -> buildInvokeDynamicInsn(insn)
                is JumpInsnNode -> buildJumpInsnNode(insn)
                is LabelNode -> buildLabelNode(insn)
                is LdcInsnNode -> buildLdcInsnNode(insn)
                is LineNumberNode -> {}
                is LookupSwitchInsnNode -> buildLookupSwitchInsnNode(insn)
                is MethodInsnNode -> buildMethodInsnNode(insn)
                is MultiANewArrayInsnNode -> buildMultiANewArrayInsnNode(insn)
                is TableSwitchInsnNode -> buildTableSwitchInsnNode(insn)
                is TypeInsnNode -> buildTypeInsnNode(insn)
                is VarInsnNode -> buildVarInsnNode(insn)
                else -> error("Unknown insn node ${insn::class}")
            }
            frames[insn] = currentFrame
        }
    }

    // `laterAssignments` and `laterStackAssignments` are maps of variable assignments
    // that we need to add to the instruction list after the construction process to ensure
    // liveness of the variables on every step of the method. We cannot add them during the construction
    // because some of them are unknown at that stage (e.g. because of loops)
    private fun buildRequiredAssignments() {
        for ((insn, assignments) in laterAssignments) {
            val insnList = instructionList(insn)
            val frame = frames[insn]!!
            for ((variable, value) in assignments) {
                if (value != frame[variable]) {
                    if (insn.isBranchingInst || insn.isTerminateInst) {
                        insnList.add(insnList.lastIndex, JcRawAssignInst(value, frame[variable]!!))
                    } else {
                        insnList += JcRawAssignInst(value, frame[variable]!!)
                    }
                }
            }
        }
        for ((insn, assignments) in laterStackAssignments) {
            val insnList = instructionList(insn)
            val frame = frames[insn]!!
            for ((variable, value) in assignments) {
                if (value != frame.stack[variable]) {
                    if (insn.isBranchingInst || insn.isTerminateInst) {
                        insnList.add(insnList.lastIndex, JcRawAssignInst(value, frame.stack[variable]))
                    } else {
                        insnList += JcRawAssignInst(value, frame.stack[variable])
                    }
                }
            }
        }
    }

    // adds the `goto` instructions to ensure consistency in the instruction list:
    // every jump is show explicitly with some branching instruction
    private fun buildRequiredGotos() {
        for (insn in methodNode.instructions) {
            if (methodNode.tryCatchBlocks.any { it.handler == insn }) continue

            val predecessors = predecessors.getOrDefault(insn, emptyList())
            if (predecessors.size > 1) {
                for (predecessor in predecessors) {
                    if (!predecessor.isBranchingInst) {
                        val label = when (insn) {
                            is LabelNode -> labelRef(insn)
                            else -> {
                                val newLabel = nextLabel()
                                instructionList(insn).add(0, newLabel)
                                newLabel.ref
                            }
                        }
                        instructionList(predecessor).add(JcRawGotoInst(label))
                    }
                }
            }
        }
    }

    /**
     * represets a frame state: information about types of local variables and stack variables
     * needed to handle ASM FrameNode instructions
     */
    private data class FrameState(
        val locals: SortedMap<Int, TypeName>,
        val stack: SortedMap<Int, TypeName>
    ) {
        companion object {
            fun parseNew(insn: FrameNode): FrameState {
                return FrameState(insn.local.parseLocals(), insn.stack.parseStack())
            }
        }

        fun appendFrame(insn: FrameNode): FrameState {
            val maxKey = this.locals.keys.maxOrNull() ?: -1
            val lastType = locals[maxKey]
            val insertKey = when {
                lastType == null -> 0
                lastType.isDWord -> maxKey + 2
                else -> maxKey + 1
            }
            val appendedLocals = insn.local.parseLocals()
            val newLocals = this.locals.toMutableMap()
            for ((index, type) in appendedLocals) {
                newLocals[insertKey + index] = type
            }
            return copy(locals = newLocals.toSortedMap(), stack = sortedMapOf())
        }

        fun dropFrame(inst: FrameNode): FrameState {
            val newLocals = this.locals.toList().dropLast(inst.local.size).toMap()
            return copy(locals = newLocals.toSortedMap(), stack = sortedMapOf())
        }

        fun copy0(): FrameState = this.copy(stack = sortedMapOf())

        fun copy1(insn: FrameNode): FrameState {
            val newStack = insn.stack.parseStack()
            return this.copy(stack = newStack)
        }
    }

    /**
     * represents the bytecode Frame: a set of active local variables and stack variables
     * during the execution of the instruction
     */
    private data class Frame(
        val locals: PersistentMap<Int, JcRawValue>,
        val stack: PersistentList<JcRawValue>
    ) {
        fun put(variable: Int, value: JcRawValue): Frame = copy(locals = locals.put(variable, value), stack = stack)
        operator fun get(variable: Int) = locals[variable]

        fun push(value: JcRawValue) = copy(locals = locals, stack = stack.add(value))
        fun peek() = stack.last()
        fun pop(): Pair<Frame, JcRawValue> =
            copy(locals = locals, stack = stack.removeAt(stack.lastIndex)) to stack.last()
    }

    private fun pop(): JcRawValue {
        val (frame, value) = currentFrame.pop()
        currentFrame = frame
        return value
    }

    private fun push(value: JcRawValue) {
        currentFrame = currentFrame.push(value)
    }

    private fun peek(): JcRawValue = currentFrame.peek()

    private fun local(variable: Int) = currentFrame.locals.getValue(variable)

    private fun local(variable: Int, expr: JcRawValue): JcRawAssignInst? {
        val oldVar = currentFrame.locals[variable]
        return if (oldVar != null) {
            if (oldVar.typeName == expr.typeName) {
                JcRawAssignInst(oldVar, expr)
            } else if (expr is JcRawSimpleValue) {
                currentFrame = currentFrame.put(variable, expr)
                null
            } else {
                val assignment = nextRegister(expr.typeName)
                currentFrame = currentFrame.put(variable, assignment)
                JcRawAssignInst(oldVar, expr)
            }
        } else {
            val newLocal = nextRegister(expr.typeName)
            val result = JcRawAssignInst(newLocal, expr)
            currentFrame = currentFrame.put(variable, newLocal)
            result
        }
    }

    private fun label(insnNode: LabelNode): JcRawLabelInst = labels.getOrPut(insnNode, ::nextLabel)

    private fun labelRef(insnNode: LabelNode): JcRawLabelRef = label(insnNode).ref

    private fun instructionList(insn: AbstractInsnNode) = instructions.getOrPut(insn, ::mutableListOf)


    private fun nextRegister(typeName: TypeName): JcRawValue {
        return JcRawLocal("%${localCounter++}", typeName)
    }
    private fun nextLabel(): JcRawLabelInst = JcRawLabelInst("#${labelCounter++}")

    private fun buildGraph() {
        methodNode.instructions.first?.let {
            predecessors.getOrPut(it, ::mutableListOf).add(ENTRY)
        }
        for (insn in methodNode.instructions) {
            if (insn is JumpInsnNode) {
                predecessors.getOrPut(insn.label, ::mutableListOf).add(insn)
                if (insn.opcode != Opcodes.GOTO) {
                    predecessors.getOrPut(insn.next, ::mutableListOf).add(insn)
                }
            } else if (insn is TableSwitchInsnNode) {
                predecessors.getOrPut(insn.dflt, ::mutableListOf).add(insn)
                insn.labels.forEach {
                    predecessors.getOrPut(it, ::mutableListOf).add(insn)
                }
            } else if (insn is LookupSwitchInsnNode) {
                predecessors.getOrPut(insn.dflt, ::mutableListOf).add(insn)
                insn.labels.forEach {
                    predecessors.getOrPut(it, ::mutableListOf).add(insn)
                }
            } else if (insn.isTerminateInst) {
                continue
            } else if (insn.next != null) {
                predecessors.getOrPut(insn.next, ::mutableListOf).add(insn)
            }
        }
        for (tryCatchBlock in methodNode.tryCatchBlocks) {
            val preStart = predecessors.getOrDefault(tryCatchBlock.start, setOf(ENTRY))
            predecessors.getOrPut(tryCatchBlock.handler, ::mutableListOf).addAll(preStart)

            var current: AbstractInsnNode = tryCatchBlock.start
            while (current != tryCatchBlock.end) {
                predecessors.getOrPut(tryCatchBlock.handler, ::mutableListOf).add(current)
                current = current.next
            }
        }
    }

    private fun createInitialFrame(): Frame {
        val locals = hashMapOf<Int, JcRawValue>()
        argCounter = 0
        if (!method.isStatic) {
            val thisRef = JcRawThis(method.enclosingClass.name.typeName())
            locals[argCounter++] = thisRef
        }
        for (parameter in method.parameters) {
            val argument = JcRawArgument(parameter.index, null, parameter.type)
            locals[argCounter] = argument
            if (argument.typeName.isDWord) argCounter += 2
            else argCounter++
        }

        return Frame(locals.toPersistentMap(), persistentListOf())
    }

    private fun buildInsnNode(insn: InsnNode) {
        when (insn.opcode) {
            Opcodes.NOP -> Unit
            in Opcodes.ACONST_NULL..Opcodes.DCONST_1 -> buildConstant(insn)
            in Opcodes.IALOAD..Opcodes.SALOAD -> buildArrayRead(insn)
            in Opcodes.IASTORE..Opcodes.SASTORE -> buildArrayStore(insn)
            in Opcodes.POP..Opcodes.POP2 -> buildPop(insn)
            in Opcodes.DUP..Opcodes.DUP2_X2 -> buildDup(insn)
            Opcodes.SWAP -> buildSwap()
            in Opcodes.IADD..Opcodes.DREM -> buildBinary(insn)
            in Opcodes.INEG..Opcodes.DNEG -> buildUnary(insn)
            in Opcodes.ISHL..Opcodes.LXOR -> buildBinary(insn)
            in Opcodes.I2L..Opcodes.I2S -> buildCast(insn)
            in Opcodes.LCMP..Opcodes.DCMPG -> buildCmp(insn)
            in Opcodes.IRETURN..Opcodes.RETURN -> buildReturn(insn)
            Opcodes.ARRAYLENGTH -> buildUnary(insn)
            Opcodes.ATHROW -> buildThrow(insn)
            in Opcodes.MONITORENTER..Opcodes.MONITOREXIT -> buildMonitor(insn)
            else -> error("Unknown insn opcode: ${insn.opcode}")
        }
    }

    private fun buildConstant(insn: InsnNode) {
        val constant = when (val opcode = insn.opcode) {
            Opcodes.ACONST_NULL -> JcRawNull()
            Opcodes.ICONST_M1 -> JcRawInt(-1)
            in Opcodes.ICONST_0..Opcodes.ICONST_5 -> JcRawInt(opcode - Opcodes.ICONST_0)
            in Opcodes.LCONST_0..Opcodes.LCONST_1 -> JcRawLong((opcode - Opcodes.LCONST_0).toLong())
            in Opcodes.FCONST_0..Opcodes.FCONST_2 -> JcRawFloat((opcode - Opcodes.FCONST_0).toFloat())
            in Opcodes.DCONST_0..Opcodes.DCONST_1 -> JcRawDouble((opcode - Opcodes.DCONST_0).toDouble())
            else -> error("Unknown constant opcode: $opcode")
        }
        push(constant)
    }

    private fun buildArrayRead(insn: InsnNode) {
        val index = pop()
        val arrayRef = pop()
        val read = JcRawArrayAccess(arrayRef, index, arrayRef.typeName.elementType())

        val assignment = nextRegister(read.typeName)
        instructionList(insn).add(JcRawAssignInst(assignment, read))
        push(assignment)
    }

    private fun buildArrayStore(insn: InsnNode) {
        val value = pop()
        val index = pop()
        val arrayRef = pop()
        instructionList(insn) += JcRawAssignInst(
            JcRawArrayAccess(arrayRef, index, arrayRef.typeName.elementType()),
            value
        )
    }

    private fun buildPop(insn: InsnNode) {
        when (val opcode = insn.opcode) {
            Opcodes.POP -> pop()
            Opcodes.POP2 -> {
                val top = pop()
                if (!top.typeName.isDWord) pop()
            }

            else -> error("Unknown pop opcode: $opcode")
        }
    }

    private fun buildDup(insn: InsnNode) {
        when (val opcode = insn.opcode) {
            Opcodes.DUP -> push(peek())
            Opcodes.DUP_X1 -> {
                val top = pop()
                val prev = pop()
                push(top)
                push(prev)
                push(top)
            }

            Opcodes.DUP_X2 -> {
                val val1 = pop()
                val val2 = pop()
                if (val2.typeName.isDWord) {
                    push(val1)
                    push(val2)
                    push(val1)
                } else {
                    val val3 = pop()
                    push(val1)
                    push(val3)
                    push(val2)
                    push(val1)
                }
            }

            Opcodes.DUP2 -> {
                val top = pop()
                if (top.typeName.isDWord) {
                    push(top)
                    push(top)
                } else {
                    val bot = pop()
                    push(bot)
                    push(top)
                    push(bot)
                    push(top)
                }
            }

            Opcodes.DUP2_X1 -> {
                val val1 = pop()
                if (val1.typeName.isDWord) {
                    val val2 = pop()
                    push(val1)
                    push(val2)
                    push(val1)
                } else {
                    val val2 = pop()
                    val val3 = pop()
                    push(val2)
                    push(val1)
                    push(val3)
                    push(val2)
                    push(val1)
                }
            }

            Opcodes.DUP2_X2 -> {
                val val1 = pop()
                if (val1.typeName.isDWord) {
                    val val2 = pop()
                    if (val2.typeName.isDWord) {
                        push(val1)
                        push(val2)
                        push(val1)
                    } else {
                        val val3 = pop()
                        push(val1)
                        push(val3)
                        push(val2)
                        push(val1)
                    }
                } else {
                    val val2 = pop()
                    val val3 = pop()
                    if (val3.typeName.isDWord) {
                        push(val2)
                        push(val1)
                        push(val3)
                        push(val2)
                        push(val1)
                    } else {
                        val val4 = pop()
                        push(val2)
                        push(val1)
                        push(val4)
                        push(val3)
                        push(val2)
                        push(val1)
                    }
                }
            }

            else -> error("Unknown dup opcode: $opcode")
        }
    }

    private fun buildSwap() {
        val top = pop()
        val bot = pop()
        push(top)
        push(bot)
    }

    private fun buildBinary(insn: InsnNode) {
        val rhv = pop()
        val lhv = pop()
        val expr = when (val opcode = insn.opcode) {
            in Opcodes.IADD..Opcodes.DADD -> JcRawAddExpr(lhv.typeName, lhv, rhv)
            in Opcodes.ISUB..Opcodes.DSUB -> JcRawSubExpr(lhv.typeName, lhv, rhv)
            in Opcodes.IMUL..Opcodes.DMUL -> JcRawMulExpr(lhv.typeName, lhv, rhv)
            in Opcodes.IDIV..Opcodes.DDIV -> JcRawDivExpr(lhv.typeName, lhv, rhv)
            in Opcodes.IREM..Opcodes.DREM -> JcRawRemExpr(lhv.typeName, lhv, rhv)
            in Opcodes.ISHL..Opcodes.LSHL -> JcRawShlExpr(lhv.typeName, lhv, rhv)
            in Opcodes.ISHR..Opcodes.LSHR -> JcRawShrExpr(lhv.typeName, lhv, rhv)
            in Opcodes.IUSHR..Opcodes.LUSHR -> JcRawUshrExpr(lhv.typeName, lhv, rhv)
            in Opcodes.IAND..Opcodes.LAND -> JcRawAndExpr(lhv.typeName, lhv, rhv)
            in Opcodes.IOR..Opcodes.LOR -> JcRawOrExpr(lhv.typeName, lhv, rhv)
            in Opcodes.IXOR..Opcodes.LXOR -> JcRawXorExpr(lhv.typeName, lhv, rhv)
            else -> error("Unknown binary opcode: $opcode")
        }
        val assignment = nextRegister(lhv.typeName)
        instructionList(insn) += JcRawAssignInst(assignment, expr)
        push(assignment)
    }

    private fun buildUnary(insn: InsnNode) {
        val operand = pop()
        val expr = when (val opcode = insn.opcode) {
            in Opcodes.INEG..Opcodes.DNEG -> JcRawNegExpr(operand.typeName, operand)
            Opcodes.ARRAYLENGTH -> JcRawLengthExpr(PredefinedPrimitives.int.typeName(), operand)
            else -> error("Unknown unary opcode $opcode")
        }
        val assignment = nextRegister(expr.typeName)
        instructionList(insn) += JcRawAssignInst(assignment, expr)
        push(assignment)
    }

    private fun buildCast(insn: InsnNode) {
        val operand = pop()
        val targetType = when (val opcode = insn.opcode) {
            Opcodes.I2L, Opcodes.F2L, Opcodes.D2L -> PredefinedPrimitives.long.typeName()
            Opcodes.I2F, Opcodes.L2F, Opcodes.D2F -> PredefinedPrimitives.float.typeName()
            Opcodes.I2D, Opcodes.L2D, Opcodes.F2D -> PredefinedPrimitives.double.typeName()
            Opcodes.L2I, Opcodes.F2I, Opcodes.D2I -> PredefinedPrimitives.int.typeName()
            Opcodes.I2B -> PredefinedPrimitives.byte.typeName()
            Opcodes.I2C -> PredefinedPrimitives.char.typeName()
            Opcodes.I2S -> PredefinedPrimitives.short.typeName()
            else -> error("Unknown cast opcode $opcode")
        }
        val assignment = nextRegister(targetType)
        instructionList(insn) += JcRawAssignInst(assignment, JcRawCastExpr(targetType, operand))
        push(assignment)
    }

    private fun buildCmp(insn: InsnNode) {
        val rhv = pop()
        val lhv = pop()
        val expr = when (val opcode = insn.opcode) {
            Opcodes.LCMP -> JcRawCmpExpr(PredefinedPrimitives.int.typeName(), lhv, rhv)
            Opcodes.FCMPL, Opcodes.DCMPL -> JcRawCmplExpr(PredefinedPrimitives.int.typeName(), lhv, rhv)
            Opcodes.FCMPG, Opcodes.DCMPG -> JcRawCmpgExpr(PredefinedPrimitives.int.typeName(), lhv, rhv)
            else -> error("Unknown cmp opcode $opcode")
        }
        val assignment = nextRegister(PredefinedPrimitives.int.typeName())
        instructionList(insn) += JcRawAssignInst(assignment, expr)
        push(assignment)
    }

    private fun buildReturn(insn: InsnNode) {
        instructionList(insn) += when (val opcode = insn.opcode) {
            Opcodes.RETURN -> JcRawReturnInst(null)
            in Opcodes.IRETURN..Opcodes.ARETURN -> JcRawReturnInst(pop())
            else -> error("Unknown return opcode: $opcode")
        }
    }

    private fun buildMonitor(insn: InsnNode) {
        val monitor = pop() as JcRawSimpleValue
        instructionList(insn) += when (val opcode = insn.opcode) {
            Opcodes.MONITORENTER -> {
                JcRawEnterMonitorInst(monitor)
            }

            Opcodes.MONITOREXIT -> JcRawExitMonitorInst(monitor)
            else -> error("Unknown monitor opcode $opcode")
        }
    }

    private fun buildThrow(insn: InsnNode) {
        val throwable = pop()
        instructionList(insn) += JcRawThrowInst(throwable)
    }

    private fun buildFieldInsnNode(insnNode: FieldInsnNode) {
        val fieldName = insnNode.name
        val fieldType = insnNode.desc.typeName()
        val declaringClass = insnNode.owner.typeName()
        when (insnNode.opcode) {
            Opcodes.GETFIELD -> {
                val assignment = nextRegister(fieldType)
                val field = JcRawFieldRef(pop(), declaringClass, fieldName, fieldType)
                instructionList(insnNode).add(JcRawAssignInst(assignment, field))
                push(assignment)
            }

            Opcodes.PUTFIELD -> {
                val value = pop()
                val instance = pop()
                val fieldRef = JcRawFieldRef(instance, declaringClass, fieldName, fieldType)
                instructionList(insnNode) += JcRawAssignInst(fieldRef, value)
            }

            Opcodes.GETSTATIC -> {
                val assignment = nextRegister(fieldType)
                val field = JcRawFieldRef(declaringClass, fieldName, fieldType)
                instructionList(insnNode).add(JcRawAssignInst(assignment, field))
                push(assignment)
            }

            Opcodes.PUTSTATIC -> {
                val value = pop()
                val fieldRef = JcRawFieldRef(declaringClass, fieldName, fieldType)
                instructionList(insnNode) += JcRawAssignInst(fieldRef, value)
            }
        }
    }

    /**
     * a helper function that helps to merge local variables from several predecessor frames into one map
     * if all the predecessor frames are known (meaning we already visited all the corresponding instructions
     * in the bytecode) --- merge process is trivial
     * if some predecessor frames are unknown, we remebmer them and add requried assignment instructions after
     * the full construction process is complete, see #buildRequiredAssignments function
     */
    private fun SortedMap<Int, TypeName>.copyLocals(predFrames: Map<AbstractInsnNode, Frame?>): Map<Int, JcRawValue> =
        when {
            // should not happen usually, but sometimes there are some "handing" blocks in the bytecode that are
            // not connected to any other part of the code
            predFrames.isEmpty() -> this.mapValues { nextRegister(it.value) }

            // simple case --- current block has only one predecessor, we can simply copy all the local variables from
            // predecessor to new frame; however we sometimes can refine the information about types of local variables
            // from the frame descriptor. In that case we create a new local variable with correct type and remember to
            // normalize them afterwards
            predFrames.size == 1 -> {
                val (node, frame) = predFrames.toList().first()
                when (frame) {
                    null -> this.mapNotNull { (variable, type) ->
                        when (type) {
                            TOP -> null
                            else -> variable to nextRegister(type).also {
                                laterAssignments.getOrPut(node, ::mutableMapOf)[variable] = it
                            }
                        }
                    }.toMap()

                    else -> frame.locals.filterKeys { it in this }.mapValues {
                        when {
                            it.value is JcRawLocal && it.value.typeName != this[it.key]!! -> JcRawLocal(
                                (it.value as JcRawLocal).name,
                                this[it.key]!!
                            ).also { newLocal ->
                                localTypeRefinement[it.value as JcRawLocal] = newLocal
                            }

                            else -> it.value
                        }
                    }
                }
            }

            // complex case --- we have a multiple predecessor frames and some of them may be unknown
            else -> mapNotNull { (variable, type) ->
                val options = predFrames.values.map { it?.get(variable) }.toSet()
                val value = when {
                    type == TOP -> null
                    options.size == 1 -> options.singleOrNull()
                    variable < argCounter -> frames.values.mapNotNull { it[variable] }.firstOrNull {
                        it is JcRawArgument || it is JcRawThis
                    }

                    else -> {
                        val assignment = nextRegister(type)
                        for ((node, frame) in predFrames) {
                            if (frame != null) {
                                val instList = instructionList(node)
                                if (node.isBranchingInst) {
                                    instList.add(0, JcRawAssignInst(assignment, frame[variable]!!))
                                } else {
                                    instList.add(JcRawAssignInst(assignment, frame[variable]!!))
                                }
                            } else {
                                laterAssignments.getOrPut(node, ::mutableMapOf)[variable] = assignment
                            }
                        }
                        assignment
                    }
                }
                value?.let { variable to it }
            }.toMap()
        }

    /**
     * a helper function that helps to merge stack variables from several predecessor frames into one map
     * if all the predecessor frames are known (meaning we already visited all the corresponding instructions
     * in the bytecode) --- merge process is trivial
     * if some predecessor frames are unknown, we remebmer them and add requried assignment instructions after
     * the full construction process is complete, see #buildRequiredAssignments function
     */
    private fun SortedMap<Int, TypeName>.copyStack(predFrames: Map<AbstractInsnNode, Frame?>): List<JcRawValue> = when {
        // should not happen usually, but sometimes there are some "handing" blocks in the bytecode that are
        // not connected to any other part of the code
        predFrames.isEmpty() -> this.values.map { nextRegister(it) }


        // simple case --- current block has only one predecessor, we can simply copy all the local variables from
        // predecessor to new frame; however we sometimes can refine the information about types of local variables
        // from the frame descriptor. In that case we create a new local variable with correct type and remember to
        // normalize them afterwards
        predFrames.size == 1 -> {
            val (node, frame) = predFrames.toList().first()
            when (frame) {
                null -> this.mapNotNull { (variable, type) ->
                    when (type) {
                        TOP -> null
                        else -> nextRegister(type).also {
                            laterStackAssignments.getOrPut(node, ::mutableMapOf)[variable] = it
                        }
                    }
                }

                else -> frame.stack.withIndex().filter { it.index in this }.map {
                    when {
                        it.value is JcRawLocal && it.value.typeName != this[it.index]!! -> JcRawLocal(
                            (it.value as JcRawLocal).name,
                            this[it.index]!!
                        ).also { newLocal ->
                            localTypeRefinement[it.value as JcRawLocal] = newLocal
                        }

                        else -> it.value
                    }
                }
            }
        }

        // complex case --- we have a multiple predecessor frames and some of them may be unknown
        else -> this.mapNotNull { (variable, type) ->
            val options = predFrames.values.map { it?.stack?.get(variable) }.toSet()
            when (options.size) {
                1 -> options.singleOrNull()
                else -> {
                    val assignment = nextRegister(type)
                    for ((node, frame) in predFrames) {
                        if (frame != null) {
                            val instList = instructionList(node)
                            if (node.isBranchingInst) {
                                instList.add(0, JcRawAssignInst(assignment, frame.stack[variable]))
                            } else {
                                instList.add(JcRawAssignInst(assignment, frame.stack[variable]))
                            }
                        } else {
                            laterStackAssignments.getOrPut(node, ::mutableMapOf)[variable] = assignment
                        }
                    }
                    assignment
                }
            }
        }
    }

    private fun buildFrameNode(insnNode: FrameNode) {
        val (currentEntry, blockPredecessors) = run {
            var current: AbstractInsnNode = insnNode
            while (current !is LabelNode) current = current.previous
            current to predecessors[current]!!
        }
        val predecessorFrames = blockPredecessors.associateWith { frames[it] }
        assert(predecessorFrames.isNotEmpty())
        lastFrameState = when (insnNode.type) {
            Opcodes.F_NEW -> FrameState.parseNew(insnNode)
            Opcodes.F_FULL -> FrameState.parseNew(insnNode)
            Opcodes.F_APPEND -> lastFrameState.appendFrame(insnNode)
            Opcodes.F_CHOP -> lastFrameState.dropFrame(insnNode)
            Opcodes.F_SAME -> lastFrameState.copy0()
            Opcodes.F_SAME1 -> lastFrameState.copy1(insnNode)
            else -> error("Unknown frame node type: ${insnNode.type}")
        }

        when (val catch = methodNode.tryCatchBlocks.firstOrNull { it.handler == currentEntry }) {
            null -> {
                currentFrame = Frame(
                    lastFrameState.locals.copyLocals(predecessorFrames).toPersistentMap(),
                    lastFrameState.stack.copyStack(predecessorFrames).toPersistentList()
                )
            }

            else -> {
                currentFrame = Frame(
                    lastFrameState.locals.copyLocals(predecessorFrames).toPersistentMap(),
                    persistentListOf()
                )
                val throwable = nextRegister(catch.typeOrDefault.typeName())
                instructionList(insnNode) += JcRawCatchInst(
                    throwable,
                    labelRef(catch.handler),
                    labelRef(catch.start),
                    labelRef(catch.end)
                )
                push(throwable)
            }
        }
    }

    private fun buildIincInsnNode(insnNode: IincInsnNode) {
        val local = local(insnNode.`var`)
        val add = JcRawAddExpr(local.typeName, local, JcRawInt(insnNode.incr))
        instructionList(insnNode) += JcRawAssignInst(local, add)
        local(insnNode.`var`, local)
    }

    private fun buildIntInsnNode(insnNode: IntInsnNode) {
        val operand = insnNode.operand
        when (val opcode = insnNode.opcode) {
            Opcodes.BIPUSH -> push(JcRawInt(operand))
            Opcodes.SIPUSH -> push(JcRawInt(operand))
            Opcodes.NEWARRAY -> {
                val expr = JcRawNewArrayExpr(operand.toPrimitiveType().asArray(), pop())
                val assignment = nextRegister(expr.typeName)
                instructionList(insnNode) += JcRawAssignInst(assignment, expr)
                push(assignment)
            }

            else -> error("Unknown int insn opcode: $opcode")
        }
    }

    private val Handle.bsmHandleArg
        get() = BsmHandle(
            tag,
            owner.typeName(),
            name,
            Type.getArgumentTypes(desc).map { it.descriptor.typeName() },
            Type.getReturnType(desc).descriptor.typeName(),
            isInterface
        )

    private fun bsmNumberArg(number: Number) = when (number) {
        is Int -> BsmIntArg(number)
        is Float -> BsmFloatArg(number)
        is Long -> BsmLongArg(number)
        is Double -> BsmDoubleArg(number)
        else -> error("Unknown number: $number")
    }

    private fun buildInvokeDynamicInsn(insnNode: InvokeDynamicInsnNode) {
        val desc = insnNode.desc
        val bsmMethod = insnNode.bsm.bsmHandleArg
        val bsmArgs = insnNode.bsmArgs.map {
            when (it) {
                is Number -> bsmNumberArg(it)
                is String -> BsmStringArg(it)
                is Type -> it.asTypeName
                is Handle -> it.bsmHandleArg
                else -> error("Unknown arg of bsm: $it")
            }
        }.reversed()
        val args = Type.getArgumentTypes(desc).map { pop() }.reversed()
        val expr = JcRawDynamicCallExpr(
            bsmMethod,
            bsmArgs,
            insnNode.name,
            Type.getArgumentTypes(desc).map { it.descriptor.typeName() },
            Type.getReturnType(desc).descriptor.typeName(),
            args,
        )
        if (Type.getReturnType(desc) == Type.VOID_TYPE) {
            instructionList(insnNode) += JcRawCallInst(expr)
        } else {
            val result = nextRegister(Type.getReturnType(desc).descriptor.typeName())
            instructionList(insnNode) += JcRawAssignInst(result, expr)
            push(result)
        }
    }

    private fun buildJumpInsnNode(insnNode: JumpInsnNode) {
        val target = labelRef(insnNode.label)
        when (val opcode = insnNode.opcode) {
            Opcodes.GOTO -> instructionList(insnNode) += JcRawGotoInst(target)
            else -> {
                val falseTarget = (insnNode.next as? LabelNode)?.let { label(it) } ?: nextLabel()
                val rhv = pop()
                val boolTypeName = PredefinedPrimitives.boolean.typeName()
                val expr = when (opcode) {
                    Opcodes.IFNULL -> JcRawEqExpr(boolTypeName, rhv, JcRawNull())
                    Opcodes.IFNONNULL -> JcRawNeqExpr(boolTypeName, rhv, JcRawNull())
                    Opcodes.IFEQ -> JcRawEqExpr(boolTypeName, JcRawZero(rhv.typeName), rhv)
                    Opcodes.IFNE -> JcRawNeqExpr(boolTypeName, JcRawZero(rhv.typeName), rhv)
                    Opcodes.IFLT -> JcRawLtExpr(boolTypeName, JcRawZero(rhv.typeName), rhv)
                    Opcodes.IFGE -> JcRawGeExpr(boolTypeName, JcRawZero(rhv.typeName), rhv)
                    Opcodes.IFGT -> JcRawGtExpr(boolTypeName, JcRawZero(rhv.typeName), rhv)
                    Opcodes.IFLE -> JcRawLeExpr(boolTypeName, JcRawZero(rhv.typeName), rhv)
                    Opcodes.IF_ICMPEQ -> JcRawEqExpr(boolTypeName, pop(), rhv)
                    Opcodes.IF_ICMPNE -> JcRawNeqExpr(boolTypeName, pop(), rhv)
                    Opcodes.IF_ICMPLT -> JcRawLtExpr(boolTypeName, pop(), rhv)
                    Opcodes.IF_ICMPGE -> JcRawGeExpr(boolTypeName, pop(), rhv)
                    Opcodes.IF_ICMPGT -> JcRawGtExpr(boolTypeName, pop(), rhv)
                    Opcodes.IF_ICMPLE -> JcRawLeExpr(boolTypeName, pop(), rhv)
                    Opcodes.IF_ACMPEQ -> JcRawEqExpr(boolTypeName, pop(), rhv)
                    Opcodes.IF_ACMPNE -> JcRawNeqExpr(boolTypeName, pop(), rhv)
                    else -> error("Unknown jump opcode $opcode")
                }
                instructionList(insnNode) += JcRawIfInst(expr, target, falseTarget.ref)
                if (insnNode.next !is LabelNode) {
                    instructionList(insnNode) += falseTarget
                }
            }
        }
    }

    private fun mergeFrames(frames: Map<AbstractInsnNode, Frame>): Frame {
        val frameSet = frames.values
        if (frames.isEmpty()) return currentFrame
        if (frames.size == 1) return frameSet.first()

        val allLocals = frameSet.flatMap { it.locals.keys }
        val localTypes = allLocals
            .filter { local -> frameSet.all { local in it.locals } }
            .associateWith {
                val types = frameSet.map { frame -> frame[it]!!.typeName }
                types.firstOrNull { it != NULL } ?: NULL
            }
            .toSortedMap()
        val newLocals = localTypes.copyLocals(frames).toPersistentMap()

        val stackIndices = frameSet.flatMap { it.stack.indices }.toSortedSet()
        val stackRanges = stackIndices
            .filter { stack -> frameSet.all { stack in it.stack.indices } }
            .associateWith {
                val types = frameSet.map { frame -> frame.stack[it].typeName }
                types.firstOrNull { it != NULL } ?: NULL
            }
            .toSortedMap()
        val newStack = stackRanges.copyStack(frames).toPersistentList()

        return Frame(newLocals, newStack)
    }

    private fun buildLabelNode(insnNode: LabelNode) {
        val labelInst = label(insnNode)
        instructionList(insnNode) += labelInst
        val predecessors = predecessors.getOrDefault(insnNode, emptySet())
        val predecessorFrames = predecessors.mapNotNull { frames[it] }
        if (predecessors.size == predecessorFrames.size) {
            currentFrame = mergeFrames(predecessors.zip(predecessorFrames).toMap())
        }
        when (val tryCatch = methodNode.tryCatchBlocks.firstOrNull { it.handler == insnNode }) {
            null -> {}
            else -> {
                push(nextRegister(tryCatch.typeOrDefault.typeName()))
            }
        }
    }

    private fun buildLdcInsnNode(insnNode: LdcInsnNode) {
        when (val cst = insnNode.cst) {
            is Int -> push(JcRawInt(cst))
            is Float -> push(JcRawFloat(cst))
            is Double -> push(JcRawDouble(cst))
            is Long -> push(JcRawLong(cst))
            is String -> push(JcRawStringConstant(cst, STRING_CLASS.typeName()))
            is Type -> {
                val assignment = nextRegister(CLASS_CLASS.typeName())
                instructionList(insnNode) += JcRawAssignInst(
                    assignment,
                    JcRawClassConstant(cst.descriptor.typeName(), CLASS_CLASS.typeName())
                )
                push(assignment)
            }

            is Handle -> {
                val assignment = nextRegister(CLASS_CLASS.typeName())
                instructionList(insnNode) += JcRawAssignInst(
                    assignment,
                    JcRawMethodConstant(
                        cst.owner.typeName(),
                        cst.name,
                        Type.getArgumentTypes(cst.desc).map { it.descriptor.typeName() },
                        Type.getReturnType(cst.desc).descriptor.typeName(),
                        METHOD_HANDLE_CLASS.typeName()
                    )
                )
                push(assignment)
            }

            else -> error("Unknown LDC constant: $cst")
        }
    }

    private fun buildLookupSwitchInsnNode(insnNode: LookupSwitchInsnNode) {
        val key = pop()
        val default = labelRef(insnNode.dflt)
        val branches = insnNode.keys
            .zip(insnNode.labels)
            .associate { (JcRawInt(it.first) as JcRawValue) to labelRef(it.second) }
        instructionList(insnNode) += JcRawSwitchInst(key, branches, default)
    }

    private fun buildMethodInsnNode(insnNode: MethodInsnNode) {
        val owner = when {
            insnNode.owner.typeName().isArray -> OBJECT_CLASS.typeName()
            else -> insnNode.owner.typeName()
        }
        val methodName = insnNode.name
        val argTypes = Type.getArgumentTypes(insnNode.desc).map { it.descriptor.typeName() }
        val returnType = Type.getReturnType(insnNode.desc).descriptor.typeName()

        val args = Type.getArgumentTypes(insnNode.desc).map { pop() }.reversed()

        val expr = when (val opcode = insnNode.opcode) {
            Opcodes.INVOKESTATIC -> JcRawStaticCallExpr(
                owner,
                methodName,
                argTypes,
                returnType,
                args
            )

            else -> {
                val instance = pop()
                when (opcode) {
                    Opcodes.INVOKEVIRTUAL -> JcRawVirtualCallExpr(
                        owner,
                        methodName,
                        argTypes,
                        returnType,
                        instance,
                        args
                    )

                    Opcodes.INVOKESPECIAL -> JcRawSpecialCallExpr(
                        owner,
                        methodName,
                        argTypes,
                        returnType,
                        instance,
                        args
                    )

                    Opcodes.INVOKEINTERFACE -> JcRawInterfaceCallExpr(
                        owner,
                        methodName,
                        argTypes,
                        returnType,
                        instance,
                        args
                    )

                    else -> error("Unknown method insn opcode: ${insnNode.opcode}")
                }
            }
        }
        if (Type.getReturnType(insnNode.desc) == Type.VOID_TYPE) {
            instructionList(insnNode) += JcRawCallInst(expr)
        } else {
            val result = nextRegister(Type.getReturnType(insnNode.desc).descriptor.typeName())
            instructionList(insnNode) += JcRawAssignInst(result, expr)
            push(result)
        }
    }

    private fun buildMultiANewArrayInsnNode(insnNode: MultiANewArrayInsnNode) {
        val dimensions = mutableListOf<JcRawValue>()
        repeat(insnNode.dims) {
            dimensions += pop()
        }
        val expr = JcRawNewArrayExpr(insnNode.desc.typeName(), dimensions)
        val assignment = nextRegister(expr.typeName)
        instructionList(insnNode) += JcRawAssignInst(assignment, expr)
        push(assignment)
    }

    private fun buildTableSwitchInsnNode(insnNode: TableSwitchInsnNode) {
        val index = pop()
        val default = labelRef(insnNode.dflt)
        val branches = (insnNode.min..insnNode.max)
            .zip(insnNode.labels)
            .associate { (JcRawInt(it.first) as JcRawValue) to labelRef(it.second) }
        instructionList(insnNode) += JcRawSwitchInst(index, branches, default)
    }

    private fun buildTypeInsnNode(insnNode: TypeInsnNode) {
        val type = insnNode.desc.typeName()
        when (insnNode.opcode) {
            Opcodes.NEW -> {
                val assignment = nextRegister(type)
                instructionList(insnNode) += JcRawAssignInst(assignment, JcRawNewExpr(type))
                push(assignment)
            }

            Opcodes.ANEWARRAY -> {
                val length = pop()
                val assignment = nextRegister(type.asArray())
                instructionList(insnNode) += JcRawAssignInst(assignment, JcRawNewArrayExpr(type.asArray(), length))
                push(assignment)
            }

            Opcodes.CHECKCAST -> {
                val assignment = nextRegister(type)
                instructionList(insnNode) += JcRawAssignInst(assignment, JcRawCastExpr(type, pop()))
                push(assignment)
            }

            Opcodes.INSTANCEOF -> {
                val assignment = nextRegister(PredefinedPrimitives.boolean.typeName())
                instructionList(insnNode) += JcRawAssignInst(
                    assignment,
                    JcRawInstanceOfExpr(PredefinedPrimitives.boolean.typeName(), pop(), type)
                )
                push(assignment)
            }

            else -> error("Unknown opcode ${insnNode.opcode} in TypeInsnNode")
        }
    }

    private fun buildVarInsnNode(insnNode: VarInsnNode) {
        when (insnNode.opcode) {
            in Opcodes.ISTORE..Opcodes.ASTORE -> local(insnNode.`var`, pop())?.let { instructionList(insnNode).add(it) }

            in Opcodes.ILOAD..Opcodes.ALOAD -> push(local(insnNode.`var`))
            else -> error("Unknown opcode ${insnNode.opcode} in VarInsnNode")
        }
    }
}
