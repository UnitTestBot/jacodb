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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.jacodb.analysis.logger
import org.jacodb.analysis.runAnalysis
import org.jacodb.api.JcMethod
import org.jacodb.api.analysis.JcApplicationGraph
import java.util.concurrent.ConcurrentHashMap

interface IfdsUnitCommunicator {
    val incomingEdges: Flow<IfdsEdge>

    fun subscribeForSummaryEdgesOf(method: JcMethod): Flow<IfdsEdge>

    fun uploadSummaryFact(fact: SummaryFact)

    fun addEdgeForOtherRunner(edge: IfdsEdge)

    fun updateQueueStatus(isEmpty: Boolean)
}

/**
 * Used to launch and manage [ifdsUnitRunner] jobs for units, reachable from [startMethods].
 * See [runAnalysis] for more info.
 */
class MainIfdsUnitManager<UnitType>(
    private val graph: JcApplicationGraph,
    private val unitResolver: UnitResolver<UnitType>,
    private val ifdsUnitRunner: IfdsUnitRunner,
    private val startMethods: List<JcMethod>,
    private val timeoutMillis: Long
) {

    private val unitsQueue: MutableSet<UnitType> = mutableSetOf()
    private val foundMethods: MutableMap<UnitType, MutableSet<JcMethod>> = mutableMapOf()
    private val dependsOn: MutableMap<UnitType, Int> = mutableMapOf()
    private val crossUnitCallers: MutableMap<JcMethod, MutableSet<CrossUnitCallFact>> = mutableMapOf()

    private val summaryEdgesStorage = SummaryStorageImpl<SummaryEdgeFact>()
    private val tracesStorage = SummaryStorageImpl<TraceGraphFact>()
    private val crossUnitCallsStorage = SummaryStorageImpl<CrossUnitCallFact>()
    private val vulnerabilitiesStorage = SummaryStorageImpl<VulnerabilityLocation>()
    private val unitJobs: MutableMap<UnitType, Job> = ConcurrentHashMap()
    private val communicators: MutableMap<UnitType, MyIfdsUnitCommunicator> = ConcurrentHashMap()

    inner class MyIfdsUnitCommunicator(private val unit: UnitType) : IfdsUnitCommunicator {
        override val incomingEdges: MutableSharedFlow<IfdsEdge> = MutableSharedFlow(replay = Int.MAX_VALUE)

        override fun subscribeForSummaryEdgesOf(method: JcMethod): Flow<IfdsEdge> {
            return summaryEdgesStorage.getFacts(method, null).map {
                it.edge
            }
        }

        override fun uploadSummaryFact(fact: SummaryFact) {
            when (fact) {
                is CrossUnitCallFact -> crossUnitCallsStorage.addNewSender(fact.method).send(fact)
                is SummaryEdgeFact -> summaryEdgesStorage.addNewSender(fact.method).send(fact)
                is TraceGraphFact -> tracesStorage.addNewSender(fact.method).send(fact)
                is VulnerabilityLocation -> vulnerabilitiesStorage.addNewSender(fact.method).send(fact)
            }
        }

        override fun addEdgeForOtherRunner(edge: IfdsEdge) {
            // TODO: cache queries for not yet launched units
            communicators[unit]?.incomingEdges?.tryEmit(edge)
        }

        override fun updateQueueStatus(isEmpty: Boolean) {
            if (isEmpty) {
                unitJobs[unit]?.cancel()
            }
        }
    }

    init {
        startMethods.forEach { addStart(it) }
    }

    private val IfdsVertex.traceGraph: TraceGraph
        get() = tracesStorage
            .getCurrentFacts(method, null)
            .map { it.graph }
            .singleOrNull { it.sink == this }
            ?: TraceGraph.bySink(this)

    /**
     * Launches [ifdsUnitRunner] for each observed unit, handles respective jobs,
     * and gathers results into list of vulnerabilities, restoring full traces
     */
    fun analyze(): List<VulnerabilityInstance> = runBlocking(Dispatchers.Default) {
        val unitsCount = unitsQueue.size

        logger.info { "Starting analysis. Number of units to analyze: $unitsCount" }

        val progressLoggerJob = launch {
            while (isActive) {
                delay(1000)
                logger.info {
                    "Current progress: ${unitJobs.values.count { it.isCompleted }} / $unitsCount units completed"
                }
            }
        }

        // TODO: do smth smarter here
        unitsQueue.toList().forEach { unit ->
            val communicator = MyIfdsUnitCommunicator(unit)
            communicators[unit] = communicator

            val job = launch {
                withTimeout(timeoutMillis) {
                    ifdsUnitRunner.run(
                        graph,
                        communicator,
                        unitResolver,
                        unit,
                        foundMethods[unit]!!.toList()
                    )
                }
            }

            unitJobs[unit] = job
            // TODO: clear unitJobs and communicators on-fly
        }

        unitJobs.values.joinAll()
        progressLoggerJob.cancelAndJoin()
        logger.info { "All jobs completed, gathering results..." }

        val foundVulnerabilities = foundMethods.values.flatten().flatMap { method ->
            vulnerabilitiesStorage.getCurrentFacts(method, null)
        }

        foundMethods.values.flatten().forEach { method ->
            for (crossUnitCall in crossUnitCallsStorage.getCurrentFacts(method, null)) {
                val calledMethod = graph.methodOf(crossUnitCall.calleeVertex.statement)
                crossUnitCallers.getOrPut(calledMethod) { mutableSetOf() }.add(crossUnitCall)
            }
        }

        logger.info { "Restoring traces..." }

        foundVulnerabilities
            .map { VulnerabilityInstance(it.vulnerabilityType, extendTraceGraph(it.sink.traceGraph)) }
            .filter {
                it.traceGraph.sources.any { source ->
                    graph.methodOf(source.statement) in startMethods || source.domainFact == ZEROFact
                }
            }
    }

    private val TraceGraph.methods: List<JcMethod>
        get() {
            return (edges.keys.map { graph.methodOf(it.statement) } +
                    listOf(graph.methodOf(sink.statement))).distinct()
        }

    /**
     * Given a [traceGraph], searches for other traceGraphs (from different units)
     * and merges them into given if they extend any path leading to sink.
     *
     * This method allows to restore traces that pass through several units.
     */
    private fun extendTraceGraph(traceGraph: TraceGraph): TraceGraph {
        var result = traceGraph
        val methodQueue: MutableSet<JcMethod> = traceGraph.methods.toMutableSet()
        val addedMethods: MutableSet<JcMethod> = methodQueue.toMutableSet()
        while (methodQueue.isNotEmpty()) {
            val method = methodQueue.first()
            methodQueue.remove(method)
            for (callFact in crossUnitCallers[method].orEmpty()) {
                // TODO: merge calleeVertices here
                val sFacts = setOf(callFact.calleeVertex)
                val upGraph = callFact.callerVertex.traceGraph
                val newValue = result.mergeWithUpGraph(upGraph, sFacts)
                if (result != newValue) {
                    result = newValue
                    for (nMethod in upGraph.methods) {
                        if (nMethod !in addedMethods) {
                            addedMethods.add(nMethod)
                            methodQueue.add(nMethod)
                        }
                    }
                }
            }
        }
        return result
    }

    private fun getAllDependencies(method: JcMethod): Set<JcMethod> {
        val result = mutableSetOf<JcMethod>()
        for (inst in method.flowGraph().instructions) {
            graph.callees(inst).forEach {
                result.add(it)
            }
        }
        return result
    }

    private fun addStart(method: JcMethod) {
        val unit = unitResolver.resolve(method)
        if (method in foundMethods[unit].orEmpty()) {
            return
        }

        unitsQueue.add(unit)
        foundMethods.getOrPut(unit) { mutableSetOf() }.add(method)
        val dependencies = getAllDependencies(method)
        dependencies.forEach { addStart(it) }
        dependsOn[unit] = dependsOn.getOrDefault(unit, 0) + dependencies.size
    }
}