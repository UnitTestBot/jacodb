package org.utbot.jcdb.api.cfg

import org.utbot.jcdb.api.*

class JcGraph(
    val classpath: JcClasspath,
    val instructions: List<JcInst>,
) : Iterable<JcInst> {
    private val indexMap = instructions.mapIndexed { index, jcInst -> jcInst to index }.toMap()

    private val predecessorMap = mutableMapOf<JcInst, MutableSet<JcInst>>()
    private val successorMap = mutableMapOf<JcInst, MutableSet<JcInst>>()

    private val throwPredecessors = mutableMapOf<JcCatchInst, MutableSet<JcInst>>()
    private val throwSuccessors = mutableMapOf<JcInst, MutableSet<JcCatchInst>>()

    val entry: JcInst get() = instructions.single { predecessors(it).isEmpty() && throwers(it).isEmpty() }

    init {
        for (inst in instructions) {
            val successors = when (inst) {
                is JcTerminatingInst -> mutableSetOf()
                is JcBranchingInst -> inst.successors.map { inst(it) }.toMutableSet()
                else -> mutableSetOf(next(inst))
            }
            successorMap[inst] = successors

            for (successor in successors) {
                predecessorMap.getOrPut(successor, ::mutableSetOf) += inst
            }

            if (inst is JcCatchInst) {
                throwPredecessors[inst] = inst.throwers.map { inst(it) }.toMutableSet()
                inst.throwers.forEach {
                    throwSuccessors.getOrPut(inst(it), ::mutableSetOf).add(inst)
                }
            }
        }
    }

    private fun index(inst: JcInst) = indexMap.getOrDefault(inst, -1)

    fun ref(inst: JcInst): JcInstRef = JcInstRef(index(inst))
    fun inst(ref: JcInstRef) = instructions[ref.index]

    fun previous(inst: JcInst): JcInst = instructions[ref(inst).index - 1]
    fun next(inst: JcInst): JcInst = instructions[ref(inst).index + 1]

    fun successors(inst: JcInst): Set<JcInst> = successorMap.getOrDefault(inst, emptySet())
    fun predecessors(inst: JcInst): Set<JcInst> = predecessorMap.getOrDefault(inst, emptySet())

    fun throwers(inst: JcInst): Set<JcInst> = throwPredecessors.getOrDefault(inst, emptySet())
    fun catchers(inst: JcInst): Set<JcCatchInst> = throwSuccessors.getOrDefault(inst, emptySet())

    fun previous(inst: JcInstRef): JcInst = previous(inst(inst))
    fun next(inst: JcInstRef): JcInst = next(inst(inst))

    fun successors(inst: JcInstRef): Set<JcInst> = successors(inst(inst))
    fun predecessors(inst: JcInstRef): Set<JcInst> = predecessors(inst(inst))

    fun throwers(inst: JcInstRef): Set<JcInst> = throwers(inst(inst))
    fun catchers(inst: JcInstRef): Set<JcCatchInst> = catchers(inst(inst))

    fun blockGraph(): JcBlockGraph = JcBlockGraph(this)

    override fun toString(): String = instructions.joinToString("\n")

    override fun iterator() = instructions.iterator()
}

class JcBasicBlock(val start: JcInstRef, val end: JcInstRef)

