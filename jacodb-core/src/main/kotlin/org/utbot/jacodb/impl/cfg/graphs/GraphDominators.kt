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

package org.utbot.jacodb.impl.cfg.graphs

import org.utbot.jacodb.api.cfg.JcCatchInst
import org.utbot.jacodb.api.cfg.JcGraph
import org.utbot.jacodb.api.cfg.JcInst
import java.util.*


/**
 * Calculate dominators for basic blocks.
 *
 * Uses the algorithm contained in Dragon book, pg. 670-1.
 */
open class GraphDominators(val graph: JcGraph) {

    private val size = graph.instructions.size

    private val head = graph.entry
    private val flowSets = HashMap<Int, BitSet>(size)

    fun find() {
        val fullSet = BitSet(size)
        fullSet.flip(0, size) // set all to true

        // set up domain for intersection: head nodes are only dominated by themselves,
        // other nodes are dominated by everything else
        graph.instructions.forEachIndexed { index, inst ->
            flowSets[index] = when {
                inst === head -> BitSet().also {
                    it.set(index)
                }

                else -> fullSet
            }
        }
        var changed: Boolean
        do {
            changed = false
            graph.instructions.forEachIndexed { index, inst ->
                if (inst !== head) {
                    val fullClone = fullSet.clone() as BitSet
                    val predecessors = when (inst) {
                        !is JcCatchInst -> graph.predecessors(inst)
                        else -> graph.throwers(inst)
                    }

                    predecessors.forEach { fullClone.and(it.dominatorsBitSet) }

                    val oldSet = inst.dominatorsBitSet
                    fullClone.set(index)
                    if (fullClone != oldSet) {
                        flowSets[index] = fullClone
                        changed = true
                    }
                }
            }
        } while (changed)
    }

    private val JcInst.indexOf: Int
        get() {
            val index = graph.instructions.indexOf(this)
            return index.takeIf { it >= 0 } ?: error("No with index ${this} in the graph!")
        }

    private val Int.instruction: JcInst
        get() {
            return graph.instructions[this]
        }

    private val JcInst.dominatorsBitSet: BitSet
        get() {
            return flowSets[indexOf] ?: error("Node $this is not in the graph!")
        }

    fun dominators(inst: JcInst): List<JcInst> {
        // reconstruct list of dominators from bitset
        val result = arrayListOf<JcInst>()
        val bitSet = inst.dominatorsBitSet
        var i = bitSet.nextSetBit(0)
        while (i >= 0) {
            result.add(i.instruction)
            if (i == Int.MAX_VALUE) {
                break // or (i+1) would overflow
            }
            i = bitSet.nextSetBit(i + 1)
        }
        return result
    }

    fun immediateDominator(inst: JcInst): JcInst? {
        // root node
        if (head === inst) {
            return null
        }
        val doms = inst.dominatorsBitSet.clone() as BitSet
        doms.clear(inst.indexOf)
        var i = doms.nextSetBit(0)
        while (i >= 0) {
            val dominator = i.instruction
            if (dominator.isDominatedByAll(doms)) {
                return dominator
            }
            if (i == Int.MAX_VALUE) {
                break // or (i+1) would overflow
            }
            i = doms.nextSetBit(i + 1)
        }
        return null
    }

    private fun JcInst.isDominatedByAll(dominators: BitSet): Boolean {
        val bitSet = dominatorsBitSet
        var i = dominators.nextSetBit(0)
        while (i >= 0) {
            if (!bitSet[i]) {
                return false
            }
            if (i == Int.MAX_VALUE) {
                break // or (i+1) would overflow
            }
            i = dominators.nextSetBit(i + 1)
        }
        return true
    }

    fun isDominatedBy(node: JcInst, dominator: JcInst): Boolean {
        return node.dominatorsBitSet[dominator.indexOf]
    }

    fun isDominatedByAll(node: JcInst, dominators: Collection<JcInst>): Boolean {
        val bitSet = node.dominatorsBitSet
        for (n in dominators) {
            if (!bitSet[n.indexOf]) {
                return false
            }
        }
        return true
    }
}

fun JcGraph.findDominators(): GraphDominators {
    return GraphDominators(this).also {
        it.find()
    }
}
