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

package org.jacodb.panda.staticvm.cfg

import org.jacodb.api.common.analysis.ApplicationGraph
import org.jacodb.api.common.cfg.BytecodeGraph
import org.jacodb.api.common.cfg.Graph
import org.jacodb.panda.staticvm.*
import org.jacodb.panda.staticvm.classpath.PandaMethod
import org.jacodb.panda.staticvm.classpath.PandaProject
import org.jacodb.panda.staticvm.ir.PandaBasicBlockIr
import org.jacodb.panda.staticvm.utils.OneDirectionGraph

interface PandaBytecodeGraph<out Statement> : BytecodeGraph<Statement>

/**
 * Control flow graph for a single [PandaMethod].
 */
class PandaGraph private constructor(
    private val instList: PandaInstList,
    private val graph: OneDirectionGraph<PandaInst>,
    private val throwGraph: OneDirectionGraph<PandaInst>,
) : PandaBytecodeGraph<PandaInst>, Graph<PandaInst> {

    override val entries: List<PandaInst> = listOfNotNull(instList.firstOrNull())

    override val exits: List<PandaInst> = setOfNotNull(instList.lastOrNull().takeIf { it !is PandaGotoInst })
        .plus(instList.filterIsInstance<PandaTerminatingInst>())
        .toList()

    override val instructions: List<PandaInst> = instList.instructions

    fun index(inst: PandaInst): Int {
        if (inst in instructions) {
            return inst.location.index
        }
        return -1
    }

    fun ref(inst: PandaInst): PandaInstRef = PandaInstRef(index(inst))
    fun inst(ref: PandaInstRef): PandaInst = instructions[ref.index]

    fun previous(inst: PandaInst): PandaInst = instructions[ref(inst).index - 1]
    fun next(inst: PandaInst): PandaInst = instructions[ref(inst).index + 1]

    override fun successors(node: PandaInst): Set<PandaInst> = graph.successors(node)
    override fun predecessors(node: PandaInst): Set<PandaInst> = graph.predecessors(node)

    override fun throwers(node: PandaInst): Set<PandaInst> = throwGraph.predecessors(node)
    override fun catchers(node: PandaInst): Set<PandaInst> = throwGraph.successors(node)

    companion object {
        fun empty(): PandaGraph {
            return PandaGraph(
                PandaInstList(emptyList()),
                OneDirectionGraph(emptySet()) { emptySet() },
                OneDirectionGraph(emptySet()) { emptySet() }
            )
        }

        fun of(method: PandaMethod, blocks: List<PandaBasicBlockIr>): PandaGraph {
            val instListBuilder = InstListBuilder(method, blocks)
            val instList = instListBuilder.instList
            val graph = OneDirectionGraph(instList) { inst ->
                when (inst) {
                    is PandaBranchingInst -> inst.successors.map { instList[it.index] }
                    is PandaTerminatingInst -> emptyList()
                    else -> listOfNotNull(instList.getOrNull(instList.indexOf(inst) + 1))
                }
            }
            val throwGraph = OneDirectionGraph(instList) { thrower ->
                instListBuilder.throwEdges
                    .filter { (from, _) -> instList[from.index] == thrower }
                    .map { instList[it.second.index] }
            }
            // instListBuilder.throwEdges.applyFold(graph) { (from, to) -> withEdge(instList[from.index], instList[to.index]) }
            return PandaGraph(PandaInstList(instList), graph, throwGraph)
        }
    }
}

class PandaApplicationGraph(
    override val project: PandaProject,
) : ApplicationGraph<PandaMethod, PandaInst> {
    private val callersMap = project.methods
        .flatMap { it.flowGraph().instructions }
        .flatMap { inst -> callees(inst).map { it to inst } }
        .groupBy(Pair<PandaMethod, PandaInst>::first, Pair<PandaMethod, PandaInst>::second)

    override fun predecessors(node: PandaInst): Sequence<PandaInst> {
        val graph = node.location.method.flowGraph()
        val predecessors = graph.predecessors(node)
        if (node is PandaCatchInst) {
            val throwers = graph.throwers(node)
            return predecessors.asSequence() + throwers.asSequence()
        } else {
            return predecessors.asSequence()
        }
    }

    override fun successors(node: PandaInst): Sequence<PandaInst> {
        val graph = node.location.method.flowGraph()
        val successors = graph.successors(node)
        if (node is PandaThrowInst) {
            val catchers = graph.catchers(node)
            return successors.asSequence() + catchers.asSequence()
        } else {
            return successors.asSequence()
        }
    }

    override fun callees(node: PandaInst): Sequence<PandaMethod> = when (node) {
        is PandaAssignInst -> when (val expr = node.rhv) {
            is PandaStaticCallExpr -> sequenceOf(expr.method)
            is PandaVirtualCallExpr -> sequenceOf(expr.method)
            else -> emptySequence()
        }

        else -> emptySequence()
    }

    override fun callers(method: PandaMethod): Sequence<PandaInst> {
        return callersMap[method]?.asSequence() ?: emptySequence()
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
