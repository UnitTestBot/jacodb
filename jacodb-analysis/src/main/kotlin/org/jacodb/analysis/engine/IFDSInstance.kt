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

    private inner class EdgesStorage {
        private val byStart: MutableMap<IFDSVertex<DomainFact>, MutableSet<IFDSEdge<DomainFact>>> = mutableMapOf()
        private val byEnd:   MutableMap<IFDSVertex<DomainFact>, MutableSet<IFDSEdge<DomainFact>>> = mutableMapOf()
        operator fun contains(e: IFDSEdge<DomainFact>): Boolean {
            return e in getByStart(e.u)
        }

        fun add(e: IFDSEdge<DomainFact>) {
            byStart
                .getOrPut(e.u) { mutableSetOf() }
                .add(e)

            byEnd
                .getOrPut(e.v) { mutableSetOf() }
                .add(e)
        }

        fun getByStart(start: IFDSVertex<DomainFact>): Set<IFDSEdge<DomainFact>> = byStart.getOrDefault(start, emptySet())
        fun getByEnd(end: IFDSVertex<DomainFact>): Set<IFDSEdge<DomainFact>> = byEnd.getOrDefault(end, emptySet())

        fun getAll(): Set<IFDSEdge<DomainFact>> {
            return byStart.flatMap { it.value.toList() }.toSet()
        }
    }

    private val pathEdges = EdgesStorage()
    private val startToEndEdges = EdgesStorage()
    private val workList: Queue<IFDSEdge<DomainFact>> = LinkedList()
    private val callToStartEdges = mutableListOf<IFDSEdge<DomainFact>>()
    private val summaryEdges = EdgesStorage()
    private val callSitesOf: MutableMap<IFDSVertex<DomainFact>, MutableSet<IFDSVertex<DomainFact>>> = mutableMapOf()

    private val flowSpace get() = analyzer.flowFunctions

    fun addListener(listener: IFDSInstanceListener) = listeners.add(listener)

    override fun addStart(method: JcMethod) {
        val entryPoints = graph.entryPoint(method)

        for (entryPoint in entryPoints) {
            for (fact in flowSpace.obtainStartFacts(entryPoint)) {
                val startV = IFDSVertex(entryPoint, fact)
                val startU = IFDSVertex(entryPoint, ZEROFact)
                val startE = IFDSEdge(startU, startV)
                propagate(startE)
            }
        }
    }

    fun propagate(e: IFDSEdge<DomainFact>, pred: JcInst? = null): Boolean {
        if (e !in pathEdges) {
            pathEdges.add(e)
            workList.add(e)
            val isNew = pred != null && !pathEdges.contains(IFDSEdge(e.u, IFDSVertex(pred, e.v.domainFact)))
            listeners.forEach { it.onPropagate(e, pred, isNew) }
            return true
        }
        return false
    }

    // Build summary edges of form (caller, d4) -> (*, *)
    private fun findNewSummaryEdges(callSite: JcInst, d4: DomainFact, startToEndEdge: IFDSEdge<DomainFact>) {
        val (ep, d2) = startToEndEdge.v

        val returnSitesOfCaller = graph.successors(callSite)
        for (returnSiteOfCaller in returnSitesOfCaller) {
            val exitToReturnFlowFunction = flowSpace.obtainExitToReturnSiteFlowFunction(callSite, returnSiteOfCaller, ep)
            val d5Set = exitToReturnFlowFunction.compute(d2)
            for (d5 in d5Set) {
                val newSummaryEdge = IFDSEdge(IFDSVertex(callSite, d4), IFDSVertex(returnSiteOfCaller, d5))
                if (newSummaryEdge !in summaryEdges) {
                    summaryEdges.add(newSummaryEdge)

                    for (pathEdge in pathEdges.getByEnd(newSummaryEdge.u).toList()) {
                        val newPathEdge = IFDSEdge(pathEdge.u, newSummaryEdge.v)
                        propagate(newPathEdge, callSite)
                    }
                }
            }
        }
    }

    fun run() {
        while(!workList.isEmpty()) {
            val (u, v) = workList.poll()
            val (n, d2) = v

            val callees = devirtualizer.findPossibleCallees(n).toList()
            // 13
            if (callees.isNotEmpty()) {
                //14
                for (calledProc in callees) {
                    val flowFunction = flowSpace.obtainCallToStartFlowFunction(n, calledProc)
                    val nextFacts = flowFunction.compute(d2)
                    for (sCalledProc in graph.entryPoint(calledProc)) {
                        for (d3 in nextFacts) {
                            //15
                            val sCalledProcWithD3 = IFDSVertex(sCalledProc, d3)
                            val nextEdge = IFDSEdge(sCalledProcWithD3, sCalledProcWithD3)
                            if (propagate(nextEdge)) {
                                callToStartEdges.add(IFDSEdge(v, sCalledProcWithD3))
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
                        val returnSiteVertex = IFDSVertex(returnSite, d3)
                        val nextEdge = IFDSEdge(u, returnSiteVertex)
                        propagate(nextEdge, n)
                    }
                }
                for (callee in callees) {
                    for (d1 in flowSpace.obtainCallToStartFlowFunction(n, callee).compute(d2)) {
                        for (entryPoint in graph.entryPoint(callee)) {
                            val startVertex = IFDSVertex(entryPoint, d1)
                            if (v !in callSitesOf[startVertex].orEmpty()) {
                                callSitesOf.getOrPut(startVertex) { mutableSetOf() }.add(v)
                                for (startToEndEdge in startToEndEdges.getByStart(IFDSVertex(entryPoint, d1)).toList()) {
                                    findNewSummaryEdges(n, d2, startToEndEdge)
                                }
                            }
                        }
                    }
                }
                //17-18 for summary edges
                for (summaryEdge in summaryEdges.getByStart(v).toList()) {
                    val newPathEdge = IFDSEdge(u, summaryEdge.v)
                    propagate(newPathEdge, n)
                }
            } else {
                // 21-22
                val nMethod = graph.methodOf(n)
                val nMethodExitPoints = graph.exitPoints(nMethod).toList()
                if (n in nMethodExitPoints) {
                    listeners.forEach { it.onExitPoint(IFDSEdge(u, v)) }
                    for ((c, d4) in callSitesOf[u].orEmpty()) {
                        findNewSummaryEdges(c, d4, IFDSEdge(u, v))
                    }
                    startToEndEdges.add(IFDSEdge(u, v))
                } else {
                    val nextInstrs = graph.successors(n)
                    for (m in nextInstrs) {
                        val flowFunction = flowSpace.obtainSequentFlowFunction(n, m)
                        val d3Set = flowFunction.compute(d2)
                        for (d3 in d3Set) {
                            val newEdge = IFDSEdge(u, IFDSVertex(m, d3))
                            propagate(newEdge, n)
                        }
                    }
                }
            }
        }
    }

    private fun collectResults(): IFDSResult {
        val resultFacts = mutableMapOf<JcInst, MutableSet<DomainFact>>()

        // 6-8
        // todo: think about optimizations when we don't need all facts
        for (pathEdge in pathEdges.getAll()) {
            resultFacts.getOrPut(pathEdge.v.statement) { mutableSetOf() }.add(pathEdge.v.domainFact)
        }
        return IFDSResult(graph, pathEdges.getAll().toList(), summaryEdges.getAll().toList(), resultFacts, callToStartEdges)
    }

    override fun analyze(): DumpableAnalysisResult {
        run()

        val ifdsResult = collectResults()
        val paths = analyzer.calculateSources(ifdsResult)

        return paths
    }
}