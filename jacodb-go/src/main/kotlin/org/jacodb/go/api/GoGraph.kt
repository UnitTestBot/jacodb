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

package org.jacodb.go.api

import org.jacodb.api.common.cfg.BytecodeGraph

class GoGraph(
    override val instructions: List<GoInst>,
    private val firstInstruction: List<GoInst>
) : BytecodeGraph<GoInst> {
    private val predecessorsList: MutableList<MutableSet<GoInst>> = MutableList(instructions.size) { mutableSetOf() }
    private val successorsList: MutableList<Set<GoInst>> = MutableList(instructions.size) { setOf() }

    init {
        for (inst in instructions) {
            val successors = when (inst) {
                is GoTerminatingInst -> emptySet()
                is GoBranchingInst -> inst.successors.map { firstInstruction[it.index] }.toSet()
                else -> setOf(next(inst))
            }
            successorsList[inst.location.lineNumber] = successors
            for (successor in successors) {
                predecessorsList[successor.location.lineNumber].add(inst)
            }
        }
    }

    override val entries: List<GoInst>
        get() = instructions.take(1)

    override val exits: List<GoInst>
        get() = instructions.filterIsInstance<GoTerminatingInst>()

    fun next(inst: GoInst): GoInst = instructions[inst.location.lineNumber + 1]

    override fun successors(node: GoInst): Set<GoInst> = successorsList[node.location.lineNumber]
    override fun predecessors(node: GoInst): Set<GoInst> = predecessorsList[node.location.lineNumber]

    override fun throwers(node: GoInst): Set<GoInst> {
        return exits.filterIsInstance<GoPanicInst>().toSet()
    }

    // TODO: catchers? Is there any?
    override fun catchers(node: GoInst): Set<GoInst> = emptySet()

    override fun iterator(): Iterator<GoInst> = instructions.iterator()
}

data class GoBasicBlock(
    val id: Int,
    val successors: List<Int>,
    val predecessors: List<Int>,
    var instructions: List<GoInst> = emptyList(),
)

class GoBlockGraph(
    override val instructions: List<GoBasicBlock>,
    instList: List<GoInst>,
    firstInstruction: List<GoInst>,
) : BytecodeGraph<GoBasicBlock> {
    val graph: GoGraph = GoGraph(instList, firstInstruction)

    override val entries: List<GoBasicBlock>
        get() = instructions.take(1)

    override val exits: List<GoBasicBlock>
        get() = instructions.filter { it.successors.isEmpty() }

    override fun successors(node: GoBasicBlock): Set<GoBasicBlock> {
        return node.successors.mapTo(hashSetOf()) { instructions[it] }
    }

    override fun predecessors(node: GoBasicBlock): Set<GoBasicBlock> {
        return node.predecessors.mapTo(hashSetOf()) { instructions[it] }
    }

    override fun throwers(node: GoBasicBlock): Set<GoBasicBlock> {
        return exits.filter { it.instructions.last() is GoPanicInst }.toSet()
    }

    // TODO: catchers? Is there any?
    override fun catchers(node: GoBasicBlock): Set<GoBasicBlock> = emptySet()

    override fun iterator(): Iterator<GoBasicBlock> = instructions.iterator()
}
