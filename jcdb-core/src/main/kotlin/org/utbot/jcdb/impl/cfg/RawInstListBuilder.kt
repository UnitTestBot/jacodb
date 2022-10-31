package org.utbot.jcdb.impl.cfg

import kotlinx.collections.immutable.*
import kotlinx.coroutines.runBlocking
import org.objectweb.asm.Handle
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*
import org.utbot.jcdb.api.*
import org.utbot.jcdb.impl.types.TypeNameImpl
import java.util.*

private const val STRING_CLASS = "java.lang.String"
private const val CLASS_CLASS = "java.lang.Class"
private const val METHOD_HANDLE_CLASS = "java.lang.invoke.MethodHandle"

private val TypeName.isDWord
    get() = when (typeName) {
        PredefinedPrimitives.long -> true
        PredefinedPrimitives.double -> true
        else -> false
    }

private fun String.typeName(): TypeName = TypeNameImpl(jcdbName())
private fun TypeName.asArray(dimensions: Int = 1) = "$typeName${"[]".repeat(dimensions)}".typeName()
private fun TypeName.elementType() = when {
    typeName.endsWith("[]") -> typeName.removeSuffix("[]").typeName()
    else -> error("Attempting to get element type of non-array type $this")
}

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
private val NULL = "null".typeName()
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


private fun JcRawNull() = JcRawNullConstant(NULL)
private fun JcRawBool(value: Boolean) = JcRawBool(value, PredefinedPrimitives.boolean.typeName())
private fun JcRawByte(value: Byte) = JcRawByte(value, PredefinedPrimitives.byte.typeName())
private fun JcRawShort(value: Short) = JcRawShort(value, PredefinedPrimitives.short.typeName())
private fun JcRawChar(value: Char) = JcRawChar(value, PredefinedPrimitives.char.typeName())
private fun JcRawInt(value: Int) = JcRawInt(value, PredefinedPrimitives.int.typeName())
private fun JcRawLong(value: Long) = JcRawLong(value, PredefinedPrimitives.long.typeName())
private fun JcRawFloat(value: Float) = JcRawFloat(value, PredefinedPrimitives.float.typeName())
private fun JcRawDouble(value: Double) = JcRawDouble(value, PredefinedPrimitives.double.typeName())

private fun JcRawZero(typeName: TypeName) = when (typeName.typeName) {
    PredefinedPrimitives.boolean -> JcRawBool(false)
    PredefinedPrimitives.byte -> JcRawByte(0)
    PredefinedPrimitives.char -> JcRawChar(0.toChar())
    PredefinedPrimitives.short -> JcRawShort(0)
    PredefinedPrimitives.int -> JcRawInt(0)
    PredefinedPrimitives.long -> JcRawLong(0)
    PredefinedPrimitives.float -> JcRawFloat(0.0f)
    PredefinedPrimitives.double -> JcRawDouble(0.0)
    else -> error("Unknown primitive type: $typeName")
}

private fun JcRawNumber(number: Number) = when (number) {
    is Int -> JcRawInt(number)
    is Float -> JcRawFloat(number)
    is Long -> JcRawLong(number)
    is Double -> JcRawDouble(number)
    else -> error("Unknown number: $number")
}

private fun JcRawString(value: String) = JcRawStringConstant(value, STRING_CLASS.typeName())


private val Type.asTypeName: Any
    get() = when (this.sort) {
        Type.VOID -> PredefinedPrimitives.void.typeName()
        Type.BOOLEAN -> PredefinedPrimitives.boolean.typeName()
        Type.CHAR -> PredefinedPrimitives.char.typeName()
        Type.BYTE -> PredefinedPrimitives.byte.typeName()
        Type.SHORT -> PredefinedPrimitives.short.typeName()
        Type.INT -> PredefinedPrimitives.int.typeName()
        Type.FLOAT -> PredefinedPrimitives.float.typeName()
        Type.LONG -> PredefinedPrimitives.long.typeName()
        Type.DOUBLE -> PredefinedPrimitives.double.typeName()
        Type.ARRAY -> (elementType.asTypeName as TypeName).asArray()
        Type.OBJECT -> className.typeName()
        Type.METHOD -> Pair(this.argumentTypes.map { it.asTypeName }, this.returnType.asTypeName)
        else -> error("Unknown type: $this")
    }

