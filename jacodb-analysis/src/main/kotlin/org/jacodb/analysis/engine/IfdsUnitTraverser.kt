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
import org.jacodb.analysis.VulnerabilityInstance
import org.jacodb.analysis.points2.Devirtualizer
import org.jacodb.api.JcMethod
import org.jacodb.api.analysis.JcApplicationGraph
import org.jacodb.impl.fs.logger

interface AnalysisContext {
    val summaries: Map<JcMethod, IfdsMethodSummary>
}

class IfdsUnitTraverser<UnitType>(
    private val graph: JcApplicationGraph,
    private val unitResolver: UnitResolver<UnitType>,
    private val devirtualizer: Devirtualizer,
    private val ifdsInstanceProvider: IfdsInstanceProvider
) : AnalysisEngine {
    private val contextInternal: MutableMap<JcMethod, IfdsMethodSummary> = mutableMapOf()
    private val context = object : AnalysisContext {
        override val summaries: Map<JcMethod, IfdsMethodSummary>
            get() = contextInternal
    }

    private val initMethods: MutableSet<JcMethod> = mutableSetOf()

    private val unitsQueue: MutableSet<UnitType> = mutableSetOf()
    private val foundMethods: MutableMap<UnitType, MutableSet<JcMethod>> = mutableMapOf()
    private val dependsOn: MutableMap<UnitType, Int> = mutableMapOf()
    private val crossUnitCallees: MutableMap<JcMethod, MutableSet<IfdsEdge>> = mutableMapOf()

    override fun analyze(): List<VulnerabilityInstance> {
        logger.info { "Started traversing" }
        logger.info { "Amount of units to analyze: ${unitsQueue.size}" }
        while (unitsQueue.isNotEmpty()) {
            logger.info { "${unitsQueue.size} unit(s) left" }

            // TODO: do smth smarter here
            val next = unitsQueue.minBy { dependsOn[it]!! }
            unitsQueue.remove(next)

            val ifdsInstance = ifdsInstanceProvider.createInstance(graph, devirtualizer, context, unitResolver, next)
            for (method in foundMethods[next].orEmpty()) {
                ifdsInstance.addStart(method)
            }

            val results = ifdsInstance.analyze()

            // Relaxing of crossUnitCallees
            for ((_, summary) in results) {
                for ((inc, outcs) in summary.crossUnitCallees) {
                    for (outc in outcs.factsAtCalleeStart) {
                        val calledMethod = graph.methodOf(outc.statement)
                        val newEdge = IfdsEdge(inc, outc)
                        crossUnitCallees.getOrPut(calledMethod) { mutableSetOf() }.add(newEdge)
                    }
                }
            }

            results.forEach { (method, summary) ->
                contextInternal[method] = summary
            }
        }

        logger.info { "Restoring full realisation paths..." }
        // TODO: think about correct filters for overall results
        val vulnerabilities = context.summaries.flatMap { (_, summary) ->
            summary.foundVulnerabilities
                .map { VulnerabilityInstance(it.vulnerabilityType, extendRealisationsGraph(it.realisationsGraph)) }
                .filter {
                    it.realisationsGraph.sources.any { source ->
                        graph.methodOf(source.statement) in initMethods || source.domainFact == ZEROFact
                    }
                }
        }

        logger.info { "Analysis completed" }
        return vulnerabilities
    }

    private val TaintRealisationsGraph.methods: List<JcMethod>
        get() {
            return (edges.keys.map { graph.methodOf(it.statement) } +
                    listOf(graph.methodOf(sink.statement))).distinct()
        }

    private fun extendRealisationsGraph(realisationsGraph: TaintRealisationsGraph): TaintRealisationsGraph {
        var result = realisationsGraph
        val methodQueue: MutableSet<JcMethod> = realisationsGraph.methods.toMutableSet()
        val addedMethods: MutableSet<JcMethod> = mutableSetOf()
        while (methodQueue.isNotEmpty()) {
            val method = methodQueue.first()
            methodQueue.remove(method)
            addedMethods.add(method)
            for (crossUnitEdge in crossUnitCallees[method].orEmpty()) {
                val caller = graph.methodOf(crossUnitEdge.u.statement)
                val (entryPoints, upGraph) = context.summaries[caller]!!.crossUnitCallees[crossUnitEdge.u]!!
                val newValue = result.mergeWithUpGraph(upGraph, entryPoints)
                if (result != newValue) {
                    result = newValue
                    for (nMethod in upGraph.methods) {
                        if (nMethod !in addedMethods) {
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
            devirtualizer.findPossibleCallees(inst).forEach {
                result.add(it)
            }
        }
        return result
    }

    private fun internalAddStart(method: JcMethod) {
        if (method !in context.summaries) {
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
    }

    override fun addStart(method: JcMethod) {
        initMethods.add(method)
        internalAddStart(method)
    }
}