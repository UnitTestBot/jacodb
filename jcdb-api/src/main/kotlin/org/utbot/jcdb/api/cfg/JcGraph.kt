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

package org.utbot.jcdb.api.cfg

import org.utbot.jcdb.api.BsmArg
import org.utbot.jcdb.api.JcClassType
import org.utbot.jcdb.api.JcClasspath
import org.utbot.jcdb.api.JcType
import org.utbot.jcdb.api.JcTypedField
import org.utbot.jcdb.api.JcTypedMethod
import org.utbot.jcdb.api.cfg.ext.JcExceptionResolver
import org.utbot.jcdb.api.isSubtypeOf

class JcGraph(
    val classpath: JcClasspath,
    val instructions: List<JcInst>,
) : Iterable<JcInst> {
    private val indexMap = instructions.mapIndexed { index, jcInst -> jcInst to index }.toMap()

    private val predecessorMap = mutableMapOf<JcInst, MutableSet<JcInst>>()
    private val successorMap = mutableMapOf<JcInst, MutableSet<JcInst>>()

    private val throwPredecessors = mutableMapOf<JcCatchInst, MutableSet<JcInst>>()
    private val throwSuccessors = mutableMapOf<JcInst, MutableSet<JcCatchInst>>()
    private val _throwExits = mutableMapOf<JcClassType, MutableSet<JcInstRef>>()

    val entry: JcInst get() = instructions.single { predecessors(it).isEmpty() && throwers(it).isEmpty() }
    val exits: List<JcInst> get() = instructions.filterIsInstance<JcTerminatingInst>()

    /**
     * returns a map of possible exceptions that may be thrown from this method
     * for each instruction of in the graph in determines possible thrown exceptions using
     * #JcExceptionResolver class
     */
    val throwExits: Map<JcClassType, List<JcInst>> get() = _throwExits.mapValues { (_, refs) -> refs.map { inst(it) } }

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

        for (inst in instructions) {
            for (throwableType in inst.accept(JcExceptionResolver(classpath))) {
                if (!catchers(inst).any { throwableType.jcClass isSubtypeOf (it.throwable.type as JcClassType).jcClass }) {
                    _throwExits.getOrPut(throwableType, ::mutableSetOf) += ref(inst)
                }
            }
        }
    }

    private fun index(inst: JcInst) = indexMap.getOrDefault(inst, -1)

    fun ref(inst: JcInst): JcInstRef = JcInstRef(index(inst))
    fun inst(ref: JcInstRef): JcInst = instructions[ref.index]

    fun previous(inst: JcInst): JcInst = instructions[ref(inst).index - 1]
    fun next(inst: JcInst): JcInst = instructions[ref(inst).index + 1]

    /**
     * `successors` and `predecessors` represent normal control flow
     */
    fun successors(inst: JcInst): Set<JcInst> = successorMap.getOrDefault(inst, emptySet())
    fun predecessors(inst: JcInst): Set<JcInst> = predecessorMap.getOrDefault(inst, emptySet())

    /**
     * `throwers` and `catchers` represent control flow when an exception occurs
     * `throwers` returns an empty set for every instruction except `JcCatchInst`
     */
    fun throwers(inst: JcInst): Set<JcInst> = throwPredecessors.getOrDefault(inst, emptySet())
    fun catchers(inst: JcInst): Set<JcCatchInst> = throwSuccessors.getOrDefault(inst, emptySet())

    fun previous(inst: JcInstRef): JcInst = previous(inst(inst))
    fun next(inst: JcInstRef): JcInst = next(inst(inst))

    fun successors(inst: JcInstRef): Set<JcInst> = successors(inst(inst))
    fun predecessors(inst: JcInstRef): Set<JcInst> = predecessors(inst(inst))

    fun throwers(inst: JcInstRef): Set<JcInst> = throwers(inst(inst))
    fun catchers(inst: JcInstRef): Set<JcCatchInst> = catchers(inst(inst))

    /**
     * get all the exceptions types that this instruction may throw and terminate
     * current method
     */
    fun exceptionExits(inst: JcInst): Set<JcClassType> =
        inst.accept(JcExceptionResolver(classpath)).filter { it in _throwExits }.toSet()

    fun exceptionExits(ref: JcInstRef): Set<JcClassType> = exceptionExits(inst(ref))

    fun blockGraph(): JcBlockGraph = JcBlockGraph(this)

    override fun toString(): String = instructions.joinToString("\n")

    override fun iterator(): Iterator<JcInst> = instructions.iterator()
}

/**
 * Basic block represents a list of instructions that:
 * - guaranteed to execute one after other during normal control flow
 * (i.e. no exceptions thrown)
 * - all have the same exception handlers (i.e. `jcGraph.catchers(inst)`
 * returns the same result for all instructions of the basic block)
 *
 * Because of the current implementation of basic block API, block is *not*
 * guaranteed to end with a terminating (i.e. `JcTerminatingInst` or `JcBranchingInst`) instruction.
 * However, any terminating instruction is guaranteed to be the last instruction of a basic block.
 */
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
    val entry: JcBasicBlock
        get() = basicBlocks.single {
            predecessors(it).isEmpty() && jcGraph.throwers(it.start).isEmpty()
        }
    val exits: List<JcBasicBlock> get() = basicBlocks.filter { successors(it).isEmpty() }

    init {
        val inst2Block = mutableMapOf<JcInst, JcBasicBlock>()

        val currentRefs = mutableListOf<JcInstRef>()

        val createBlock = {
            val block = JcBasicBlock(currentRefs.first(), currentRefs.last())
            for (ref in currentRefs) {
                inst2Block[jcGraph.inst(ref)] = block
            }
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
            if (shouldTerminate) {
                createBlock()
            }
            when {
                inst is JcBranchingInst
                        || inst is JcTerminatingInst
                        || jcGraph.predecessors(inst).size > 1 -> {
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

    fun instructions(block: JcBasicBlock): List<JcInst> =
        (block.start.index..block.end.index).map { jcGraph.instructions[it] }

    /**
     * `successors` and `predecessors` represent normal control flow
     */
    fun predecessors(block: JcBasicBlock): Set<JcBasicBlock> = predecessorMap.getOrDefault(block, emptySet())
    fun successors(block: JcBasicBlock): Set<JcBasicBlock> = successorMap.getOrDefault(block, emptySet())

    /**
     * `throwers` and `catchers` represent control flow when an exception occurs
     */
    fun catchers(block: JcBasicBlock): Set<JcBasicBlock> = catchersMap.getOrDefault(block, emptySet())
    fun throwers(block: JcBasicBlock): Set<JcBasicBlock> = throwersMap.getOrDefault(block, emptySet())

    override fun iterator(): Iterator<JcBasicBlock> = basicBlocks.iterator()
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

/**
 * JcLambdaExpr is created when we can resolve the `invokedynamic` instruction.
 * When Java or Kotlin compiles a code with the lambda call, it generates
 * an `invokedynamic` instruction which returns a call cite object. When we can
 * resolve the lambda call, we create `JcLambdaExpr` that returns a similar call cite
 * object, but stores a reference to the actual method
 */
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

/**
 * `invokevirtual` and `invokeinterface` instructions of the bytecode
 * are both represented with `JcVirtualCallExpr` for simplicity
 */
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
