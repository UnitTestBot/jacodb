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
import org.jacodb.analysis.AnalysisResult
import org.jacodb.analysis.VulnerabilityInstance
import org.jacodb.analysis.points2.Devirtualizer
import org.jacodb.api.JcMethod
import org.jacodb.api.analysis.JcApplicationGraph

class AnalysisContext {
    val summaries: MutableMap<JcMethod, IFDSMethodSummary> = mutableMapOf()
}

class IFDSUnitTraverser<UnitType>(
    private val graph: JcApplicationGraph,
    private val analyzer: Analyzer,
    private val unitResolver: UnitResolver<UnitType>,
    private val devirtualizer: Devirtualizer,
    private val ifdsInstanceProvider: IFDSInstanceProvider
) : AnalysisEngine {
    private val context = AnalysisContext()
    private val initMethods: MutableSet<JcMethod> = mutableSetOf()

    private val unitsQueue: MutableSet<UnitType> = mutableSetOf()
    private val foundMethods: MutableMap<UnitType, MutableSet<JcMethod>> = mutableMapOf()
    private val dependsOn: MutableMap<UnitType, Int> = mutableMapOf()
    private val externEdgesByExternMethod: MutableMap<JcMethod, MutableSet<IFDSEdge<DomainFact>>> = mutableMapOf()

    override fun analyze(): AnalysisResult {
        println("${foundMethods.values.sumOf { it.size }}, ${unitsQueue.size}")
        while (unitsQueue.isNotEmpty()) {
            println("${unitsQueue.size} unit(s) left")

            // TODO: do smth smarter here
            val next = unitsQueue.minBy { dependsOn[it]!! }
            unitsQueue.remove(next)

//            val ifdsInstance = IFDSUnitInstance(graph, analyzer, devirtualizer, context, listOf(next))
            val ifdsInstance = ifdsInstanceProvider.createInstance(graph, analyzer, devirtualizer, context, unitResolver, foundMethods[next].orEmpty().toList())
            val results = ifdsInstance.analyze()
            for ((_, summary) in results) {
                for ((inc, outcs) in summary.externCallees) {
                    for (outc in outcs.first) {
                        val calledMethod = outc.statement.location.method
                        val newEdge = IFDSEdge(inc, outc)
                        externEdgesByExternMethod.getOrPut(calledMethod) { mutableSetOf() }.add(newEdge)
                    }
                }
            }
            results.forEach { (method, summary) ->
                context.summaries[method] = summary
            }
        }

        println("HERE")

        // TODO: think about correct filters for overall results
        val vulnerabilities = context.summaries.flatMap { (_, summary) ->
            summary.foundVulnerabilities.vulnerabilities.filter {
                val fullRealisationsGraph = extendRealisationsGraph(it.realisationsGraph)
                fullRealisationsGraph.sources.any {
                    it.statement.location.method in initMethods || it.domainFact == ZEROFact
                }
            }.map { VulnerabilityInstance(it.vulnerabilityType, extendRealisationsGraph(it.realisationsGraph)) }
        }.distinct()

        return AnalysisResult(vulnerabilities)
    }

    private val TaintRealisationsGraph.methods: List<JcMethod>
        get() {
            return (edges.keys.map { it.statement.location.method } + listOf(sink.statement.location.method)).distinct()
        }

    private fun extendRealisationsGraph(graph: TaintRealisationsGraph): TaintRealisationsGraph {
        var result = graph
        val methodQueue: MutableSet<JcMethod> = graph.methods.toMutableSet()
        val addedMethods: MutableSet<JcMethod> = mutableSetOf()
        while (methodQueue.isNotEmpty()) {
            val method = methodQueue.first()
            methodQueue.remove(method)
            addedMethods.add(method)
            for (externCallEdge in externEdgesByExternMethod[method].orEmpty()) {
                val caller = externCallEdge.u.statement.location.method
                val (entryPoints, upGraph) = context.summaries[caller]!!.externCallees[externCallEdge.u]!!
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