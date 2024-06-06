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

package org.jacodb.analysis.impl.custom

import org.jacodb.api.jvm.cfg.JcAssignInst
import org.jacodb.api.jvm.cfg.JcBasicBlock
import org.jacodb.api.jvm.cfg.JcInst
import org.jacodb.api.jvm.cfg.JcInstRef
import org.jacodb.api.jvm.cfg.JcValue
import org.jacodb.impl.cfg.JcBlockGraphImpl
import java.util.BitSet

class ReachingDefinitionsAnalysis(val blockGraph: JcBlockGraphImpl) {
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
