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

package org.jacodb.analysis.impl

import org.jacodb.api.analysis.ApplicationGraph
import java.util.*

class IFDSInstance<Method, Statement, D> (
    val graph: ApplicationGraph<Method, Statement>,
    private val flowSpace: FlowFunctionsSpace<Method, Statement, D>,
    private val devirtualizer: Devirtualizer<Method, Statement>? = null,
    private val listeners: MutableList<IFDSInstanceListener<Statement, D>> = mutableListOf()
) {
    private val pathEdges = mutableListOf<Edge<Statement, D>>()
    private val workList: Queue<Edge<Statement, D>> = LinkedList()
    private val callToStartEdges = mutableListOf<Edge<Statement, D>>()
    private val summaryEdges = mutableListOf<Edge<Statement, D>>()

    fun addListener(listener: IFDSInstanceListener<Statement, D>) = listeners.add(listener)

    fun addStart(startMethod: Method) {
        val entryPoints = graph.entryPoint(startMethod)

        for(entryPoint in entryPoints) {
            for (fact in flowSpace.obtainStartFacts(entryPoint)) {
                val startV = Vertex(entryPoint, fact)
                val startE = Edge(startV, startV)
                propagate(startE)
            }
        }
    }

    fun propagate(e: Edge<Statement, D>, pred: Statement? = null): Boolean {
        if (e !in pathEdges) {
            pathEdges.add(e)
            workList.add(e)
            listeners.forEach { it.onPropagate(e, pred) }
            return true
        }
        return false
    }

    fun run() {
        while(!workList.isEmpty()) {
            val (u, v) = workList.poll()
            val (sp, d1) = u
            val (n, d2) = v

            val callees = devirtualizer?.findPossibleCallees(n)?.toList() ?: graph.callees(n).toList()
            // 13
            if (callees.isNotEmpty()) {
                //14
                for (calledProc in callees) {
                    val flowFunction = flowSpace.obtainCallToStartFlowFunction(n, calledProc)
                    val nextFacts = flowFunction.compute(d2)
                    for (sCalledProc in graph.entryPoint(calledProc)) {
                        for (d3 in nextFacts) {
                            //15
                            val sCalledProcWithD3 = Vertex(sCalledProc, d3)
                            val nextEdge = Edge(sCalledProcWithD3, sCalledProcWithD3)
                            if (propagate(nextEdge, n)) {
                                callToStartEdges.add(Edge(v, sCalledProcWithD3))
                            }
                        }
                    }
                }
                //17-18
                val returnSitesOfN = graph.successors(n)
                for (returnSite in returnSitesOfN) {
                    val flowFunction = flowSpace.obtainCallToReturnFlowFunction(n, returnSite)
                    val nextFacts = flowFunction.compute(d2)
                    for (d3 in nextFacts) {
                        val returnSiteVertex = Vertex(returnSite, d3)
                        val nextEdge = Edge(u, returnSiteVertex)
                        propagate(nextEdge, n)
                    }
                }
                //17-18 for summary edges
                for (summaryEdge in summaryEdges) {
                    if (summaryEdge.u == v) {
                        val newPathEdge = Edge(u, summaryEdge.v)
                        propagate(newPathEdge, n)
                    }
                }
            } else {
                // 21-22
                val nMethod = graph.methodOf(n)
                val nMethodExitPoints = graph.exitPoints(nMethod).toList()
                if (n in nMethodExitPoints) {
                    listeners.forEach { it.onExitPoint(Edge(u, v)) }
                    @Suppress("UnnecessaryVariable") val ep = n
                    val callers = graph.callers(nMethod)
                    for (caller in callers) {
                        // todo think
                        val callToStartEdgeFlowFunction = flowSpace.obtainCallToStartFlowFunction(caller, nMethod)
                        val d4Set = callToStartEdgeFlowFunction.computeBackward(d1)
                        for (d4 in d4Set) {
                            val returnSitesOfCallers = graph.successors(caller)
                            for (returnSiteOfCaller in returnSitesOfCallers) {
                                val exitToReturnFlowFunction = flowSpace.obtainExitToReturnSiteFlowFunction(caller, returnSiteOfCaller, ep)
                                val d5Set = exitToReturnFlowFunction.compute(d2)
                                for (d5 in d5Set) {
                                    val newSummaryEdge = Edge(Vertex(caller, d4), Vertex(returnSiteOfCaller, d5))
                                    if (newSummaryEdge !in summaryEdges) {
                                        summaryEdges.add(newSummaryEdge)

                                        // Can't use iterator-based loops because of possible ConcurrentModificationException
                                        var ind = 0
                                        while (ind < pathEdges.size) {
                                            val pathEdge = pathEdges[ind]
                                            if (pathEdge.v == newSummaryEdge.u) {
                                                val newPathEdge = Edge(pathEdge.u, newSummaryEdge.v)
                                                propagate(newPathEdge, pathEdge.u.statement)
                                            }
                                            ind += 1
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    val nextInstrs = graph.successors(n)
                    for (m in nextInstrs) {
                        val flowFunction = flowSpace.obtainSequentFlowFunction(n, m)
                        val d3Set = flowFunction.compute(d2)
                        for (d3 in d3Set) {
                            val newEdge = Edge(u, Vertex(m, d3))
                            propagate(newEdge, n)
                        }
                    }
                }
            }
        }
    }

    fun collectResults(): IFDSResult<Method, Statement, D> {
        val resultFacts = mutableMapOf<Statement, MutableSet<D>>()

        // 6-8
        // todo: think about optimizations when we don't need all facts
        for (pathEdge in pathEdges) {
            //val method = pathEdge.u.statement.location.method
            resultFacts.getOrPut(pathEdge.v.statement) { mutableSetOf() }.add(pathEdge.v.domainFact)
        }
        return IFDSResult(graph, pathEdges, summaryEdges, resultFacts, callToStartEdges)
    }
}