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

import org.jacodb.api.core.CoreMethod
import org.jacodb.api.core.analysis.ApplicationGraph
import org.jacodb.api.core.cfg.CoreInst
import org.jacodb.api.core.cfg.CoreInstLocation
import java.util.concurrent.ConcurrentHashMap

/**
 * Handlers of [AbstractAnalyzer] produce some common events like new [SummaryEdgeFact]s, new [CrossUnitCallFact]s, etc.
 * Inheritors may override all these handlers, and also they can extend them by calling the super-method and adding
 * their own event
 *
 * @property verticesWithTraceGraphNeeded For all vertices added to this set,
 * a [TraceGraphFact] will be produced at [handleIfdsResult]
 *
 * @property isMainAnalyzer Iff this property is set to true, handlers will
 * 1. Produce [NewSummaryFact] events with [SummaryEdgeFact]s and [CrossUnitCallFact]s
 * 2. Will produce [EdgeForOtherRunnerQuery] for each cross-unit call
 *
 * Usually this should be set to true for forward analyzers (which are expected to tell anything they found),
 * but in backward analyzers this should be set to false
 */
abstract class AbstractAnalyzer<Method, Location, Statement>(
    private val graph: ApplicationGraph<Method, Statement>
) : Analyzer<Method, Location, Statement>
        where Method : CoreMethod<Statement>,
              Location : CoreInstLocation<Method>,
              Statement : CoreInst<Location, Method, *> {
    protected val verticesWithTraceGraphNeeded: MutableSet<IfdsVertex<Method, Location, Statement>> =
        ConcurrentHashMap.newKeySet()

    abstract val isMainAnalyzer: Boolean

    /**
     * If the edge is start-to-end and [isMainAnalyzer] is true,
     * produces a [NewSummaryFact]  with this summary edge.
     * Otherwise, returns empty list.
     */
    override fun handleNewEdge(edge: IfdsEdge<Method, Location, Statement>): List<AnalysisDependentEvent> {
        return if (isMainAnalyzer && edge.v.statement in graph.exitPoints(edge.method)) {
            listOf(NewSummaryFact(SummaryEdgeFact(edge)))
        } else {
            emptyList()
        }
    }

    /**
     * If [isMainAnalyzer] is set to true, produces a [NewSummaryFact] with given [fact]
     * and also produces [EdgeForOtherRunnerQuery]
     */
    override fun handleNewCrossUnitCall(
        fact: CrossUnitCallFact<Method, Location, Statement>
    ): List<AnalysisDependentEvent> {
        return if (isMainAnalyzer) {
            verticesWithTraceGraphNeeded.add(fact.callerVertex)
            listOf(NewSummaryFact(fact), EdgeForOtherRunnerQuery(IfdsEdge(fact.calleeVertex, fact.calleeVertex)))
        } else {
            emptyList()
        }
    }

    /**
     * Produces trace graphs for all vertices added to [verticesWithTraceGraphNeeded]
     */
    override fun handleIfdsResult(ifdsResult: IfdsResult<Method, Location, Statement>): List<AnalysisDependentEvent> {
        val traceGraphs = verticesWithTraceGraphNeeded.map {
            ifdsResult.resolveTraceGraph(it)
        }

        return traceGraphs.map {
            NewSummaryFact(TraceGraphFact<Method>(it))
        }
    }
}