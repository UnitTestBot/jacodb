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

import org.utbot.jcdb.api.BsmStringArg
import org.utbot.jcdb.api.JcClassType
import org.utbot.jcdb.api.PredefinedPrimitives
import org.utbot.jcdb.api.autoboxIfNeeded
import org.utbot.jcdb.api.ext.findTypeOrNull
import java.util.*
import kotlin.collections.ArrayDeque
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.collections.MutableSet
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.emptyList
import kotlin.collections.filter
import kotlin.collections.first
import kotlin.collections.flatMap
import kotlin.collections.fold
import kotlin.collections.getOrDefault
import kotlin.collections.getOrPut
import kotlin.collections.indices
import kotlin.collections.isNotEmpty
import kotlin.collections.listOf
import kotlin.collections.map
import kotlin.collections.mapValues
import kotlin.collections.minusAssign
import kotlin.collections.mutableListOf
import kotlin.collections.mutableMapOf
import kotlin.collections.mutableSetOf
import kotlin.collections.plus
import kotlin.collections.plusAssign
import kotlin.collections.putAll
import kotlin.collections.random
import kotlin.collections.set
import kotlin.collections.toMutableSet

class ReachingDefinitionsAnalysis(val blockGraph: JcBlockGraph) {
    val jcGraph get() = blockGraph.jcGraph

    private val nDefinitions = jcGraph.instructions.size
    private val ins = mutableMapOf<JcBasicBlock, BitSet>()
    private val outs = mutableMapOf<JcBasicBlock, BitSet>()
    private val assignmentsMap = mutableMapOf<JcValue, MutableSet<JcInstRef>>()

    init {
        initAssignmentsMap()
        val entry = blockGraph.entry
        for (block in blockGraph)
            outs[block] = emptySet()

        val queue = ArrayDeque<JcBasicBlock>().also { it += entry }
        val notVisited = blockGraph.toMutableSet()
        while (queue.isNotEmpty() || notVisited.isNotEmpty()) {
            val current = when {
                queue.isNotEmpty() -> queue.removeFirst()
                else -> notVisited.random()
            }
            notVisited -= current

            ins[current] = fullPredecessors(current).map { outs[it]!! }.fold(emptySet()) { acc, bitSet ->
                acc.or(bitSet)
                acc
            }

            val oldOut = outs[current]!!.clone() as BitSet
            val newOut = gen(current)

            if (oldOut != newOut) {
                outs[current] = newOut
                for (successor in fullSuccessors(current)) {
                    queue += successor
                }
            }
        }
    }

    private fun initAssignmentsMap() {
        for (inst in jcGraph) {
            if (inst is JcAssignInst) {
                assignmentsMap.getOrPut(inst.lhv, ::mutableSetOf) += jcGraph.ref(inst)
            }
        }
    }

    private fun emptySet(): BitSet = BitSet(nDefinitions)

    private fun gen(block: JcBasicBlock): BitSet {
        val inSet = ins[block]!!.clone() as BitSet
        for (inst in blockGraph.instructions(block)) {
            if (inst is JcAssignInst) {
                for (kill in assignmentsMap.getOrDefault(inst.lhv, mutableSetOf())) {
                    inSet[kill] = false
                }
                inSet[jcGraph.ref(inst)] = true
            }
        }
        return inSet
    }

    private fun fullPredecessors(block: JcBasicBlock) = blockGraph.predecessors(block) + blockGraph.throwers(block)
    private fun fullSuccessors(block: JcBasicBlock) = blockGraph.successors(block) + blockGraph.catchers(block)

    private operator fun BitSet.set(ref: JcInstRef, value: Boolean) {
        this.set(ref.index, value)
    }

    fun outs(block: JcBasicBlock): List<JcInst> {
        val defs = outs.getOrDefault(block, emptySet())
        return (0 until nDefinitions).filter { defs[it] }.map { jcGraph.instructions[it] }
    }
}


