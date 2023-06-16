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

import org.jacodb.analysis.AnalysisResult
import org.jacodb.analysis.VulnerabilityInstance
import org.jacodb.analysis.engine.PathEdgePredecessorKind.NO_PREDECESSOR
import org.jacodb.analysis.engine.PathEdgePredecessorKind.SEQUENT
import org.jacodb.analysis.engine.PathEdgePredecessorKind.THROUGH_SUMMARY
import org.jacodb.analysis.points2.Devirtualizer
import org.jacodb.api.JcMethod
import org.jacodb.api.analysis.ApplicationGraph
import org.jacodb.api.cfg.JcInst
import java.util.*

class IfdsUnitInstance<UnitType>(
    private val graph: ApplicationGraph<JcMethod, JcInst>,
    private val analyzer: Analyzer,
    private val devirtualizer: Devirtualizer,
    private val context: AnalysisContext,
    private val unitResolver: UnitResolver<UnitType>,
    private val unit: UnitType
): IfdsInstance {

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
    private val workList: Queue<IfdsEdge> = LinkedList()
    private val summaryEdgeToStartToEndEdges: MutableMap<IfdsEdge, MutableSet<IfdsEdge>> = mutableMapOf()
    private val callSitesOf: MutableMap<IfdsVertex, MutableSet<IfdsEdge>> = mutableMapOf()
    private val pathEdgesPreds: MutableMap<IfdsEdge, MutableSet<PathEdgePredecessor>> = mutableMapOf()
    private val crossUnitCallees: MutableMap<IfdsVertex, MutableSet<IfdsVertex>> = mutableMapOf()

    private val flowSpace get() = analyzer.flowFunctions

    private val listeners: MutableList<IfdsInstanceListener> = mutableListOf()

    fun addListener(listener: IfdsInstanceListener) = listeners.add(listener)

    override fun addStart(method: JcMethod) {
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
            workList.add(e)
            val isNew =
                pred.kind != SEQUENT && pred.kind != PathEdgePredecessorKind.UNKNOWN || pred.predEdge.v.domainFact != e.v.domainFact
            val predInst =
                pred.predEdge.v.statement.takeIf { it != e.v.statement && it.location.method == e.v.statement.location.method }
            listeners.forEach { it.onPropagate(e, predInst, isNew) }
            return true
        }
        return false
    }

    fun addNewPathEdge(e: IfdsEdge): Boolean {
        return propagate(e, PathEdgePredecessor(e, PathEdgePredecessorKind.UNKNOWN))
    }

    private val JcMethod.isExtern: Boolean
        get() = unitResolver.resolve(this) != unit

    fun run() {
        while (!workList.isEmpty()) {
            val curEdge = workList.poll()
            val (u, v) = curEdge
            val (n, d2) = v

            val callees = devirtualizer.findPossibleCallees(n).toList()
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
                                val exitVertexes = if (callee.isExtern) {
                                    context.summaries[callee]?.factsAtExits?.get(sVertex).orEmpty()
                                } else {
                                    startToEndEdges.getByStart(sVertex).map { it.v }
                                }

                                for ((exitStatement, eFact) in exitVertexes) {
                                    val finalFacts = flowSpace.obtainExitToReturnSiteFlowFunction(n, returnSite, exitStatement).compute(eFact)
                                    for (finalFact in finalFacts) {
                                        val summaryEdge = IfdsEdge(v, IfdsVertex(returnSite, finalFact))
                                        val startToEndEdge = IfdsEdge(IfdsVertex(sPoint, sFact), IfdsVertex(exitStatement, eFact))
                                        val newEdge = IfdsEdge(u, IfdsVertex(returnSite, finalFact))
                                        summaryEdgeToStartToEndEdges.getOrPut(summaryEdge) { mutableSetOf() }.add(startToEndEdge)
                                        propagate(newEdge, PathEdgePredecessor(curEdge, THROUGH_SUMMARY))
                                    }
                                }

                                if (callee.isExtern) {
                                    crossUnitCallees.getOrPut(v) { mutableSetOf() }.add(sVertex)
                                } else {
                                    callSitesOf.getOrPut(sVertex) { mutableSetOf() }.add(curEdge)
                                    val nextEdge = IfdsEdge(sVertex, sVertex)
                                    propagate(nextEdge, PathEdgePredecessor(curEdge, PathEdgePredecessorKind.CALL_TO_START))
                                }
                            }
                        }
                    }
                }
            } else {
                if (n in graph.exitPoints(graph.methodOf(n))) {
                    listeners.forEach { it.onExitPoint(curEdge) }
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
        }
    }

    private val fullResults: IfdsResult by lazy {
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

    override fun analyze(): Map<JcMethod, IfdsMethodSummary> {
        // TODO: rewrite this method cleaner
        run()

        val methods = fullResults.pathEdges.map { graph.methodOf(it.u.statement) }.distinct()

        val factsAtExits = mutableMapOf<JcMethod, MutableMap<IfdsVertex, MutableSet<IfdsVertex>>>()
        for (pathEdge in pathEdges.getAll()) {
            val method = graph.methodOf(pathEdge.u.statement)
            if (pathEdge.v.statement in graph.exitPoints(method)) {
                factsAtExits.getOrPut(method) { mutableMapOf() }.getOrPut(pathEdge.u) { mutableSetOf() }.add(pathEdge.v)
            }
        }

        val relevantVulnerabilities = mutableMapOf<JcMethod, MutableList<VulnerabilityInstance>>()
        analyzer.calculateSources(fullResults).vulnerabilities.forEach {
            relevantVulnerabilities.getOrPut(graph.methodOf(it.realisationsGraph.sink.statement)) { mutableListOf() }
                .add(it)
        }

        val sortedCrossUnitCallees = mutableMapOf<JcMethod, MutableMap<IfdsVertex, CalleeInfo>>()
        crossUnitCallees.forEach { (callVertex, sVertexes) ->
            val method = graph.methodOf(callVertex.statement)
            sortedCrossUnitCallees.getOrPut(method) { mutableMapOf() }[callVertex] = CalleeInfo(
                sVertexes,
                fullResults.resolveTaintRealisationsGraph(callVertex)
            )
        }
        return methods.associateWith {
            IfdsMethodSummary(
                factsAtExits[it].orEmpty(),
                sortedCrossUnitCallees[it].orEmpty(),
                AnalysisResult(relevantVulnerabilities[it].orEmpty())
            )
        }
    }

    companion object : IfdsInstanceProvider {
        override fun <UnitType> createInstance(
            graph: ApplicationGraph<JcMethod, JcInst>,
            analyzer: Analyzer,
            devirtualizer: Devirtualizer,
            context: AnalysisContext,
            unitResolver: UnitResolver<UnitType>,
            unit: UnitType
        ): IfdsInstance {
            return IfdsUnitInstance(graph, analyzer, devirtualizer, context, unitResolver, unit)
        }
    }
}