class JcBlockGraph(
    val jcGraph: JcGraph
) : Iterable<JcBasicBlock> {
    private val _basicBlocks = mutableListOf<JcBasicBlock>()
    private val predecessorMap = mutableMapOf<JcBasicBlock, MutableSet<JcBasicBlock>>()
    private val successorMap = mutableMapOf<JcBasicBlock, MutableSet<JcBasicBlock>>()
    private val catchersMap = mutableMapOf<JcBasicBlock, MutableSet<JcBasicBlock>>()
    private val throwersMap = mutableMapOf<JcBasicBlock, MutableSet<JcBasicBlock>>()

    val basicBlocks: List<JcBasicBlock> get() = _basicBlocks
    val entry get() = basicBlocks.single { predecessors(it).isEmpty() && jcGraph.throwers(it.start).isEmpty() }

    init {
        val inst2Block = mutableMapOf<JcInst, JcBasicBlock>()

        val currentRefs = mutableListOf<JcInstRef>()

        val createBlock = {
            val block = JcBasicBlock(currentRefs.first(), currentRefs.last())
            for (ref in currentRefs) {
                inst2Block[jcGraph.inst(ref)] = block
            }
//                instructions(block).map { jcGraph.catchers(it) }.toSet().single()
            currentRefs.clear()
            _basicBlocks.add(block)
        }
        for (inst in jcGraph.instructions) {
            val currentRef = jcGraph.ref(inst)
            val shouldBeAddedBefore = jcGraph.predecessors(inst).size <= 1 || currentRefs.isEmpty()
            val shouldTerminate = when {
                currentRefs.isEmpty() -> false
                else -> jcGraph.catchers(currentRefs.first()) != jcGraph.catchers(currentRef)
            }
            when {
                inst is JcBranchingInst
                        || inst is JcTerminatingInst
                        || jcGraph.predecessors(inst).size > 1
                        || shouldTerminate -> {
                    if (shouldBeAddedBefore) currentRefs += currentRef
                    createBlock()
                    if (!shouldBeAddedBefore) {
                        currentRefs += currentRef
                        createBlock()
                    }
                }

                else -> {
                    currentRefs += currentRef
                }
            }
        }
        if (currentRefs.isNotEmpty()) {
            val block = JcBasicBlock(currentRefs.first(), currentRefs.last())
            for (ref in currentRefs) {
                inst2Block[jcGraph.inst(ref)] = block
            }
            currentRefs.clear()
            _basicBlocks.add(block)
        }
        for (block in _basicBlocks) {
            predecessorMap.getOrPut(block, ::mutableSetOf) += jcGraph.predecessors(block.start).map { inst2Block[it]!! }
            successorMap.getOrPut(block, ::mutableSetOf) += jcGraph.successors(block.end).map { inst2Block[it]!! }
            catchersMap.getOrPut(block, ::mutableSetOf) += jcGraph.catchers(block.start).map { inst2Block[it]!! }.also {
                for (catcher in it) {
                    throwersMap.getOrPut(catcher, ::mutableSetOf) += block
                }
            }
        }
    }

    fun instructions(block: JcBasicBlock) = (block.start.index..block.end.index).map { jcGraph.instructions[it] }
    fun predecessors(block: JcBasicBlock) = predecessorMap.getOrDefault(block, emptySet())
    fun successors(block: JcBasicBlock) = successorMap.getOrDefault(block, emptySet())

    fun catchers(block: JcBasicBlock) = catchersMap.getOrDefault(block, emptySet())
    fun throwers(block: JcBasicBlock) = throwersMap.getOrDefault(block, emptySet())

    override fun iterator() = basicBlocks.iterator()
}


sealed interface JcInst {
    val operands: List<JcExpr>

    fun <T> accept(visitor: JcInstVisitor<T>): T
}

data class JcInstRef internal constructor(
    val index: Int
)

class JcAssignInst(
    val lhv: JcValue,
    val rhv: JcExpr
) : JcInst {

    override val operands: List<JcExpr>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv = $rhv"

    override fun <T> accept(visitor: JcInstVisitor<T>): T {
        return visitor.visitJcAssignInst(this)
    }
}

class JcEnterMonitorInst(
    val monitor: JcValue
) : JcInst {
    override val operands: List<JcExpr>
        get() = listOf(monitor)

    override fun toString(): String = "enter monitor $monitor"

    override fun <T> accept(visitor: JcInstVisitor<T>): T {
        return visitor.visitJcEnterMonitorInst(this)
    }
}

