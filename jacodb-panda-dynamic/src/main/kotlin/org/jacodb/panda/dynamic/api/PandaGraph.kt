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

package org.jacodb.panda.dynamic.api

import org.jacodb.api.jvm.cfg.JcBytecodeGraph
import java.util.*

data class PandaBasicBlock(
    val id: Int,
    val successors: Set<Int>,
    val predecessors: Set<Int>,
    val start: PandaInstRef,
    val end: PandaInstRef
) {

    fun contains(inst: PandaInst): Boolean {
        return inst.location.index <= end.index && inst.location.index >= start.index
    }

    fun contains(inst: PandaInstRef): Boolean {
        return inst.index <= end.index && inst.index >= start.index
    }
}

class PandaGraph(
    override val instructions: List<PandaInst>,
    val blockGraph: PandaBlockGraph
) : JcBytecodeGraph<PandaInst> {

    private val predecessorMap = hashMapOf<PandaInst, Set<PandaInst>>()
    private val successorMap = hashMapOf<PandaInst, Set<PandaInst>>()

    init {
        for (inst in instructions) {
            val successors = when (inst) {
                is PandaTerminatingInst -> emptySet()
                is PandaBranchingInst -> inst.successors.map { instructions[it.index] }.toSet()
                else -> setOf(next(inst))
            }
            successorMap[inst] = successors

            for (successor in successors) {
                predecessorMap.add(successor, inst)
            }
        }

    }

    fun next(inst: PandaInst): PandaInst = instructions[ref(inst).index + 1]

    fun index(inst: PandaInst): Int {
        if (instructions.contains(inst)) {
            return inst.location.index
        }
        return -1
    }

    fun ref(inst: PandaInst): PandaInstRef = PandaInstRef(index(inst))

    val entry: PandaInst get() = instructions.first()

    override fun successors(node: PandaInst): Set<PandaInst> = successorMap.getOrDefault(node, emptySet())

    override fun predecessors(node: PandaInst): Set<PandaInst> = predecessorMap.getOrDefault(node, emptySet())

    //TODO: throwers and catchers
    override fun throwers(node: PandaInst): Set<PandaInst> = emptySet()

    override fun catchers(node: PandaInst): Set<PandaInst> = emptySet()

    override val entries: List<PandaInst>
        get() = if (instructions.isEmpty()) listOf() else listOf(entry)
    override val exits: List<PandaInst>
        get() = instructions.filterIsInstance<PandaTerminatingInst>()

    override fun iterator(): Iterator<PandaInst> = instructions.iterator()

    private fun <KEY, VALUE> MutableMap<KEY, Set<VALUE>>.add(key: KEY, value: VALUE) {
        val current = this[key]
        if (current == null) {
            this[key] = Collections.singleton(value)
        } else {
            this[key] = current + value
        }
    }

}

class PandaBlockGraph(
    override val instructions: List<PandaBasicBlock>,
    private val instList: List<PandaInst>
) : JcBytecodeGraph<PandaBasicBlock> {

    private val _graph = PandaGraph(instList, this)

    val graph: PandaGraph get() = _graph

    override fun successors(node: PandaBasicBlock): Set<PandaBasicBlock> {
        return node.successors.map { instructions[it] }.toSet()
    }

    override fun predecessors(node: PandaBasicBlock): Set<PandaBasicBlock> {
        return node.predecessors.map { instructions[it] }.toSet()
    }

    //TODO: throwers and catchers
    override fun throwers(node: PandaBasicBlock): Set<PandaBasicBlock> = emptySet()

    override fun catchers(node: PandaBasicBlock): Set<PandaBasicBlock> = emptySet()

    override val entries: List<PandaBasicBlock>
        get() = listOf(instructions.first())
    override val exits: List<PandaBasicBlock>
        get() = instructions.filter { it.successors.isEmpty() }


    override fun iterator(): Iterator<PandaBasicBlock> {
        return instructions.iterator()
    }

}
