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

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.yield
import org.jacodb.analysis.engine.PathEdgePredecessorKind.CALL_TO_START
import org.jacodb.analysis.engine.PathEdgePredecessorKind.NO_PREDECESSOR
import org.jacodb.analysis.engine.PathEdgePredecessorKind.SEQUENT
import org.jacodb.analysis.engine.PathEdgePredecessorKind.THROUGH_SUMMARY
import org.jacodb.api.JcMethod
import org.jacodb.api.analysis.ApplicationGraph
import org.jacodb.api.analysis.JcApplicationGraph
import org.jacodb.api.cfg.JcInst

class IfdsUnitInstanceFactory(private val analyzer: Analyzer) : IfdsInstanceFactory {
    override suspend fun <UnitType> createInstance(
        graph: JcApplicationGraph,
        summary: Summary,
        unitResolver: UnitResolver<UnitType>,
        unit: UnitType,
        startMethods: List<JcMethod>,
        startFacts: Map<JcMethod, Set<DomainFact>>
    ) {
        val instance = IfdsUnitInstance(graph, analyzer, summary, unitResolver, unit)

        startMethods.forEach {
            instance.addStart(it)
        }

        startFacts.forEach { (method, facts) ->
            facts.forEach { fact ->
                instance.addStartFact(method, fact)
            }
        }

        instance.analyze()
    }
}