class JcExitMonitorInst(
    val monitor: JcValue
) : JcInst {
    override val operands: List<JcExpr>
        get() = listOf(monitor)

    override fun toString(): String = "exit monitor $monitor"

    override fun <T> accept(visitor: JcInstVisitor<T>): T {
        return visitor.visitJcExitMonitorInst(this)
    }
}

class JcCallInst(
    val callExpr: JcCallExpr
) : JcInst {
    override val operands: List<JcExpr>
        get() = listOf(callExpr)

    override fun toString(): String = "$callExpr"

    override fun <T> accept(visitor: JcInstVisitor<T>): T {
        return visitor.visitJcCallInst(this)
    }
}

sealed interface JcTerminatingInst : JcInst

class JcReturnInst(
    val returnValue: JcValue?
) : JcTerminatingInst {
    override val operands: List<JcExpr>
        get() = listOfNotNull(returnValue)

    override fun toString(): String = "return" + (returnValue?.let { " $it" } ?: "")

    override fun <T> accept(visitor: JcInstVisitor<T>): T {
        return visitor.visitJcReturnInst(this)
    }
}

class JcThrowInst(
    val throwable: JcValue
) : JcTerminatingInst {
    override val operands: List<JcExpr>
        get() = listOf(throwable)

    override fun toString(): String = "throw $throwable"

    override fun <T> accept(visitor: JcInstVisitor<T>): T {
        return visitor.visitJcThrowInst(this)
    }
}

class JcCatchInst(
    val throwable: JcValue,
    val throwers: List<JcInstRef>
) : JcInst {
    override val operands: List<JcExpr>
        get() = listOf(throwable)

    override fun toString(): String = "catch ($throwable: ${throwable.type})"

    override fun <T> accept(visitor: JcInstVisitor<T>): T {
        return visitor.visitJcCatchInst(this)
    }
}

sealed interface JcBranchingInst : JcInst {
    val successors: List<JcInstRef>
}

class JcGotoInst(
    val target: JcInstRef
) : JcBranchingInst {
    override val operands: List<JcExpr>
        get() = emptyList()

    override val successors: List<JcInstRef>
        get() = listOf(target)

    override fun toString(): String = "goto $target"

    override fun <T> accept(visitor: JcInstVisitor<T>): T {
        return visitor.visitJcGotoInst(this)
    }
}

class JcIfInst(
    val condition: JcConditionExpr,
    val trueBranch: JcInstRef,
    val falseBranch: JcInstRef
) : JcBranchingInst {
    override val operands: List<JcExpr>
        get() = listOf(condition)

    override val successors: List<JcInstRef>
        get() = listOf(trueBranch, falseBranch)

    override fun toString(): String = "if ($condition)"

    override fun <T> accept(visitor: JcInstVisitor<T>): T {
        return visitor.visitJcIfInst(this)
    }
}

class JcSwitchInst(
    val key: JcValue,
    val branches: Map<JcValue, JcInstRef>,
    val default: JcInstRef
) : JcBranchingInst {
    override val operands: List<JcExpr>
        get() = listOf(key) + branches.keys

    override val successors: List<JcInstRef>
        get() = branches.values + default

    override fun toString(): String = "switch ($key)"

    override fun <T> accept(visitor: JcInstVisitor<T>): T {
        return visitor.visitJcSwitchInst(this)
    }
}

sealed interface JcExpr {
    val type: JcType
    val operands: List<JcValue>

    fun <T> accept(visitor: JcExprVisitor<T>): T
}

interface JcBinaryExpr : JcExpr {
    val lhv: JcValue
    val rhv: JcValue
}

data class JcAddExpr(
    override val type: JcType,
    override val lhv: JcValue,
    override val rhv: JcValue
) : JcBinaryExpr {
    override val operands: List<JcValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv + $rhv"

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcAddExpr(this)
    }
}

