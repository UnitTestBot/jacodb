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

import org.jacodb.analysis.AnalysisResult
import org.jacodb.analysis.engine.PathEdgePredecessorKind.NO_PREDECESSOR
import org.jacodb.analysis.engine.PathEdgePredecessorKind.SEQUENT
import org.jacodb.analysis.engine.PathEdgePredecessorKind.THROUGH_SUMMARY
import org.jacodb.analysis.points2.Devirtualizer
import org.jacodb.api.JcMethod
import org.jacodb.api.analysis.ApplicationGraph
import org.jacodb.api.cfg.JcInst
import java.util.*

class IFDSUnitInstance<UnitType>(
    private val graph: ApplicationGraph<JcMethod, JcInst>,
    private val analyzer: Analyzer,
    private val devirtualizer: Devirtualizer,
    private val context: AnalysisContext,
    private val unitResolver: UnitResolver<UnitType>,
    methods: List<JcMethod>
) {
    private val unit: UnitType

    private class EdgesStorage {
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

        fun getAll(): Set<IFDSEdge<DomainFact>> {
            return byStart.flatMap { it.value.toList() }.toSet()
        }
    }

    private val pathEdges = EdgesStorage()
    private val startToEndEdges = EdgesStorage()
    private val workList: Queue<IFDSEdge<DomainFact>> = LinkedList()
    private val summaryEdgeToStartToEndEdges: MutableMap<IFDSEdge<DomainFact>, MutableSet<IFDSEdge<DomainFact>>> = mutableMapOf()
    private val callSitesOf: MutableMap<IFDSVertex<DomainFact>, MutableSet<IFDSEdge<DomainFact>>> = mutableMapOf()
    private val pathEdgesPreds: MutableMap<IFDSEdge<DomainFact>, MutableSet<PathEdgePredecessor<DomainFact>>> = mutableMapOf()
    private val externCallees: MutableMap<IFDSVertex<DomainFact>, MutableSet<IFDSVertex<DomainFact>>> = mutableMapOf()

    private val flowSpace get() = analyzer.flowFunctions

    private val listeners: MutableList<IFDSInstanceListener> = mutableListOf()

    fun addListener(listener: IFDSInstanceListener) = listeners.add(listener)

    init {
        unit = unitResolver.resolve(methods.first())
        for (method in methods) {
            addStart(method)
        }
    }

    fun addStart(method: JcMethod) {
        for (sPoint in graph.entryPoint(method)) {
            for (sFact in flowSpace.obtainAllPossibleStartFacts(sPoint)) {
                val vertex = IFDSVertex(sPoint, sFact)
                val edge = IFDSEdge(vertex, vertex)
                propagate(edge, PathEdgePredecessor(edge, NO_PREDECESSOR))
            }
        }
    }

    private fun propagate(e: IFDSEdge<DomainFact>, pred: PathEdgePredecessor<DomainFact>): Boolean {
        pathEdgesPreds.getOrPut(e) { mutableSetOf() }.add(pred)
        if (e !in pathEdges) {
            pathEdges.add(e)
            workList.add(e)
            val isNew =
                pred.kind != SEQUENT && pred.kind != PathEdgePredecessorKind.UNKNOWN || pred.predEdge.v.domainFact != e.v.domainFact
            val predInst =
                pred.predEdge.v.statement.takeIf { it != e.v.statement && it.location.method == e.v.statement.location.method }
            listeners.forEach { it.onPropagate(e, predInst, isNew) }
            return true
        }
        return false
    }

    fun addNewPathEdge(e: IFDSEdge<DomainFact>): Boolean {
        return propagate(e, PathEdgePredecessor(e, PathEdgePredecessorKind.UNKNOWN))
    }

    private val JcMethod.isExtern: Boolean
        get() = unitResolver.resolve(this) != unit

    fun run() {
        while (!workList.isEmpty()) {
            val curEdge = workList.poll()
            val (u, v) = curEdge
            val (n, d2) = v

            val callees = devirtualizer.findPossibleCallees(n).toList()
            if (callees.isNotEmpty()) {
                val returnSitesOfN = graph.successors(n)
                for (returnSite in returnSitesOfN) {
                    for (fact in flowSpace.obtainCallToReturnFlowFunction(n, returnSite).compute(d2)) {
                        val newEdge = IFDSEdge(u, IFDSVertex(returnSite, fact))
                        propagate(newEdge, PathEdgePredecessor(curEdge, SEQUENT))
                    }
                    for (callee in callees) {
                        val factsAtStart = flowSpace.obtainCallToStartFlowFunction(n, callee).compute(d2)
                        for (sPoint in graph.entryPoint(callee)) {
                            for (sFact in factsAtStart) {

                                val sVertex = IFDSVertex(sPoint, sFact)
                                val exitVertexes = if (callee.isExtern) {
                                    context.summaries[callee]?.factsAtExits?.get(sVertex).orEmpty()
                                } else {
                                    startToEndEdges.getByStart(sVertex).map { it.v }
                                }

                                for ((exitStatement, eFact) in exitVertexes) {
                                    val finalFacts = flowSpace.obtainExitToReturnSiteFlowFunction(n, returnSite, exitStatement).compute(eFact)
                                    for (finalFact in finalFacts) {
                                        val summaryEdge = IFDSEdge(v, IFDSVertex(returnSite, finalFact))
                                        val startToEndEdge = IFDSEdge(IFDSVertex(sPoint, sFact), IFDSVertex(exitStatement, eFact))
                                        val newEdge = IFDSEdge(u, IFDSVertex(returnSite, finalFact))
                                        summaryEdgeToStartToEndEdges.getOrPut(summaryEdge) { mutableSetOf() }.add(startToEndEdge)
                                        propagate(newEdge, PathEdgePredecessor(curEdge, THROUGH_SUMMARY))
                                    }
                                }

                                if (callee.isExtern) {
                                    externCallees.getOrPut(v) { mutableSetOf() }.add(sVertex)
                                } else {
                                    callSitesOf.getOrPut(sVertex) { mutableSetOf() }.add(curEdge)
                                    val nextEdge = IFDSEdge(sVertex, sVertex)
                                    propagate(nextEdge, PathEdgePredecessor(curEdge, PathEdgePredecessorKind.CALL_TO_START))
                                }
                            }
                        }
                    }
                }
            } else {
                if (n in graph.exitPoints(graph.methodOf(n))) {
                    listeners.forEach { it.onExitPoint(curEdge) }
                    for (predEdge in callSitesOf[u].orEmpty()) {
                        val callerStatement = predEdge.v.statement
                        for (returnSite in graph.successors(callerStatement)) {
                            for (returnSiteFact in flowSpace.obtainExitToReturnSiteFlowFunction(callerStatement, returnSite, n).compute(d2)) {
                                val returnSiteVertex = IFDSVertex(returnSite, returnSiteFact)
                                val newEdge = IFDSEdge(predEdge.u, returnSiteVertex)
                                propagate(newEdge, PathEdgePredecessor(predEdge, THROUGH_SUMMARY))
                            }
                        }
                    }
                    startToEndEdges.add(curEdge)
                }

                val nextInstrs = graph.successors(n)
                for (m in nextInstrs) {
                    val flowFunction = flowSpace.obtainSequentFlowFunction(n, m)
                    val d3Set = flowFunction.compute(d2)
                    for (d3 in d3Set) {
                        val newEdge = IFDSEdge(u, IFDSVertex(m, d3))
                        propagate(newEdge, PathEdgePredecessor(curEdge, SEQUENT))
                    }
                }
            }
        }
    }

    private val fullResults: IFDSResult by lazy {
        val resultFacts = mutableMapOf<JcInst, MutableSet<DomainFact>>()

        for (pathEdge in pathEdges.getAll()) {
            resultFacts.getOrPut(pathEdge.v.statement) { mutableSetOf() }.add(pathEdge.v.domainFact)
        }

        IFDSResult(
            graph,
            pathEdges.getAll().toList(),
            resultFacts,
            pathEdgesPreds,
            summaryEdgeToStartToEndEdges
        )
    }

    private fun getMethodSummary(method: JcMethod): IFDSMethodSummary {
        val factsAtExits = mutableMapOf<IFDSVertex<DomainFact>, MutableSet<IFDSVertex<DomainFact>>>()

        for (pathEdge in pathEdges.getAll()) {
            if (pathEdge.v.statement in graph.exitPoints(method)) {
                factsAtExits.getOrPut(pathEdge.u) { mutableSetOf() }.add(pathEdge.v)
            }
        }

        // TODO: invoke calculateSources only once by analyze() call
        val relevantVulnerabilities = analyzer.calculateSources(fullResults).vulnerabilities.filter {
            graph.methodOf(it.realisationsGraph.sink.statement) == method
        }

        // TODO: Delete externVulnerabilities here
//        val externVulnerabilities = externCallees.flatMap { graph.callees(it.key.statement).toList() }.flatMap {
//            context.summaries[it]?.foundVulnerabilities?.vulnerabilities.orEmpty()
//        }.distinct()
        val actualResult = AnalysisResult(relevantVulnerabilities)
        val relevantExternCallees = externCallees.filterKeys { graph.methodOf(it.statement) == method }
        return IFDSMethodSummary(factsAtExits, relevantExternCallees, actualResult)
    }

    fun analyze(): Map<JcMethod, IFDSMethodSummary> {
        run()

        val methods = fullResults.pathEdges.map { graph.methodOf(it.u.statement) }.distinct()
        return methods.associateWith { getMethodSummary(it) }
    }
}