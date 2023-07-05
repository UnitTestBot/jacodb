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

import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jacodb.analysis.AnalysisEngine
import org.jacodb.analysis.VulnerabilityInstance
import org.jacodb.analysis.logger
import org.jacodb.api.JcMethod
import org.jacodb.api.analysis.JcApplicationGraph
import org.jacodb.impl.BackgroundScope
import java.util.concurrent.ConcurrentHashMap

class IfdsUnitTraverser<UnitType>(
    private val graph: JcApplicationGraph,
    private val unitResolver: UnitResolver<UnitType>,
    private val ifdsInstanceFactory: IfdsInstanceFactory
) : AnalysisEngine {
    private val initMethods: MutableSet<JcMethod> = mutableSetOf()

    private val unitsQueue: MutableSet<UnitType> = mutableSetOf()
    private val foundMethods: MutableMap<UnitType, MutableSet<JcMethod>> = mutableMapOf()
    private val dependsOn: MutableMap<UnitType, Int> = mutableMapOf()
    private val crossUnitCallers: MutableMap<JcMethod, MutableSet<CalleeFact>> = mutableMapOf()
    private val summary: Summary = SummaryImpl()
    private val unitJobs: MutableMap<UnitType, Job> = mutableMapOf()
    private val scope = BackgroundScope()
    private val foundVulnerabilities: MutableSet<VulnerabilityLocation> = ConcurrentHashMap.newKeySet()

    private val IfdsVertex.traceGraph: TraceGraph
        get() = summary
            .getCurrentFactsFiltered<TraceGraphFact>(method, this)
            .map { it.graph }
            .singleOrNull { it.sink == this }
            ?: TraceGraph.bySink(this)

    override fun analyze(): List<VulnerabilityInstance> = runBlocking {
        logger.info { "Started traversing" }
        logger.info { "Amount of units to analyze: ${unitsQueue.size}" }
        while (unitsQueue.isNotEmpty()) {
            logger.info { "${unitsQueue.size} unit(s) left" }

            // TODO: do smth smarter here
            val next = unitsQueue.minBy { dependsOn[it]!! }
            unitsQueue.remove(next)
            foundMethods[next]!!.forEach { method ->
                summary.getFactsFiltered<VulnerabilityLocation>(method, null)
                    .onEach { foundVulnerabilities.add(it) }
                    .launchIn(scope)
            }

            unitJobs[next] = launch {
                buildIfdsInstance(ifdsInstanceFactory, graph, summary, unitResolver, next) {
                    for (method in foundMethods[next].orEmpty()) {
                        addStart(method)
                    }
                }
            }
        }

        delay(250)
//        error("A")

        coroutineScope {
            unitJobs.values.forEach { it.cancel() }
            unitJobs.values.forEach { it.join() }
        }

        scope.coroutineContext.job.cancelAndJoin()

        foundMethods.values.flatten().forEach { method ->
            for (calleeFact in summary.getCurrentFactsFiltered<CalleeFact>(method, null)) {
                for (sFact in calleeFact.factsAtCalleeStart) {
                    val calledMethod = graph.methodOf(sFact.statement)
                    crossUnitCallers.getOrPut(calledMethod) { mutableSetOf() }.add(calleeFact)
                }
            }
        }

        logger.info { "Restoring traces..." }
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
            for (callee in crossUnitCallers[method].orEmpty()) {
                val sFacts = callee.factsAtCalleeStart
                val upGraph = callee.vertex.traceGraph
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

    override fun addStart(method: JcMethod) {
        initMethods.add(method)
        internalAddStart(method)
    }
}