data class JcAndExpr(
    override val type: JcType,
    override val lhv: JcValue,
    override val rhv: JcValue
) : JcBinaryExpr {
    override val operands: List<JcValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv & $rhv"

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcAndExpr(this)
    }
}

data class JcCmpExpr(
    override val type: JcType,
    override val lhv: JcValue,
    override val rhv: JcValue
) : JcBinaryExpr {
    override val operands: List<JcValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv cmp $rhv"

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcCmpExpr(this)
    }
}

data class JcCmpgExpr(
    override val type: JcType,
    override val lhv: JcValue,
    override val rhv: JcValue
) : JcBinaryExpr {
    override val operands: List<JcValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv cmpg $rhv"

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcCmpgExpr(this)
    }
}

data class JcCmplExpr(
    override val type: JcType,
    override val lhv: JcValue,
    override val rhv: JcValue
) : JcBinaryExpr {
    override val operands: List<JcValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv cmpl $rhv"

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcCmplExpr(this)
    }
}

data class JcDivExpr(
    override val type: JcType,
    override val lhv: JcValue,
    override val rhv: JcValue
) : JcBinaryExpr {
    override val operands: List<JcValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv / $rhv"

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcDivExpr(this)
    }
}

data class JcMulExpr(
    override val type: JcType,
    override val lhv: JcValue,
    override val rhv: JcValue
) : JcBinaryExpr {
    override val operands: List<JcValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv * $rhv"

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcMulExpr(this)
    }
}

sealed interface JcConditionExpr : JcBinaryExpr

data class JcEqExpr(
    override val type: JcType,
    override val lhv: JcValue,
    override val rhv: JcValue
) : JcConditionExpr {
    override val operands: List<JcValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv == $rhv"

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcEqExpr(this)
    }
}

data class JcNeqExpr(
    override val type: JcType,
    override val lhv: JcValue,
    override val rhv: JcValue
) : JcConditionExpr {
    override val operands: List<JcValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv != $rhv"

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcNeqExpr(this)
    }
}

data class JcGeExpr(
    override val type: JcType,
    override val lhv: JcValue,
    override val rhv: JcValue
) : JcConditionExpr {
    override val operands: List<JcValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv >= $rhv"

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcGeExpr(this)
    }
}

data class JcGtExpr(
    override val type: JcType,
    override val lhv: JcValue,
    override val rhv: JcValue
) : JcConditionExpr {
    override val operands: List<JcValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv > $rhv"

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcGtExpr(this)
    }
}

data class JcLeExpr(
    override val type: JcType,
    override val lhv: JcValue,
    override val rhv: JcValue
) : JcConditionExpr {
    override val operands: List<JcValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv <= $rhv"

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcLeExpr(this)
    }
}

data class JcLtExpr(
    override val type: JcType,
    override val lhv: JcValue,
    override val rhv: JcValue
) : JcConditionExpr {
    override val operands: List<JcValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv < $rhv"

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcLtExpr(this)
    }
}

data class JcOrExpr(
    override val type: JcType,
    override val lhv: JcValue,
    override val rhv: JcValue
) : JcBinaryExpr {
    override val operands: List<JcValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv | $rhv"

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcOrExpr(this)
    }
}

data class JcRemExpr(
    override val type: JcType,
    override val lhv: JcValue,
    override val rhv: JcValue
) : JcBinaryExpr {
    override val operands: List<JcValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv % $rhv"

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcRemExpr(this)
    }
}

data class JcShlExpr(
    override val type: JcType,
    override val lhv: JcValue,
    override val rhv: JcValue
) : JcBinaryExpr {
    override val operands: List<JcValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv << $rhv"

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcShlExpr(this)
    }
}

data class JcShrExpr(
    override val type: JcType,
    override val lhv: JcValue,
    override val rhv: JcValue
) : JcBinaryExpr {
    override val operands: List<JcValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv >> $rhv"

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcShrExpr(this)
    }
}