class IfdsUnitInstance<UnitType>(
    private val graph: ApplicationGraph<JcMethod, JcInst>,
    private val analyzer: Analyzer,
    private val summary: Summary,
    private val unitResolver: UnitResolver<UnitType>,
    private val unit: UnitType
) {

    private class EdgesStorage {
        private val byStart: MutableMap<IfdsVertex, MutableSet<IfdsEdge>> = mutableMapOf()

        operator fun contains(e: IfdsEdge): Boolean {
            return e in getByStart(e.u)
        }

        fun add(e: IfdsEdge) {
            byStart
                .getOrPut(e.u) { mutableSetOf() }
                .add(e)
        }

        fun getByStart(start: IfdsVertex): Set<IfdsEdge> = byStart.getOrDefault(start, emptySet())

        fun getAll(): Set<IfdsEdge> {
            return byStart.flatMap { it.value.toList() }.toSet()
        }
    }

    private val pathEdges = EdgesStorage()
    private val startToEndEdges = EdgesStorage()
    private val summaryEdgeToStartToEndEdges: MutableMap<IfdsEdge, MutableSet<IfdsEdge>> = mutableMapOf()
    private val callSitesOf: MutableMap<IfdsVertex, MutableSet<IfdsEdge>> = mutableMapOf()
    private val pathEdgesPreds: MutableMap<IfdsEdge, MutableSet<PathEdgePredecessor>> = mutableMapOf()
    private val crossUnitCallees: MutableMap<IfdsVertex, MutableSet<IfdsVertex>> = mutableMapOf()
    private val summaryHandlers: MutableMap<JcMethod, SummarySender> = mutableMapOf()

    private val flowSpace get() = analyzer.flowFunctions

    private fun stashSummaryFact(method: JcMethod, fact: SummaryFact) {
        val handler = summaryHandlers.getOrPut(method) { summary.createSender(method) }
        handler.send(fact)
    }

    fun addStart(method: JcMethod) {
        require(unitResolver.resolve(method) == unit)
        for (sPoint in graph.entryPoint(method)) {
            for (sFact in flowSpace.obtainAllPossibleStartFacts(sPoint)) {
                val vertex = IfdsVertex(sPoint, sFact)
                val edge = IfdsEdge(vertex, vertex)
                propagate(edge, PathEdgePredecessor(edge, NO_PREDECESSOR))
            }
        }
    }

    private fun propagate(e: IfdsEdge, pred: PathEdgePredecessor): Boolean {
        pathEdgesPreds.getOrPut(e) { mutableSetOf() }.add(pred)

        if (e !in pathEdges) {
            pathEdges.add(e)
            require(workList.trySend(e).isSuccess)

            val summaryFacts = analyzer.findVulnerabilities(e)

            summaryFacts.forEach { stashSummaryFact(e.method, it) }

            if (e.v.statement in graph.exitPoints(e.method)) {
                stashSummaryFact(e.method, SummaryEdgeFact(e))
            }

            return true
        }
        return false
    }

    fun addStartFact(method: JcMethod, fact: DomainFact): Boolean {
        return graph.entryPoint(method).map {
            val vertex = IfdsVertex(it, fact)
            val edge = IfdsEdge(vertex, vertex)
            propagate(edge, PathEdgePredecessor(edge, NO_PREDECESSOR))
        }.any()
    }

    private val JcMethod.isExtern: Boolean
        get() = unitResolver.resolve(this) != unit

    private val workList = Channel<IfdsEdge>(Channel.UNLIMITED)

    private suspend fun takeNewEdge(): IfdsEdge? {
        return try {
            workList.receive()
        } catch (_: Exception) {
            null
        }
    }

    suspend fun run() = coroutineScope {
        while (isActive) {
            val curEdge = takeNewEdge() ?: break
            val (u, v) = curEdge
            val (n, d2) = v

            val callees = graph.callees(n).toList()
            if (callees.isNotEmpty()) {
                val returnSitesOfN = graph.successors(n)
                for (returnSite in returnSitesOfN) {
                    for (fact in flowSpace.obtainCallToReturnFlowFunction(n, returnSite).compute(d2)) {
                        val newEdge = IfdsEdge(u, IfdsVertex(returnSite, fact))
                        propagate(newEdge, PathEdgePredecessor(curEdge, SEQUENT))
                    }
                    for (callee in callees) {
                        val factsAtStart = flowSpace.obtainCallToStartFlowFunction(n, callee).compute(d2)
                        for (sPoint in graph.entryPoint(callee)) {
                            for (sFact in factsAtStart) {
                                val sVertex = IfdsVertex(sPoint, sFact)

                                val exitVertexes: Flow<IfdsVertex> = if (callee.isExtern) {
                                    summary.getFactsFiltered<SummaryEdgeFact>(callee, sVertex)
                                        .filter { it.edge.u == sVertex }
                                        .map { it.edge.v }
                                } else {
                                    startToEndEdges.getByStart(sVertex).map { it.v }.asFlow()
                                }

                                exitVertexes.onEach { (eStatement, eFact) ->
                                    val finalFacts =
                                        flowSpace.obtainExitToReturnSiteFlowFunction(n, returnSite, eStatement)
                                            .compute(eFact)
                                    for (finalFact in finalFacts) {
                                        val summaryEdge = IfdsEdge(v, IfdsVertex(returnSite, finalFact))
                                        val startToEndEdge =
                                            IfdsEdge(IfdsVertex(sPoint, sFact), IfdsVertex(eStatement, eFact))
                                        val newEdge = IfdsEdge(u, IfdsVertex(returnSite, finalFact))
                                        summaryEdgeToStartToEndEdges.getOrPut(summaryEdge) { mutableSetOf() }
                                            .add(startToEndEdge)
                                        propagate(newEdge, PathEdgePredecessor(curEdge, THROUGH_SUMMARY))
                                    }
                                }.launchIn(this)

                                if (callee.isExtern) {
                                    crossUnitCallees.getOrPut(v) { mutableSetOf() }.add(sVertex)
                                } else {
                                    callSitesOf.getOrPut(sVertex) { mutableSetOf() }.add(curEdge)
                                    val nextEdge = IfdsEdge(sVertex, sVertex)
                                    propagate(nextEdge, PathEdgePredecessor(curEdge, CALL_TO_START))
                                }
                            }
                        }
                    }
                }
            } else {
                if (n in graph.exitPoints(graph.methodOf(n))) {
                    for (predEdge in callSitesOf[u].orEmpty()) {
                        val callerStatement = predEdge.v.statement
                        for (returnSite in graph.successors(callerStatement)) {
                            for (returnSiteFact in flowSpace.obtainExitToReturnSiteFlowFunction(callerStatement, returnSite, n).compute(d2)) {
                                val returnSiteVertex = IfdsVertex(returnSite, returnSiteFact)
                                val newEdge = IfdsEdge(predEdge.u, returnSiteVertex)
                                summaryEdgeToStartToEndEdges.getOrPut(IfdsEdge(predEdge.v, returnSiteVertex)) { mutableSetOf() }.add(curEdge)
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
                        val newEdge = IfdsEdge(u, IfdsVertex(m, d3))
                        propagate(newEdge, PathEdgePredecessor(curEdge, SEQUENT))
                    }
                }
            }
            yield()
        }
    }

    val fullResults: IfdsResult by lazy {
        val resultFacts = mutableMapOf<JcInst, MutableSet<DomainFact>>()

        for (pathEdge in pathEdges.getAll()) {
            resultFacts.getOrPut(pathEdge.v.statement) { mutableSetOf() }.add(pathEdge.v.domainFact)
        }

        IfdsResult(
            pathEdges.getAll().toList(),
            resultFacts,
            pathEdgesPreds,
            summaryEdgeToStartToEndEdges,
            crossUnitCallees
        )
    }

    private fun postActions() {
        analyzer.findPostIfdsVulnerabilities(fullResults).forEach { vulnerability ->
            stashSummaryFact(vulnerability.sink.method, vulnerability)
        }

        val methods = fullResults.pathEdges.map { graph.methodOf(it.u.statement) }.distinct()

        crossUnitCallees.forEach { (callVertex, sVertexes) ->
            val method = graph.methodOf(callVertex.statement)
            stashSummaryFact(method, CalleeFact(callVertex, sVertexes))
        }

        methods.forEach { method ->
            val vulnerabilityLocations = summary
                .getCurrentFactsFiltered<VulnerabilityLocation>(method, null)
                .map { it.sink }
            val crossUnitCallesLocations = summary
                .getCurrentFactsFiltered<CalleeFact>(method, null)
                .map { it.vertex }

            (vulnerabilityLocations + crossUnitCallesLocations)
                .distinct()
                .forEach { vertex ->
                    val graph = fullResults.resolveTraceGraph(vertex)
                    stashSummaryFact(method, TraceGraphFact(graph))
                }
        }
    }

    suspend fun analyze() =
        try {
            run()
        } finally {
            postActions()
        }
}