class RawInstListBuilder(val method: JcMethod) {
    private lateinit var methodNode: MethodNode
    private val frames = mutableMapOf<AbstractInsnNode, Frame>()
    private val labels = mutableMapOf<LabelNode, JcRawLabelInst>()
    private lateinit var lastFrameState: FrameState
    private lateinit var currentFrame: Frame

    private val tryCatchBlocks = mutableListOf<JcRawTryCatchBlock>()
    private val predecessors = mutableMapOf<AbstractInsnNode, MutableList<AbstractInsnNode>>()
    private val instructions = mutableMapOf<AbstractInsnNode, MutableList<JcRawInst>>()
    private val laterAssignments = mutableMapOf<AbstractInsnNode, MutableMap<Int, JcRawValue>>()
    private val laterStackAssignments = mutableMapOf<AbstractInsnNode, MutableMap<Int, JcRawValue>>()
    private var labelCounter = 0
    private var localCounter = 0

    fun build(): JcRawInstList {
        methodNode = runBlocking { method.body() }
        buildGraph()

        buildInstructions()
        buildRequiredAssignments()
        buildTryCatchNodes()

        return JcRawInstList(methodNode.instructions.flatMap { instructionList(it) }, tryCatchBlocks)
    }

    private fun buildInstructions() {
        currentFrame = createInitialFrame(method)
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

    private fun buildRequiredAssignments() {
        for ((insn, assignments) in laterAssignments) {
            val insnList = instructionList(insn)
            val frame = frames[insn]!!
            for ((variable, value) in assignments) {
                if (value != frame[variable]) {
                    insnList += JcRawAssignInst(value, frame[variable]!!)
                }
            }
        }
        for ((insn, assignments) in laterStackAssignments) {
            val insnList = instructionList(insn)
            val frame = frames[insn]!!
            for ((variable, value) in assignments) {
                if (value != frame.stack[variable]) {
                    insnList += JcRawAssignInst(value, frame.stack[variable])
                }
            }
        }
    }

    private fun buildTryCatchNodes() {
        for (tryCatchBlockNode in methodNode.tryCatchBlocks) {
            tryCatchBlocks += JcRawTryCatchBlock(
                tryCatchBlockNode.type.typeName(),
                label(tryCatchBlockNode.handler),
                label(tryCatchBlockNode.start),
                label(tryCatchBlockNode.end)
            )
        }
    }

    // this class is required to handle FrameNode instructions of ASM
    private data class FrameState(
        val locals: Map<Int, TypeName>,
        val stack: Map<Int, TypeName>
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
            return copy(locals = newLocals, stack = emptyMap())
        }

        fun dropFrame(inst: FrameNode): FrameState {
            val newLocals = this.locals.toList().dropLast(inst.local.size).toMap()
            return copy(locals = newLocals, stack = emptyMap())
        }

        fun copy0(): FrameState = this.copy(stack = emptyMap())

        fun copy1(insn: FrameNode): FrameState {
            val newStack = insn.stack.parseStack()
            return this.copy(stack = newStack)
        }
    }

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

    private fun local(variable: Int, expr: JcRawValue): JcRawAssignInst {
        val oldVar = currentFrame.locals[variable]
        return if (oldVar != null) {
            JcRawAssignInst(oldVar, expr)
        } else {
            val newLocal = nextRegister(expr.typeName)
            val result = JcRawAssignInst(newLocal, expr)
            currentFrame = currentFrame.put(variable, newLocal)
            result
        }
    }

    private fun label(insnNode: LabelNode): JcRawLabelInst = labels.getOrPut(insnNode, ::nextLabel)

    private fun instructionList(insn: AbstractInsnNode) = instructions.getOrPut(insn, ::mutableListOf)


    private fun nextRegister(typeName: TypeName): JcRawValue = JcRawRegister(localCounter++, typeName)
    private fun nextLabel(): JcRawLabelInst = JcRawLabelInst("#${labelCounter++}")