data class JcSubExpr(
    override val type: JcType,
    override val lhv: JcValue,
    override val rhv: JcValue
) : JcBinaryExpr {
    override val operands: List<JcValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv - $rhv"

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcSubExpr(this)
    }
}

data class JcUshrExpr(
    override val type: JcType,
    override val lhv: JcValue,
    override val rhv: JcValue
) : JcBinaryExpr {
    override val operands: List<JcValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv u<< $rhv"

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcUshrExpr(this)
    }
}

data class JcXorExpr(
    override val type: JcType,
    override val lhv: JcValue,
    override val rhv: JcValue
) : JcBinaryExpr {
    override val operands: List<JcValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv ^ $rhv"

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcXorExpr(this)
    }
}

data class JcLengthExpr(
    override val type: JcType,
    val array: JcValue
) : JcExpr {
    override val operands: List<JcValue>
        get() = listOf(array)

    override fun toString(): String = "$array.length"

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcLengthExpr(this)
    }
}

data class JcNegExpr(
    override val type: JcType,
    val operand: JcValue
) : JcExpr {
    override val operands: List<JcValue>
        get() = listOf(operand)

    override fun toString(): String = "-$operand"

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcNegExpr(this)
    }
}

data class JcCastExpr(
    override val type: JcType,
    val operand: JcValue
) : JcExpr {
    override val operands: List<JcValue>
        get() = listOf(operand)

    override fun toString(): String = "(${type.typeName}) $operand"

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcCastExpr(this)
    }
}

data class JcNewExpr(
    override val type: JcType
) : JcExpr {
    override val operands: List<JcValue>
        get() = emptyList()

    override fun toString(): String = "new ${type.typeName}"

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcNewExpr(this)
    }
}

data class JcNewArrayExpr(
    override val type: JcType,
    val dimensions: List<JcValue>
) : JcExpr {
    constructor(type: JcType, length: JcValue) : this(type, listOf(length))

    override val operands: List<JcValue>
        get() = dimensions

    override fun toString(): String = "new ${type.typeName}${dimensions.joinToString("") { "[$it]" }}"

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcNewArrayExpr(this)
    }
}

data class JcInstanceOfExpr(
    override val type: JcType,
    val operand: JcValue,
    val targetType: JcType
) : JcExpr {
    override val operands: List<JcValue>
        get() = listOf(operand)

    override fun toString(): String = "$operand instanceof $targetType"

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcInstanceOfExpr(this)
    }
}

sealed interface JcCallExpr : JcExpr {
    val method: JcTypedMethod
    val args: List<JcValue>

    override val type get() = method.returnType

    override val operands: List<JcValue>
        get() = args
}

data class JcLambdaExpr(
    override val method: JcTypedMethod,
    override val args: List<JcValue>,
) : JcCallExpr {
    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcLambdaExpr(this)
    }
}

data class JcDynamicCallExpr(
    val bsm: JcTypedMethod,
    val bsmArgs: List<BsmArg>,
    val callCiteMethodName: String,
    val callCiteArgTypes: List<JcType>,
    val callCiteReturnType: JcType,
    val callCiteArgs: List<JcValue>
) : JcCallExpr {
    override val method get() = bsm
    override val args get() = callCiteArgs

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcDynamicCallExpr(this)
    }
}

data class JcVirtualCallExpr(
    override val method: JcTypedMethod,
    val instance: JcValue,
    override val args: List<JcValue>,
) : JcCallExpr {
    override val operands: List<JcValue>
        get() = listOf(instance) + args

    override fun toString(): String =
        "$instance.${method.name}${args.joinToString(prefix = "(", postfix = ")", separator = ", ")}"

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcVirtualCallExpr(this)
    }
}


