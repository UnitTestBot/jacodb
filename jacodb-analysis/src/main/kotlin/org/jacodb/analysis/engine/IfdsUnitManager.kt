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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.jacodb.analysis.VulnerabilityInstance
import org.jacodb.analysis.logger
import org.jacodb.analysis.graph.newApplicationGraphForAnalysis
import org.jacodb.api.JcMethod
import org.jacodb.api.analysis.JcApplicationGraph
import java.util.concurrent.ConcurrentHashMap

/**
 * Used to launch and manage [ifdsUnitRunner] jobs for units, reachable from [startMethods].
 * See [runAnalysis] for more info.
 */
class IfdsUnitManager<UnitType>(
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
    private val summary: Summary = SummaryImpl()

    init {
        startMethods.forEach { addStart(it) }
    }

    private val IfdsVertex.traceGraph: TraceGraph
        get() = summary
            .getCurrentFactsFiltered<TraceGraphFact>(method, this)
            .map { it.graph }
            .singleOrNull { it.sink == this }
            ?: TraceGraph.bySink(this)

    /**
     * Launches [ifdsUnitRunner] for each observed unit, handles respective jobs,
     * and gathers results into list of vulnerabilities, restoring full traces
     */
    fun analyze(): List<VulnerabilityInstance> = runBlocking(Dispatchers.Default) {
        val unitJobs: MutableMap<UnitType, Job> = ConcurrentHashMap()
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
            unitJobs[unit] = launch {
                withTimeout(timeoutMillis) {
                    ifdsUnitRunner.run(graph, summary, unitResolver, unit, foundMethods[unit]!!.toList())
                }
            }
        }

        unitJobs.values.joinAll()
        progressLoggerJob.cancelAndJoin()
        logger.info { "All jobs completed, gathering results..." }

        val foundVulnerabilities = foundMethods.values.flatten().flatMap { method ->
            summary.getCurrentFactsFiltered<VulnerabilityLocation>(method, null)
        }

        foundMethods.values.flatten().forEach { method ->
            for (crossUnitCall in summary.getCurrentFactsFiltered<CrossUnitCallFact>(method, null)) {
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

/**
 * This is the entry point for every analysis.
 * Calling this function will find all vulnerabilities reachable from [methods].
 *
 * @param graph instance of [JcApplicationGraph] that provides mixture of CFG and call graph
 * (called supergraph in RHS95).
 * Usually built by [newApplicationGraphForAnalysis].
 *
 * @param unitResolver instance of [UnitResolver] which splits all methods into groups of methods, called units.
 * Units are analyzed concurrently, one unit will be analyzed with one call to [IfdsUnitRunner.run] method.
 * In general, larger units mean more precise, but also more resource-consuming analysis, so [unitResolver] allows
 * to reach compromise.
 * It is guaranteed that [Summary] passed to all units is the same, so they can share information through it.
 * However, the order of launching and terminating analysis for units is an implementation detail and may vary even for
 * consecutive calls of this method with same arguments.
 *
 * @param ifdsUnitRunner an [IfdsUnitRunner] instance that will be launched for each unit.
 * This is the main argument that defines the analysis.
 *
 * @param methods the list of method for analysis.
 * Each vulnerability will only be reported if it is reachable from one of these.
 *
 * @param timeoutMillis the maximum time for analysis.
 * Note that this does not include time for precalculations
 * (like searching for reachable methods and splitting them into units) and postcalculations (like restoring traces), so
 * the actual running time of this method may be longer.
 */
fun runAnalysis(
    graph: JcApplicationGraph,
    unitResolver: UnitResolver<*>,
    ifdsUnitRunner: IfdsUnitRunner,
    methods: List<JcMethod>,
    timeoutMillis: Long = Long.MAX_VALUE
): List<VulnerabilityInstance> {
    return IfdsUnitManager(graph, unitResolver, ifdsUnitRunner, methods, timeoutMillis).analyze()
}