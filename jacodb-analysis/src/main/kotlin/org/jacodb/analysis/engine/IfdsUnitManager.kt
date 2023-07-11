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
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.jacodb.analysis.VulnerabilityInstance
import org.jacodb.analysis.logger
import org.jacodb.api.JcMethod
import org.jacodb.api.analysis.JcApplicationGraph

class IfdsUnitManager<UnitType>(
    private val graph: JcApplicationGraph,
    private val unitResolver: UnitResolver<UnitType>,
    private val ifdsUnitRunner: IfdsUnitRunner,
    private val timeoutMillis: Long = Long.MAX_VALUE
) {
    private val initMethods: MutableSet<JcMethod> = mutableSetOf()

    private val unitsQueue: MutableSet<UnitType> = mutableSetOf()
    private val foundMethods: MutableMap<UnitType, MutableSet<JcMethod>> = mutableMapOf()
    private val dependsOn: MutableMap<UnitType, Int> = mutableMapOf()
    private val crossUnitCallers: MutableMap<JcMethod, MutableSet<CrossUnitCallFact>> = mutableMapOf()
    private val summary: Summary = SummaryImpl()

    private val IfdsVertex.traceGraph: TraceGraph
        get() = summary
            .getCurrentFactsFiltered<TraceGraphFact>(method, this)
            .map { it.graph }
            .singleOrNull { it.sink == this }
            ?: TraceGraph.bySink(this)

    fun analyze(): List<VulnerabilityInstance> = runBlocking(Dispatchers.Default) {
        val unitJobs: MutableMap<UnitType, Job> = mutableMapOf()

        logger.info { "Started traversing" }
        logger.info { "Number of units to analyze: ${unitsQueue.size}" }
        while (unitsQueue.isNotEmpty()) {
            // TODO: do smth smarter here
            val next = unitsQueue.minBy { dependsOn[it]!! }
            unitsQueue.remove(next)

            unitJobs[next] = launch {
                try {
                    withTimeout(timeoutMillis) {
                        ifdsUnitRunner.run(graph, summary, unitResolver, next, foundMethods[next]!!.toList())
                    }
                } finally {
                    logger.info { "Job for $next completed, remaining: ${unitJobs.values.count { it.isActive }}" }
                }
            }
        }

        logger.info { "Waiting for units to be analyzed" }
        unitJobs.values.forEach { it.join() }

        val foundVulnerabilities = foundMethods.values.flatten().flatMap { method ->
            summary.getCurrentFactsFiltered<VulnerabilityLocation>(method, null)
        }

        logger.info { "Restoring traces..." }

        foundMethods.values.flatten().forEach { method ->
            for (crossUnitCall in summary.getCurrentFactsFiltered<CrossUnitCallFact>(method, null)) {
                val calledMethod = graph.methodOf(crossUnitCall.calleeVertex.statement)
                crossUnitCallers.getOrPut(calledMethod) { mutableSetOf() }.add(crossUnitCall)
            }
        }

        foundVulnerabilities
            .map { VulnerabilityInstance(it.vulnerabilityType, extendTraceGraph(it.sink.traceGraph)) }
            .filter {
                it.traceGraph.sources.any { source ->
                    graph.methodOf(source.statement) in initMethods || source.domainFact == ZEROFact
                }
            }
    }

    private val TraceGraph.methods: List<JcMethod>
        get() {
            return (edges.keys.map { graph.methodOf(it.statement) } +
                    listOf(graph.methodOf(sink.statement))).distinct()
        }

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

    private fun internalAddStart(method: JcMethod) {
        val unit = unitResolver.resolve(method)
        if (method in foundMethods[unit].orEmpty()) {
            return
        }

        unitsQueue.add(unit)
        foundMethods.getOrPut(unit) { mutableSetOf() }.add(method)
        val dependencies = getAllDependencies(method)
        dependencies.forEach { internalAddStart(it) }
        dependsOn[unit] = dependsOn.getOrDefault(unit, 0) + dependencies.size
    }

    fun addStart(method: JcMethod) {
        initMethods.add(method)
        internalAddStart(method)
    }
}

fun runAnalysis(
    graph: JcApplicationGraph,
    unitResolver: UnitResolver<*>,
    ifdsUnitRunner: IfdsUnitRunner,
    methods: List<JcMethod>,
    timeoutMillis: Long = Long.MAX_VALUE
): List<VulnerabilityInstance> {
    val manager = IfdsUnitManager(graph, unitResolver, ifdsUnitRunner, timeoutMillis)
    for (method in methods) {
        manager.addStart(method)
    }
    return manager.analyze()
}