class StringConcatSimplifier(
    val jcGraph: JcGraph
) : DefaultJcInstVisitor<JcInst> {
    override val defaultInstHandler: (JcInst) -> JcInst
        get() = { it }
    private val instructionReplacements = mutableMapOf<JcInst, JcInst>()
    private val instructions = mutableListOf<JcInst>()
    private val catchReplacements = mutableMapOf<JcInst, MutableList<JcInst>>()
    private val instructionIndices = mutableMapOf<JcInst, Int>()

    fun build(): JcGraph {
        var changed = false
        for (inst in jcGraph) {
            if (inst is JcAssignInst) {
                val lhv = inst.lhv
                val rhv = inst.rhv

                if (rhv is JcDynamicCallExpr && rhv.callCiteMethodName == "makeConcatWithConstants") {
                    val stringType = jcGraph.classpath.findTypeOrNull<String>() as JcClassType

                    val (first, second) = when {
                        rhv.callCiteArgs.size == 2 -> rhv.callCiteArgs
                        rhv.callCiteArgs.size == 1 && rhv.bsmArgs.size == 1 && rhv.bsmArgs[0] is BsmStringArg -> listOf(
                            rhv.callCiteArgs[0],
                            JcStringConstant((rhv.bsmArgs[0] as BsmStringArg).value, stringType)
                        )

                        else -> {
                            instructions += inst
                            continue
                        }
                    }
                    changed = true

                    val result = mutableListOf<JcInst>()
                    val firstStr = stringify(first, result)
                    val secondStr = stringify(second, result)

                    val concatMethod = stringType.methods.first {
                        it.name == "concat" && it.parameters.size == 1 && it.parameters.first().type == stringType
                    }
                    val newConcatExpr = JcVirtualCallExpr(concatMethod, firstStr, listOf(secondStr))
                    result += JcAssignInst(lhv, newConcatExpr)
                    instructionReplacements[inst] = result.first()
                    catchReplacements[inst] = result
                    instructions += result
                } else {
                    instructions += inst
                }
            } else {
                instructions += inst
            }
        }

        if (!changed) return jcGraph

        instructionIndices.putAll(instructions.indices.map { instructions[it] to it })
        val mappedInstructions = instructions.map { it.accept(this) }
        return JcGraph(jcGraph.classpath, mappedInstructions)
    }

    private fun stringify(value: JcValue, instList: MutableList<JcInst>): JcValue {
        val cp = jcGraph.classpath
        val stringType = cp.findTypeOrNull<String>()!!
        return when {
            PredefinedPrimitives.matches(value.type.typeName) -> {
                val boxedType = value.type.autoboxIfNeeded() as JcClassType
                val method = boxedType.methods.first {
                    it.name == "toString" && it.parameters.size == 1 && it.parameters.first().type == value.type
                }
                val toStringExpr = JcStaticCallExpr(method, listOf(value))
                val assignment = JcLocal("${value}String", stringType)
                instList += JcAssignInst(assignment, toStringExpr)
                assignment
            }

            value.type == stringType -> value
            else -> {
                val boxedType = value.type.autoboxIfNeeded() as JcClassType
                val method = boxedType.methods.first {
                    it.name == "toString" && it.parameters.isEmpty()
                }
                val toStringExpr = JcVirtualCallExpr(method, value, emptyList())
                val assignment = JcLocal("${value}String", stringType)
                instList += JcAssignInst(assignment, toStringExpr)
                assignment
            }
        }
    }

    private fun indexOf(instRef: JcInstRef) = JcInstRef(
        instructionIndices[instructionReplacements.getOrDefault(jcGraph.inst(instRef), jcGraph.inst(instRef))] ?: -1
    )

    private fun indicesOf(instRef: JcInstRef) =
        catchReplacements.getOrDefault(jcGraph.inst(instRef), listOf(jcGraph.inst(instRef))).map {
            JcInstRef(instructions.indexOf(it))
        }

    override fun visitJcCatchInst(inst: JcCatchInst): JcInst = JcCatchInst(
        inst.throwable,
        inst.throwers.flatMap { indicesOf(it) }
    )

    override fun visitJcGotoInst(inst: JcGotoInst): JcInst = JcGotoInst(indexOf(inst.target))

    override fun visitJcIfInst(inst: JcIfInst): JcInst = JcIfInst(
        inst.condition,
        indexOf(inst.trueBranch),
        indexOf(inst.falseBranch)
    )

    override fun visitJcSwitchInst(inst: JcSwitchInst): JcInst = JcSwitchInst(
        inst.key,
        inst.branches.mapValues { indexOf(it.value) },
        indexOf(inst.default)
    )
}
