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

import org.jacodb.api.analysis.JcApplicationGraph
import java.util.concurrent.ConcurrentHashMap

abstract class AbstractAnalyzer(private val graph: JcApplicationGraph) : Analyzer {
    protected val verticesWithTraceGraphNeeded: MutableSet<IfdsVertex> = ConcurrentHashMap.newKeySet()

    abstract val isMainAnalyzer: Boolean

    override fun handleNewEdge(edge: IfdsEdge): List<AnalysisDependentEvent> {
        return if (isMainAnalyzer && edge.v.statement in graph.exitPoints(edge.method)) {
            listOf(NewSummaryFact(SummaryEdgeFact(edge)))
        } else {
            emptyList()
        }
    }

    override fun handleNewCrossUnitCall(fact: CrossUnitCallFact): List<AnalysisDependentEvent> {
        return if (isMainAnalyzer) {
            verticesWithTraceGraphNeeded.add(fact.callerVertex)
            listOf(NewSummaryFact(fact), EdgeForOtherRunnerQuery(IfdsEdge(fact.calleeVertex, fact.calleeVertex)))
        } else {
            emptyList()
        }
    }

    override fun handleIfdsResult(ifdsResult: IfdsResult): List<AnalysisDependentEvent> {
        val traceGraphs = verticesWithTraceGraphNeeded.map {
            ifdsResult.resolveTraceGraph(it)
        }

        return traceGraphs.map {
            NewSummaryFact(TraceGraphFact(it))
        }
    }
}