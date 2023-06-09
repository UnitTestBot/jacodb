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
import org.jacodb.analysis.points2.Devirtualizer
import org.jacodb.api.JcMethod
import org.jacodb.api.analysis.JcApplicationGraph

class AnalysisContext {
    val summaries: MutableMap<JcMethod, IFDSMethodSummary> = mutableMapOf()
//    val pendingMethods: MutableMap<JcMethod, MutableSet<JcMethod>> = mutableMapOf()
}

class IFDSUnitTraverser<UnitType>(
    private val graph: JcApplicationGraph,
    private val analyzer: Analyzer,
    private val unitResolver: UnitResolver<UnitType>,
    private val devirtualizer: Devirtualizer,
) : AnalysisEngine {
    private val context = AnalysisContext()
    private val initMethods: MutableSet<JcMethod> = mutableSetOf()

    private val unitsQueue: MutableSet<UnitType> = mutableSetOf()
    private val foundMethods: MutableMap<UnitType, MutableSet<JcMethod>> = mutableMapOf()
    private val dependsOn: MutableMap<UnitType, Int> = mutableMapOf()

    override fun analyze(): AnalysisResult {
        println("${foundMethods.values.sumOf { it.size }}, ${unitsQueue.size}")
        while (unitsQueue.isNotEmpty()) {
            println("${unitsQueue.size} units left")
            // TODO: do smth smarter here
            val next = unitsQueue.minBy { dependsOn[it]!! }
            unitsQueue.remove(next)

//            val ifdsInstance = IFDSUnitInstance(graph, analyzer, devirtualizer, context, listOf(next))
            val ifdsInstance = NewTaintAnalysisWithPointsTo(graph, analyzer, devirtualizer, context, unitResolver, foundMethods[next].orEmpty().toList())
            val results = ifdsInstance.ifdsResults()

//            for ((caller, calleeFacts) in summary.externCallees) {
//                val upGraph = fullResults.resolveTaintRealisationsGraph(caller)
//                for (calledMethod in graph.callees(caller.statement)) {
//                    context.summaries[calledMethod]?.let { calledMethodSummary->
//                        calledMethodSummary.foundVulnerabilities.vulnerabilities.map {
//                            it.realisationsGraph.mergeWithUpGraph(upGraph, calleeFacts)
//                        }
//                    }
//                }
//            }
            results.forEach { (method, summary) ->
                context.summaries[method] = summary
            }
        }

        // TODO: merge realisations graph here or smth like this
        // TODO: think about correct filters for overall results
        val vulnerabilities = context.summaries.flatMap { (_, summary) ->
            summary.foundVulnerabilities.vulnerabilities.filter { true ||
                it.realisationsGraph.sources.any {
                    it.statement.location.method in initMethods || it.domainFact == ZEROFact
                }

            }
        }.distinct()

        return AnalysisResult(vulnerabilities)
    }

    private fun getAllDependencies(method: JcMethod): Set<JcMethod> {
        val result = mutableSetOf<JcMethod>()
        for (inst in method.flowGraph().instructions) {
            devirtualizer.findPossibleCallees(inst).forEach {
                result.add(it)
            }
//            graph.callees(inst).forEach {
//                result.add(it)
//            }
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