data class JcStaticCallExpr(
    override val method: JcTypedMethod,
    override val args: List<JcValue>,
) : JcCallExpr {
    override fun toString(): String =
        "${method.method.enclosingClass.name}.${method.name}${
            args.joinToString(
                prefix = "(",
                postfix = ")",
                separator = ", "
            )
        }"

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcStaticCallExpr(this)
    }
}

data class JcSpecialCallExpr(
    override val method: JcTypedMethod,
    val instance: JcValue,
    override val args: List<JcValue>,
) : JcCallExpr {
    override fun toString(): String =
        "$instance.${method.name}${args.joinToString(prefix = "(", postfix = ")", separator = ", ")}"

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcSpecialCallExpr(this)
    }
}


sealed interface JcValue : JcExpr {
    override val operands: List<JcValue>
        get() = emptyList()
}

sealed interface JcSimpleValue : JcValue

data class JcThis(override val type: JcType) : JcSimpleValue {
    override fun toString(): String = "this"

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcThis(this)
    }
}

data class JcArgument(val index: Int, val name: String?, override val type: JcType) : JcSimpleValue {
    override fun toString(): String = name ?: "arg$$index"

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcArgument(this)
    }
}

data class JcLocal(val name: String, override val type: JcType) : JcSimpleValue {
    override fun toString(): String = name

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcLocal(this)
    }
}

sealed interface JcComplexValue : JcValue

data class JcFieldRef(
    val instance: JcValue?,
    val field: JcTypedField
) : JcComplexValue {
    override val type: JcType get() = this.field.fieldType

    override fun toString(): String = "${instance ?: field.field.enclosingClass}.${field.name}"

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcFieldRef(this)
    }
}

data class JcArrayAccess(
    val array: JcValue,
    val index: JcValue,
    override val type: JcType
) : JcComplexValue {
    override fun toString(): String = "$array[$index]"

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcArrayAccess(this)
    }
}

sealed interface JcConstant : JcSimpleValue

data class JcBool(val value: Boolean, override val type: JcType) : JcConstant {
    override fun toString(): String = "$value"

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcBool(this)
    }
}

data class JcByte(val value: Byte, override val type: JcType) : JcConstant {
    override fun toString(): String = "$value"

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcByte(this)
    }
}

data class JcChar(val value: Char, override val type: JcType) : JcConstant {
    override fun toString(): String = "$value"

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcChar(this)
    }
}

data class JcShort(val value: Short, override val type: JcType) : JcConstant {
    override fun toString(): String = "$value"

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcShort(this)
    }
}

data class JcInt(val value: Int, override val type: JcType) : JcConstant {
    override fun toString(): String = "$value"

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcInt(this)
    }
}

data class JcLong(val value: Long, override val type: JcType) : JcConstant {
    override fun toString(): String = "$value"

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcLong(this)
    }
}

data class JcFloat(val value: Float, override val type: JcType) : JcConstant {
    override fun toString(): String = "$value"

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcFloat(this)
    }
}

data class JcDouble(val value: Double, override val type: JcType) : JcConstant {
    override fun toString(): String = "$value"

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcDouble(this)
    }
}

data class JcNullConstant(override val type: JcType) : JcConstant {
    override fun toString(): String = "null"

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcNullConstant(this)
    }
}

data class JcStringConstant(val value: String, override val type: JcType) : JcConstant {
    override fun toString(): String = "\"$value\""

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcStringConstant(this)
    }
}

data class JcClassConstant(val klass: JcClassType, override val type: JcType) : JcConstant {
    override fun toString(): String = "${klass.jcClass.name}.class"

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcClassConstant(this)
    }
}

data class JcMethodConstant(
    val method: JcTypedMethod,
    override val type: JcType
) : JcConstant {
    override fun toString(): String = "${method.method.enclosingClass.name}::${method.name}${
        method.parameters.joinToString(
            prefix = "(",
            postfix = ")",
            separator = ", "
        )
    }:${method.returnType}"

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcMethodConstant(this)
    }
}
