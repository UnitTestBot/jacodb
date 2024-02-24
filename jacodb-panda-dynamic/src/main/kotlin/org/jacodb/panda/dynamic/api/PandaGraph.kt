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

import org.jacodb.api.common.analysis.ApplicationGraph
import org.jacodb.api.common.cfg.ControlFlowGraph

interface PandaBytecodeGraph<out Statement> : ControlFlowGraph<Statement> {
    fun throwers(node: @UnsafeVariance Statement): Set<Statement>
    fun catchers(node: @UnsafeVariance Statement): Set<Statement>
}

class PandaGraph(
    override val instructions: List<PandaInst>,
) : PandaBytecodeGraph<PandaInst> {

    private val predecessorMap: MutableMap<PandaInst, MutableSet<PandaInst>> = hashMapOf()
    private val successorMap: MutableMap<PandaInst, Set<PandaInst>> = hashMapOf()

    init {
        for (inst in instructions) {
            val successors = when (inst) {
                is PandaTerminatingInst -> emptySet()
                is PandaBranchingInst -> inst.successors.map { instructions[it.index] }.toSet()
                else -> setOf(next(inst))
            }
            successorMap[inst] = successors
            for (successor in successors) {
                predecessorMap.computeIfAbsent(successor) { mutableSetOf() }.add(inst)
            }
        }
    }

    override val entries: List<PandaInst>
        get() = instructions.take(1)

    override val exits: List<PandaInst>
        get() = instructions.filterIsInstance<PandaTerminatingInst>()

    fun index(inst: PandaInst): Int {
        if (inst in instructions) {
            return inst.location.index
        }
        return -1
    }

    fun ref(inst: PandaInst): PandaInstRef = PandaInstRef(index(inst))
    fun inst(ref: PandaInstRef): PandaInst = instructions[ref.index]
    fun next(inst: PandaInst): PandaInst = instructions[ref(inst).index + 1]
    fun previous(inst: PandaInst): PandaInst = instructions[ref(inst).index - 1]

    override fun successors(node: PandaInst): Set<PandaInst> = successorMap[node].orEmpty()
    override fun predecessors(node: PandaInst): Set<PandaInst> = predecessorMap[node].orEmpty()

    // TODO: throwers and catchers
    override fun throwers(node: PandaInst): Set<PandaInst> = emptySet()
    override fun catchers(node: PandaInst): Set<PandaInst> = emptySet()

    override fun iterator(): Iterator<PandaInst> = instructions.iterator()
}

data class PandaBasicBlock(
    val id: Int,
    val successors: Set<Int>,
    val predecessors: Set<Int>,
    val start: PandaInstRef,
    val end: PandaInstRef,
) {
    operator fun contains(inst: PandaInst): Boolean {
        return inst.location.index <= end.index && inst.location.index >= start.index
    }

    operator fun contains(inst: PandaInstRef): Boolean {
        return inst.index <= end.index && inst.index >= start.index
    }
}

class PandaBlockGraph(
    override val instructions: List<PandaBasicBlock>,
    instList: List<PandaInst>,
) : PandaBytecodeGraph<PandaBasicBlock> {

    val graph: PandaGraph = PandaGraph(instList)

    override val entries: List<PandaBasicBlock>
        get() = instructions.take(1)

    override val exits: List<PandaBasicBlock>
        get() = instructions.filter { it.successors.isEmpty() }

    override fun successors(node: PandaBasicBlock): Set<PandaBasicBlock> {
        return node.successors.mapTo(hashSetOf()) { instructions[it] }
    }

    override fun predecessors(node: PandaBasicBlock): Set<PandaBasicBlock> {
        return node.predecessors.mapTo(hashSetOf()) { instructions[it] }
    }

    // TODO: throwers and catchers
    override fun throwers(node: PandaBasicBlock): Set<PandaBasicBlock> = emptySet()
    override fun catchers(node: PandaBasicBlock): Set<PandaBasicBlock> = emptySet()

    override fun iterator(): Iterator<PandaBasicBlock> = instructions.iterator()
}

interface PandaApplicationGraph : ApplicationGraph<PandaMethod, PandaInst> {
    override val project: PandaProject
}

class PandaApplicationGraphImpl(
    override val project: PandaProject,
) : PandaApplicationGraph {

    override fun predecessors(node: PandaInst): Sequence<PandaInst> {
        val graph = node.location.method.flowGraph()
        val predecessors = graph.predecessors(node)
        val throwers = graph.throwers(node)
        return predecessors.asSequence() + throwers.asSequence()
    }

    override fun successors(node: PandaInst): Sequence<PandaInst> {
        val graph = node.location.method.flowGraph()
        val successors = graph.successors(node)
        val catchers = graph.catchers(node)
        return successors.asSequence() + catchers.asSequence()
    }

    override fun callees(node: PandaInst): Sequence<PandaMethod> {
        val callExpr = node.callExpr ?: return emptySequence()
        return sequenceOf(callExpr.callee)
    }

    override fun callers(method: PandaMethod): Sequence<PandaInst> {
        TODO("Not yet implemented")
    }

    override fun entryPoints(method: PandaMethod): Sequence<PandaInst> {
        return method.flowGraph().entries.asSequence()
    }

    override fun exitPoints(method: PandaMethod): Sequence<PandaInst> {
        return method.flowGraph().exits.asSequence()
    }

    override fun methodOf(node: PandaInst): PandaMethod {
        return node.location.method
    }
}
