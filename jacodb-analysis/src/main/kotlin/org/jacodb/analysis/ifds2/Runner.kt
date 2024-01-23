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

package org.jacodb.analysis.ifds2

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.getOrElse
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import org.jacodb.analysis.engine.UnitResolver
import org.jacodb.analysis.engine.UnitType
import org.jacodb.api.JcMethod
import org.jacodb.api.analysis.JcApplicationGraph
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.ext.cfg.callExpr

private val logger = KotlinLogging.logger {}

// TODO: make all fields private again
class Runner(
    internal val graph: JcApplicationGraph,
    internal val analyzer: Analyzer,
    internal val manager: Manager,
    internal val unitResolver: UnitResolver,
    internal val unit: UnitType,
) {
    internal val flowSpace: FlowFunctionsSpace = analyzer.flowFunctions
    internal val workList: Channel<Edge> = Channel(Channel.UNLIMITED)
    internal val pathEdges: MutableSet<Edge> = mutableSetOf() // TODO: replace with concurrent set
    internal val summaryEdges: MutableMap<Vertex, MutableSet<Vertex>> = mutableMapOf()
    internal val callSitesOf: MutableMap<Vertex, MutableSet<Edge>> = mutableMapOf()

    suspend fun run(startMethods: List<JcMethod>) {
        for (method in startMethods) {
            addStart(method)
        }

        tabulationAlgorithm()
    }

    // TODO: should 'addStart' be public?
    // TODO: should 'addStart' replace 'submitNewEdge'?
    // TODO: inline
    private suspend fun addStart(method: JcMethod) {
        require(unitResolver.resolve(method) == unit)
        val startFacts = flowSpace.obtainPossibleStartFacts(method)
        for (startFact in startFacts) {
            for (start in graph.entryPoints(method)) {
                val vertex = Vertex(start, startFact)
                val edge = Edge(vertex, vertex) // loop
                propagate(edge)
            }
        }
    }

    suspend fun submitNewEdge(edge: Edge) {
        propagate(edge)
    }

    private suspend fun propagate(edge: Edge, reason: Edge? = null): Boolean {
        require(unitResolver.resolve(edge.method) == unit) {
            "Propagated edge must be in the same unit"
        }

        // Handle only NEW edges:
        if (pathEdges.add(edge)) {
            if (reason != null) {
                edge.reason = reason
            }

            val doPrintZero = false
            if (edge.from.statement.toString() == "noop") {
                if (doPrintZero || edge.to.fact != Zero) {
                    logger.debug { "Propagating edge=$edge in method=${edge.method} via reason=${edge.reason}" }
                }
            }

            // Add edge to worklist:
            workList.send(edge)

            // Send edge to analyzer/manager:
            for (event in analyzer.handleNewEdge(edge)) {
                manager.handleEvent(event)
            }

            return true
        }

        return false
    }

    private suspend fun tabulationAlgorithm() = coroutineScope {
        while (isActive) {
            val edge = workList.tryReceive().getOrElse {
                manager.handleEvent(QueueEmptinessChanged(this@Runner, true))
                val edge = workList.receive()
                manager.handleEvent(QueueEmptinessChanged(this@Runner, false))
                edge
            }
            tabulationAlgorithmStep(edge, this@coroutineScope)
        }
    }

    private val JcMethod.isExtern: Boolean
        get() = unitResolver.resolve(this) != unit

    private suspend fun tabulationAlgorithmStep(currentEdge: Edge, scope: CoroutineScope) {
        val (startVertex, currentVertex) = currentEdge
        val (current, currentFact) = currentVertex

        val currentCallees = graph.callees(current).toList()
        // val currentIsCall = currentCallees.isNotEmpty()
        val currentIsCall = current.callExpr != null
        val currentIsExit = current in graph.exitPoints(current.location.method)

        if (currentIsCall) {
            // Propagate through the call-to-return-site edge:
            for (returnSite in graph.successors(current)) {
                val factsAtReturnSite = flowSpace
                    .obtainCallToReturnSiteFlowFunction(current, returnSite)
                    .compute(currentFact)
                for (returnSiteFact in factsAtReturnSite) {
                    val returnSiteVertex = Vertex(returnSite, returnSiteFact)
                    val newEdge = Edge(startVertex, returnSiteVertex)
                    propagate(newEdge, reason = currentEdge)
                }
            }

            // Propagate through the call:
            for (callee in currentCallees) {
                // TODO: check whether we need to analyze the callee (or it was skipped due to MethodSource)
                if (analyzer.isSkipped(callee)) {
                    logger.info { "Skipping method $callee" }
                } else {
                    val factsAtCalleeStart = flowSpace
                        .obtainCallToStartFlowFunction(current, callee)
                        .compute(currentFact)
                    for (calleeStart in graph.entryPoints(callee)) {
                        for (calleeStartFact in factsAtCalleeStart) {
                            val calleeStartVertex = Vertex(calleeStart, calleeStartFact)

                            if (callee.isExtern) {
                                // Initialize analysis of callee:
                                for (event in analyzer.handleCrossUnitCall(currentVertex, calleeStartVertex)) {
                                    manager.handleEvent(event)
                                }

                                // Subscribe on summary edges:
                                val event = SubscriptionForSummaryEdges3(callee, scope) { edge ->
                                    if (edge.from == calleeStartVertex) {
                                        handleSummaryEdge(edge, startVertex, current)
                                    } else {
                                        logger.debug { "Skipping unsuitable edge $edge" }
                                    }
                                }
                                manager.handleEvent(event)
                            } else {
                                // Save info about the call for summary edges that will be found later:
                                callSitesOf.getOrPut(calleeStartVertex) { mutableSetOf() }.add(currentEdge)

                                // Initialize analysis of callee:
                                run {
                                    val newEdge = Edge(calleeStartVertex, calleeStartVertex) // loop
                                    propagate(newEdge, reason = currentEdge)
                                }

                                // Handle already-found summary edges:
                                for (exitVertex in summaryEdges[calleeStartVertex].orEmpty()) {
                                    val edge = Edge(calleeStartVertex, exitVertex)
                                    handleSummaryEdge(edge, startVertex, current)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            if (currentIsExit) {
                // Propagate through the summary edge:
                for (callerPathEdge in callSitesOf[startVertex].orEmpty()) {
                    val callerStartVertex = callerPathEdge.from
                    val caller = callerPathEdge.to.statement
                    handleSummaryEdge(currentEdge, callerStartVertex, caller)
                }

                // Add new summary edge:
                summaryEdges.getOrPut(startVertex) { mutableSetOf() }.add(currentVertex)
            }

            // Simple (sequential) propagation to the next instruction:
            for (next in graph.successors(current)) {
                val factsAtNext = flowSpace
                    .obtainSequentFlowFunction(current, next)
                    .compute(currentFact)
                for (nextFact in factsAtNext) {
                    val nextVertex = Vertex(next, nextFact)
                    val newEdge = Edge(startVertex, nextVertex)
                    propagate(newEdge, reason = currentEdge)
                }
            }
        }
    }

    private suspend fun handleSummaryEdge(
        edge: Edge,
        startVertex: Vertex,
        caller: JcInst,
    ) {
        for (returnSite in graph.successors(caller)) {
            // val calleeStartVertex = edge.from
            val (exit, exitFact) = edge.to
            val finalFacts = flowSpace
                .obtainExitToReturnSiteFlowFunction(caller, returnSite, exit)
                .compute(exitFact)
            for (returnSiteFact in finalFacts) {
                val returnSiteVertex = Vertex(returnSite, returnSiteFact)
                val newEdge = Edge(startVertex, returnSiteVertex)
                propagate(newEdge, reason = edge)
            }
        }
    }
}
