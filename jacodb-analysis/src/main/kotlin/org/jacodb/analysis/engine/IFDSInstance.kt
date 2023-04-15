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

package org.jacodb.analysis.engine

import org.jacodb.analysis.AnalysisEngine
import org.jacodb.analysis.DumpableAnalysisResult
import org.jacodb.analysis.points2.Devirtualizer
import org.jacodb.api.JcMethod
import org.jacodb.api.analysis.ApplicationGraph
import org.jacodb.api.cfg.JcInst
import java.util.*

class IFDSInstance(
    private val graph: ApplicationGraph<JcMethod, JcInst>,
    private val analyzer: Analyzer,
    private val devirtualizer: Devirtualizer,
    private val listeners: MutableList<IFDSInstanceListener> = mutableListOf()
): AnalysisEngine {
    private val pathEdges = mutableListOf<IfdsEdge<DomainFact>>()
    private val workList: Queue<IfdsEdge<DomainFact>> = LinkedList()
    private val callToStartEdges = mutableListOf<IfdsEdge<DomainFact>>()
    private val summaryEdges = mutableListOf<IfdsEdge<DomainFact>>()
    private val callSites = mutableListOf<IfdsVertex<DomainFact>>()

    private val flowSpace get() = analyzer.flowFunctions

    fun addListener(listener: IFDSInstanceListener) = listeners.add(listener)

    override fun addStart(method: JcMethod) {
        val entryPoints = graph.entryPoint(method)

        for(entryPoint in entryPoints) {
            for (fact in flowSpace.obtainStartFacts(entryPoint)) {
                val startV = IfdsVertex(entryPoint, fact)
                val startE = IfdsEdge(startV, startV)
                propagate(startE)
            }
        }
    }

    fun propagate(e: IfdsEdge<DomainFact>, pred: JcInst? = null): Boolean {
        if (e !in pathEdges) {
            pathEdges.add(e)
            workList.add(e)
            val isNew = pred != null && !pathEdges.contains(IfdsEdge(e.u, IfdsVertex(pred, e.v.domainFact)))
            listeners.forEach { it.onPropagate(e, pred, isNew) }
            return true
        }
        return false
    }

    // Build summary edges of form (caller, d4) -> (*, *)
    private fun findNewSummaryEdges(callSite: JcInst, d4: DomainFact, startToEndEdge: IfdsEdge<DomainFact>) {
        val (sp, d1) = startToEndEdge.u
        val (ep, d2) = startToEndEdge.v
        val nMethod = graph.methodOf(ep)

        if (ep !in graph.exitPoints(nMethod)) // Not a start-to-end edge
            return

        if (nMethod !in graph.callees(callSite) || d1 !in flowSpace.obtainCallToStartFlowFunction(callSite, nMethod).compute(d4))
        // (sp, d1) is not reachable from (caller, d4)
            return

        // todo think
        val returnSitesOfCallers = graph.successors(callSite)
        for (returnSiteOfCaller in returnSitesOfCallers) {
            val exitToReturnFlowFunction = flowSpace.obtainExitToReturnSiteFlowFunction(callSite, returnSiteOfCaller, ep)
            val d5Set = exitToReturnFlowFunction.compute(d2)
            for (d5 in d5Set) {
                val newSummaryEdge = IfdsEdge(IfdsVertex(callSite, d4), IfdsVertex(returnSiteOfCaller, d5))
                if (newSummaryEdge !in summaryEdges) {
                    summaryEdges.add(newSummaryEdge)

                    // Can't use iterator-based loops because of possible ConcurrentModificationException
                    var ind = 0
                    while (ind < pathEdges.size) {
                        val pathEdge = pathEdges[ind]
                        if (pathEdge.v == newSummaryEdge.u) {
                            val newPathEdge = IfdsEdge(pathEdge.u, newSummaryEdge.v)
                            propagate(newPathEdge, callSite)
                        }
                        ind += 1
                    }
                }
            }
        }
    }

    fun run() {
        while(!workList.isEmpty()) {
            val (u, v) = workList.poll()
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
                            val sCalledProcWithD3 = IfdsVertex(sCalledProc, d3)
                            val nextEdge = IfdsEdge(sCalledProcWithD3, sCalledProcWithD3)
                            if (propagate(nextEdge)) {
                                callToStartEdges.add(IfdsEdge(v, sCalledProcWithD3))
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
                        val returnSiteVertex = IfdsVertex(returnSite, d3)
                        val nextEdge = IfdsEdge(u, returnSiteVertex)
                        propagate(nextEdge, n)
                    }
                }
                if (v !in callSites) {
                    callSites.add(v)
                    // lazy computation of summaryEdges for lines 17-18
                    // TODO: optimize here by looking only at startToEndEges
                    for (startToEndEdge in pathEdges.toList()) {
                        findNewSummaryEdges(n, d2, startToEndEdge)
                    }
                }
                //17-18 for summary edges
                for (summaryEdge in summaryEdges) {
                    if (summaryEdge.u == v) {
                        val newPathEdge = IfdsEdge(u, summaryEdge.v)
                        propagate(newPathEdge, n)
                    }
                }
            } else {
                // 21-22
                val nMethod = graph.methodOf(n)
                val nMethodExitPoints = graph.exitPoints(nMethod).toList()
                if (n in nMethodExitPoints) {
                    listeners.forEach { it.onExitPoint(IfdsEdge(u, v)) }
                    for ((c, d4) in callSites) {
                        findNewSummaryEdges(c, d4, IfdsEdge(u, v))
                    }
                } else {
                    val nextInstrs = graph.successors(n)
                    for (m in nextInstrs) {
                        val flowFunction = flowSpace.obtainSequentFlowFunction(n, m)
                        val d3Set = flowFunction.compute(d2)
                        for (d3 in d3Set) {
                            val newEdge = IfdsEdge(u, IfdsVertex(m, d3))
                            propagate(newEdge, n)
                        }
                    }
                }
            }
        }
    }

    fun collectResults(): IFDSResult {
        val resultFacts = mutableMapOf<JcInst, MutableSet<DomainFact>>()

        // 6-8
        // todo: think about optimizations when we don't need all facts
        for (pathEdge in pathEdges) {
            resultFacts.getOrPut(pathEdge.v.statement) { mutableSetOf() }.add(pathEdge.v.domainFact)
        }
        return IFDSResult(graph, pathEdges, summaryEdges, resultFacts, callToStartEdges)
    }

    override fun analyze(): DumpableAnalysisResult {
        run()

        val ifdsResult = collectResults()
        val paths = analyzer.calculateSources(ifdsResult)

        return paths
    }
}