    private fun buildGraph() {
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
            } else if (insn is InsnNode && (insn.opcode == Opcodes.ATHROW || insn.opcode in Opcodes.IRETURN..Opcodes.RETURN)) {
                continue
            } else if (insn.next != null) {
                predecessors.getOrPut(insn.next, ::mutableListOf).add(insn)
            }
        }
        for (tryCatchBlock in methodNode.tryCatchBlocks) {
            var current: AbstractInsnNode = tryCatchBlock.start
            while (current != tryCatchBlock.end) {
                predecessors.getOrPut(tryCatchBlock.handler, ::mutableListOf).add(current)
                current = current.next
            }
        }
    }

    private fun createInitialFrame(method: JcMethod): Frame {
        val locals = hashMapOf<Int, JcRawValue>()
        var localIndex = 0
        if (!method.isStatic) {
            val thisRef = JcRawThis(method.enclosingClass.name.typeName())
            locals[localIndex++] = thisRef
        }
        for (parameter in method.parameters) {
            val argument = JcRawArgument(parameter.index, null, parameter.type)
            locals[localIndex] = argument
            if (argument.typeName.isDWord) localIndex += 2
            else localIndex++
        }

        return Frame(locals.toPersistentMap(), persistentListOf())
    }

    private fun buildInsnNode(insn: InsnNode) {
        when (insn.opcode) {
            Opcodes.NOP -> Unit
            in Opcodes.ACONST_NULL..Opcodes.DCONST_1 -> buildConstant(insn)
            in Opcodes.IALOAD..Opcodes.SALOAD -> buildArrayRead()
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

    private fun buildArrayRead() {
        val index = pop()
        val arrayRef = pop()
        push(JcRawArrayAccess(arrayRef, index, arrayRef.typeName.elementType()))
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
            in Opcodes.IADD..Opcodes.DADD -> JcRawAddExpr(lhv, rhv)
            in Opcodes.ISUB..Opcodes.DSUB -> JcRawSubExpr(lhv, rhv)
            in Opcodes.IMUL..Opcodes.DMUL -> JcRawMulExpr(lhv, rhv)
            in Opcodes.IDIV..Opcodes.DDIV -> JcRawDivExpr(lhv, rhv)
            in Opcodes.IREM..Opcodes.DREM -> JcRawRemExpr(lhv, rhv)
            in Opcodes.ISHL..Opcodes.LSHL -> JcRawShlExpr(lhv, rhv)
            in Opcodes.ISHR..Opcodes.LSHR -> JcRawShrExpr(lhv, rhv)
            in Opcodes.IUSHR..Opcodes.LUSHR -> JcRawUshrExpr(lhv, rhv)
            in Opcodes.IAND..Opcodes.LAND -> JcRawAndExpr(lhv, rhv)
            in Opcodes.IOR..Opcodes.LOR -> JcRawOrExpr(lhv, rhv)
            in Opcodes.IXOR..Opcodes.LXOR -> JcRawXorExpr(lhv, rhv)
            else -> error("Unknown binary opcode: $opcode")
        }
        val assignment = nextRegister(lhv.typeName)
        instructionList(insn) += JcRawAssignInst(assignment, expr)
        push(assignment)
    }

    private fun buildUnary(insn: InsnNode) {
        val operand = pop()
        val (expr, typeName) = when (val opcode = insn.opcode) {
            in Opcodes.INEG..Opcodes.DNEG -> JcRawNegExpr(operand) to operand.typeName
            Opcodes.ARRAYLENGTH -> JcRawLengthExpr(operand) to PredefinedPrimitives.int.typeName()
            else -> error("Unknown unary opcode $opcode")
        }
        val assignment = nextRegister(typeName)
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
        instructionList(insn) += JcRawAssignInst(assignment, JcRawCastExpr(operand, targetType))
        push(assignment)
    }

    private fun buildCmp(insn: InsnNode) {
        val rhv = pop()
        val lhv = pop()
        val expr = when (val opcode = insn.opcode) {
            Opcodes.LCMP -> JcRawCmpExpr(lhv, rhv)
            Opcodes.FCMPL, Opcodes.DCMPL -> JcRawCmplExpr(lhv, rhv)
            Opcodes.FCMPG, Opcodes.DCMPG -> JcRawCmpgExpr(lhv, rhv)
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
        val monitor = pop()
        instructionList(insn) += when (val opcode = insn.opcode) {
            Opcodes.MONITORENTER -> JcRawEnterMonitorInst(monitor)
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
            Opcodes.GETFIELD -> push(JcRawFieldRef(pop(), declaringClass, fieldName, fieldType))
            Opcodes.PUTFIELD -> {
                val value = pop()
                val instance = pop()
                val fieldRef = JcRawFieldRef(instance, declaringClass, fieldName, fieldType)
                instructionList(insnNode) += JcRawAssignInst(fieldRef, value)
            }

            Opcodes.GETSTATIC -> push(JcRawFieldRef(declaringClass, fieldName, fieldType))
            Opcodes.PUTSTATIC -> {
                val value = pop()
                val fieldRef = JcRawFieldRef(declaringClass, fieldName, fieldType)
                instructionList(insnNode) += JcRawAssignInst(fieldRef, value)
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

        fun FrameState.copyLocals(predFrames: Map<AbstractInsnNode, Frame?>): Map<Int, JcRawValue> = when {
            predFrames.isEmpty() -> locals.mapValues { nextRegister(it.value) }

            predFrames.size == 1 -> {
                val frame = predFrames.values.first()
                assert(frame != null)
                frame!!.locals
            }

            else -> locals.mapNotNull { (variable, type) ->
                val options = predFrames.values.map { it?.get(variable) }.toSet()
                val value = when {
                    type == TOP -> null
                    options.size == 1 -> options.singleOrNull()
                    else -> {
                        val assignment = nextRegister(type)
                        for ((node, frame) in predFrames) {
                            if (frame != null) {
                                instructionList(node).add(JcRawAssignInst(assignment, frame[variable]!!))
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

        fun FrameState.copyStack(predFrames: Map<AbstractInsnNode, Frame?>): List<JcRawValue> = when {
            predFrames.isEmpty() -> stack.values.map { nextRegister(it) }

            predFrames.size == 1 -> {
                val frame = predFrames.values.first()
                assert(frame != null)
                frame!!.stack
            }

            else -> stack.mapNotNull { (variable, type) ->
                val options = predFrames.values.map { it?.stack?.get(variable) }.toSet()
                when (options.size) {
                    1 -> options.singleOrNull()
                    else -> {
                        val assignment = nextRegister(type)
                        for ((node, frame) in predFrames) {
                            if (frame != null) {
                                instructionList(node).add(JcRawAssignInst(assignment, frame.stack[variable]))
                            } else {
                                laterStackAssignments.getOrPut(node, ::mutableMapOf)[variable] = assignment
                            }
                        }
                        assignment
                    }
                }
            }
        }

        when (val catch = methodNode.tryCatchBlocks.firstOrNull { it.handler == currentEntry }) {
            null -> {
                currentFrame = Frame(
                    lastFrameState.copyLocals(predecessorFrames).toPersistentMap(),
                    lastFrameState.copyStack(predecessorFrames).toPersistentList()
                )
            }

            else -> {
                currentFrame = Frame(
                    lastFrameState.copyLocals(predecessorFrames).toPersistentMap(),
                    persistentListOf()
                )
                val throwable = nextRegister(catch.type.typeName())
                instructionList(insnNode) += JcRawCatchInst(throwable)
                push(throwable)
            }
        }
    }

    private fun buildIincInsnNode(insnNode: IincInsnNode) {
        val local = local(insnNode.`var`)
        val add = JcRawAddExpr(local, JcRawInt(insnNode.incr))
        instructionList(insnNode) += JcRawAssignInst(local, add)
        local(insnNode.`var`, local)
    }

    private fun buildIntInsnNode(insnNode: IntInsnNode) {
        val operand = insnNode.operand
        when (val opcode = insnNode.opcode) {
            Opcodes.BIPUSH -> push(JcRawInt(operand))
            Opcodes.SIPUSH -> push(JcRawInt(operand))
            Opcodes.NEWARRAY -> {
                val expr = JcRawNewArrayExpr(pop(), operand.toPrimitiveType().asArray())
                val assignment = nextRegister(expr.targetType)
                instructionList(insnNode) += JcRawAssignInst(assignment, expr)
                push(assignment)
            }

            else -> error("Unknown int insn opcode: $opcode")
        }
    }

    private val Handle.asMethodHandle
        get() = MethodHandle(
            tag, owner.typeName(), name, desc, isInterface
        )

    private fun buildInvokeDynamicInsn(insnNode: InvokeDynamicInsnNode) {
        // todo: better invokedynamic handling
        val desc = insnNode.desc
        val bsmMethod = insnNode.bsm.asMethodHandle
        val bsmArgs = insnNode.bsmArgs.map {
            when (it) {
                is Number -> JcRawNumber(it)
                is String -> JcRawString(it)
                is Type -> it.asTypeName
                is Handle -> it.asMethodHandle
                else -> error("Unknown arg of bsm: $it")
            }
        }.reversed()
        val args = Type.getArgumentTypes(desc).map { pop() }
        val expr = JcRawDynamicCallExpr(
            "".typeName(),
            "dynamic call",
            desc,
            args,
            bsmMethod,
            bsmArgs
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
        val target = label(insnNode.label)
        when (val opcode = insnNode.opcode) {
            Opcodes.GOTO -> instructionList(insnNode) += JcRawGotoInst(target)
            else -> {
                val falseTarget = (insnNode.next as? LabelNode)?.let { label(it) } ?: nextLabel()
                val rhv = pop()
                val expr = when (opcode) {
                    Opcodes.IFNULL -> JcRawEqExpr(rhv, JcRawNull())
                    Opcodes.IFNONNULL -> JcRawNeqExpr(rhv, JcRawNull())
                    Opcodes.IFEQ -> JcRawEqExpr(JcRawZero(rhv.typeName), rhv)
                    Opcodes.IFNE -> JcRawNeqExpr(JcRawZero(rhv.typeName), rhv)
                    Opcodes.IFLT -> JcRawLtExpr(JcRawZero(rhv.typeName), rhv)
                    Opcodes.IFGE -> JcRawGeExpr(JcRawZero(rhv.typeName), rhv)
                    Opcodes.IFGT -> JcRawGtExpr(JcRawZero(rhv.typeName), rhv)
                    Opcodes.IFLE -> JcRawLeExpr(JcRawZero(rhv.typeName), rhv)
                    Opcodes.IF_ICMPEQ -> JcRawEqExpr(pop(), rhv)
                    Opcodes.IF_ICMPNE -> JcRawNeqExpr(pop(), rhv)
                    Opcodes.IF_ICMPLT -> JcRawLtExpr(pop(), rhv)
                    Opcodes.IF_ICMPGE -> JcRawGeExpr(pop(), rhv)
                    Opcodes.IF_ICMPGT -> JcRawGtExpr(pop(), rhv)
                    Opcodes.IF_ICMPLE -> JcRawLeExpr(pop(), rhv)
                    Opcodes.IF_ACMPEQ -> JcRawEqExpr(pop(), rhv)
                    Opcodes.IF_ACMPNE -> JcRawNeqExpr(pop(), rhv)
                    else -> error("Unknown jump opcode $opcode")
                }
                val cond = nextRegister(PredefinedPrimitives.boolean.typeName())
                instructionList(insnNode) += JcRawAssignInst(cond, expr)
                instructionList(insnNode) += JcRawIfInst(cond, target, falseTarget)
                if (insnNode.next !is LabelNode) {
                    instructionList(insnNode) += falseTarget
                }
            }
        }
    }

    private fun buildLabelNode(insnNode: LabelNode) {
        val labelInst = label(insnNode)
        instructionList(insnNode) += labelInst
    }

    private fun buildLdcInsnNode(insnNode: LdcInsnNode) {
        when (val cst = insnNode.cst) {
            is Int -> push(JcRawInt(cst))
            is Float -> push(JcRawFloat(cst))
            is Double -> push(JcRawDouble(cst))
            is Long -> push(JcRawLong(cst))
            is String -> push(JcRawStringConstant(cst, STRING_CLASS.typeName()))
            is Type -> push(JcRawClassConstant(cst.descriptor.typeName(), CLASS_CLASS.typeName()))
            is Handle -> push(
                JcRawMethodConstant(
                    cst.owner.typeName(),
                    cst.name,
                    Type.getArgumentTypes(cst.desc).map { it.descriptor.typeName() },
                    Type.getReturnType(cst.desc).descriptor.typeName(),
                    METHOD_HANDLE_CLASS.typeName()
                )
            )

            else -> error("Unknown LDC constant: $cst")
        }
    }

    private fun buildLookupSwitchInsnNode(insnNode: LookupSwitchInsnNode) {
        val key = pop()
        val default = label(insnNode.dflt)
        val branches = insnNode.keys
            .zip(insnNode.labels)
            .associate { (JcRawInt(it.first) as JcRawValue) to label(it.second) }
        instructionList(insnNode) += JcRawSwitchInst(key, branches, default)
    }

    private fun buildMethodInsnNode(insnNode: MethodInsnNode) {
        val owner = insnNode.owner.typeName()
        val methodName = insnNode.name
        val methodDesc = insnNode.desc

        val args = Type.getArgumentTypes(methodDesc).map { pop() }.reversed()

        val expr = when (val opcode = insnNode.opcode) {
            Opcodes.INVOKESTATIC -> JcRawStaticCallExpr(owner, methodName, methodDesc, args)
            else -> {
                val instance = pop()
                when (opcode) {
                    Opcodes.INVOKEVIRTUAL -> JcRawVirtualCallExpr(owner, methodName, methodDesc, instance, args)
                    Opcodes.INVOKESPECIAL -> JcRawSpecialCallExpr(owner, methodName, methodDesc, instance, args)
                    Opcodes.INVOKEINTERFACE -> JcRawInterfaceCallExpr(owner, methodName, methodDesc, instance, args)
                    else -> error("Unknown method insn opcode: ${insnNode.opcode}")
                }
            }
        }
        if (Type.getReturnType(methodDesc) == Type.VOID_TYPE) {
            instructionList(insnNode) += JcRawCallInst(expr)
        } else {
            val result = nextRegister(Type.getReturnType(methodDesc).descriptor.typeName())
            instructionList(insnNode) += JcRawAssignInst(result, expr)
            push(result)
        }
    }

    private fun buildMultiANewArrayInsnNode(insnNode: MultiANewArrayInsnNode) {
        val dimensions = mutableListOf<JcRawValue>()
        repeat(insnNode.dims) {
            dimensions += pop()
        }
        val expr = JcRawNewArrayExpr(dimensions, insnNode.desc.typeName().asArray(dimensions.size))
        val assignment = nextRegister(expr.targetType)
        instructionList(insnNode) += JcRawAssignInst(assignment, expr)
        push(assignment)
    }

    private fun buildTableSwitchInsnNode(insnNode: TableSwitchInsnNode) {
        val index = pop()
        val default = label(insnNode.dflt)
        val branches = (insnNode.min..insnNode.max)
            .zip(insnNode.labels)
            .associate { (JcRawInt(it.first) as JcRawValue) to label(it.second) }
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
                instructionList(insnNode) += JcRawAssignInst(assignment, JcRawNewArrayExpr(length, type.asArray()))
                push(assignment)
            }

            Opcodes.CHECKCAST -> {
                val assignment = nextRegister(type)
                instructionList(insnNode) += JcRawAssignInst(assignment, JcRawCastExpr(pop(), type))
                push(assignment)
            }

            Opcodes.INSTANCEOF -> {
                val assignment = nextRegister(PredefinedPrimitives.boolean.typeName())
                instructionList(insnNode) += JcRawAssignInst(assignment, JcRawInstanceOfExpr(pop(), type))
                push(assignment)
            }

            else -> error("Unknown opcode ${insnNode.opcode} in TypeInsnNode")
        }
    }

    private fun buildVarInsnNode(insnNode: VarInsnNode) {
        when (insnNode.opcode) {
            in Opcodes.ISTORE..Opcodes.ASTORE -> instructionList(insnNode).add(local(insnNode.`var`, pop()))

            in Opcodes.ILOAD..Opcodes.ALOAD -> push(local(insnNode.`var`))
            else -> error("Unknown opcode ${insnNode.opcode} in VarInsnNode")
        }
    }
}
