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

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.jacodb.api.JcMethod
import org.jacodb.api.analysis.ApplicationGraph
import org.jacodb.api.analysis.JcApplicationGraph
import org.jacodb.api.cfg.JcInst
import java.util.concurrent.ConcurrentHashMap

class IfdsBaseUnitRunner(private val analyzerFactory: AnalyzerFactory) : IfdsUnitRunner {
    override suspend fun <UnitType> run(
        graph: JcApplicationGraph,
        summary: Summary,
        unitResolver: UnitResolver<UnitType>,
        unit: UnitType,
        startMethods: List<JcMethod>
    ) {
        val analyzer = analyzerFactory.createAnalyzer(graph)
        val instance = IfdsInstance(graph, analyzer, summary, unitResolver, unit, startMethods)
        instance.analyze()
    }
}

private class IfdsInstance<UnitType>(
    private val graph: ApplicationGraph<JcMethod, JcInst>,
    private val analyzer: Analyzer,
    private val summary: Summary,
    private val unitResolver: UnitResolver<UnitType>,
    private val unit: UnitType,
    private val startMethods: List<JcMethod>
) {

    private val pathEdges: MutableSet<IfdsEdge> = ConcurrentHashMap.newKeySet()
    private val summaryEdges: MutableMap<IfdsVertex, MutableSet<IfdsVertex>> = mutableMapOf()
    private val callSitesOf: MutableMap<IfdsVertex, MutableSet<IfdsEdge>> = mutableMapOf()
    private val pathEdgesPreds: MutableMap<IfdsEdge, MutableSet<PathEdgePredecessor>> = ConcurrentHashMap()
    private val verticesWithTraceGraphNeeded: MutableSet<IfdsVertex> = mutableSetOf()
    private val summaryHandlers: MutableMap<JcMethod, SummarySender> = mutableMapOf()
    private val visitedMethods: MutableSet<JcMethod> = mutableSetOf()

    private val flowSpace get() = analyzer.flowFunctions

    private fun stashSummaryFact(fact: SummaryFact) {
        val handler = summaryHandlers.getOrPut(fact.method) { summary.createSender(fact.method) }
        handler.send(fact)
        if (fact is VulnerabilityLocation) {
            verticesWithTraceGraphNeeded.add(fact.sink)
        }
        if (fact is CrossUnitCallFact) {
            verticesWithTraceGraphNeeded.add(fact.callerVertex)
        }
    }

    private fun addStart(method: JcMethod) {
        require(unitResolver.resolve(method) == unit)
        for (sPoint in graph.entryPoint(method)) {
            for (sFact in flowSpace.obtainStartFacts(sPoint)) {
                val vertex = IfdsVertex(sPoint, sFact)
                val edge = IfdsEdge(vertex, vertex)
                propagate(edge, PathEdgePredecessor(edge, PredecessorKind.NoPredecessor))
            }
        }
    }

    private fun propagate(e: IfdsEdge, pred: PathEdgePredecessor): Boolean {
        pathEdgesPreds.getOrPut(e) { mutableSetOf() }.add(pred)
        val predsSet = pathEdgesPreds.computeIfAbsent(e) { mutableSetOf() }

        synchronized(predsSet) {
            predsSet.add(pred)
        }

        if (pathEdges.add(e)) {
            require(workList.trySend(e).isSuccess)

            analyzer.getSummaryFacts(e).forEach {
                stashSummaryFact(it)
            }

            if (analyzer.saveSummaryEdgesAndCrossUnitCalls && e.v.statement in graph.exitPoints(e.method)) {
                stashSummaryFact(PathEdgeFact(e))
            }

            return true
        }
        return false
    }

    private val JcMethod.isExtern: Boolean
        get() = unitResolver.resolve(this) != unit

    private val workList = Channel<IfdsEdge>(Channel.UNLIMITED)

    private suspend fun takeNewEdge(): IfdsEdge? {
        return try {
            withTimeout(200) {
                workList.receive()
            }
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun runTabulationAlgorithm(): Unit = coroutineScope {
        startMethods.forEach { addStart(it) }

        while (isActive) {
            val curEdge = takeNewEdge() ?: throw CancellationException()

            if (curEdge.method !in visitedMethods) {
                visitedMethods.add(curEdge.method)
                summary // Listen for incoming updates
                    .getFactsFiltered<PathEdgeFact>(curEdge.method, null)
                    .filter { it.edge.u.statement in graph.entryPoint(curEdge.method) } // Filter out backward edges
                    .onEach { propagate(it.edge, PathEdgePredecessor(it.edge, PredecessorKind.Unknown)) }
                    .launchIn(this)
            }

            val (u, v) = curEdge
            val (curVertex, curFact) = v

            val callees = graph.callees(curVertex).toList()
            if (callees.isNotEmpty()) {
                for (returnSite in graph.successors(curVertex)) {
                    for (fact in flowSpace.obtainCallToReturnFlowFunction(curVertex, returnSite).compute(curFact)) {
                        val newEdge = IfdsEdge(u, IfdsVertex(returnSite, fact))
                        propagate(newEdge, PathEdgePredecessor(curEdge, PredecessorKind.Sequent))
                    }
                    for (callee in callees) {
                        val factsAtStart = flowSpace.obtainCallToStartFlowFunction(curVertex, callee).compute(curFact)
                        for (sPoint in graph.entryPoint(callee)) {
                            for (sFact in factsAtStart) {
                                val sVertex = IfdsVertex(sPoint, sFact)

                                val exitVertices: Flow<IfdsVertex> = if (callee.isExtern) {
//                                    stashSummaryFact(PathEdgeFact(IfdsEdge(sVertex, sVertex))) // Requesting info for this start fact
                                    summary.getFactsFiltered<PathEdgeFact>(callee, sVertex)
                                        .filter { it.edge.u == sVertex && it.edge.v.statement in graph.exitPoints(callee) }
                                        .map { it.edge.v }
                                } else {
                                    val nextEdge = IfdsEdge(sVertex, sVertex)
                                    propagate(nextEdge, PathEdgePredecessor(curEdge, PredecessorKind.CallToStart))
                                    summaryEdges[sVertex].orEmpty().asFlow()
                                }

                                exitVertices.onEach { (eStatement, eFact) ->
                                    val finalFacts =
                                        flowSpace.obtainExitToReturnSiteFlowFunction(curVertex, returnSite, eStatement)
                                            .compute(eFact)
                                    for (finalFact in finalFacts) {
                                        val summaryEdge = IfdsEdge(IfdsVertex(sPoint, sFact), IfdsVertex(eStatement, eFact))
                                        val newEdge = IfdsEdge(u, IfdsVertex(returnSite, finalFact))
                                        propagate(newEdge, PathEdgePredecessor(curEdge, PredecessorKind.ThroughSummary(summaryEdge)))
                                    }
                                }.launchIn(this)

                                if (callee.isExtern) {
                                    if (analyzer.saveSummaryEdgesAndCrossUnitCalls) {
//                                        launch {
//                                            summary.getFactsFiltered<TraceGraphFact>(callee, null)
//                                                .filter { sVertex in it.graph.edges.keys }
//                                                .first()
//                                            stashSummaryFact(CrossUnitCallFact(v, sVertex))
//                                        }
                                        stashSummaryFact(CrossUnitCallFact(v, sVertex))
                                    }
                                } else {
                                    callSitesOf.getOrPut(sVertex) { mutableSetOf() }.add(curEdge)
                                }
                            }
                        }
                    }
                }
            } else {
                if (curVertex in graph.exitPoints(graph.methodOf(curVertex))) {
                    for (callerEdge in callSitesOf[u].orEmpty()) {
                        val callerStatement = callerEdge.v.statement
                        for (returnSite in graph.successors(callerStatement)) {
                            for (returnSiteFact in flowSpace.obtainExitToReturnSiteFlowFunction(callerStatement, returnSite, curVertex).compute(curFact)) {
                                val returnSiteVertex = IfdsVertex(returnSite, returnSiteFact)
                                val newEdge = IfdsEdge(callerEdge.u, returnSiteVertex)
                                propagate(newEdge, PathEdgePredecessor(callerEdge, PredecessorKind.ThroughSummary(curEdge)))
                            }
                        }
                    }
                    summaryEdges.getOrPut(curEdge.u) { mutableSetOf() }.add(curEdge.v)
                }

                for (nextInst in graph.successors(curVertex)) {
                    val nextFacts = flowSpace.obtainSequentFlowFunction(curVertex, nextInst).compute(curFact)
                    for (nextFact in nextFacts) {
                        val newEdge = IfdsEdge(u, IfdsVertex(nextInst, nextFact))
                        propagate(newEdge, PathEdgePredecessor(curEdge, PredecessorKind.Sequent))
                    }
                }
            }
            yield()
        }
    }

    private val fullResults: IfdsResult by lazy {
        val allEdges = pathEdges.toList()

        val resultFacts = allEdges.groupBy({ it.v.statement }) {
            it.v.domainFact
        }.mapValues { (_, facts) -> facts.toSet() }

        IfdsResult(allEdges, resultFacts, pathEdgesPreds)
    }

    private fun postActions() {
        analyzer.getSummaryFactsPostIfds(fullResults).forEach { vulnerability ->
            stashSummaryFact(vulnerability)
        }

        verticesWithTraceGraphNeeded.forEach { vertex ->
            val graph = fullResults.resolveTraceGraph(vertex)
            stashSummaryFact(TraceGraphFact(graph))
        }
    }

    suspend fun analyze() =
        try {
            runTabulationAlgorithm()
        } finally {
            postActions()
        }
}