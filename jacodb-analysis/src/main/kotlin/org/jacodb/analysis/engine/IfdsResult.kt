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
import org.jacodb.analysis.engine.PathEdgePredecessorKind.CALL_TO_START
import org.jacodb.analysis.engine.PathEdgePredecessorKind.NO_PREDECESSOR
import org.jacodb.analysis.engine.PathEdgePredecessorKind.SEQUENT
import org.jacodb.analysis.engine.PathEdgePredecessorKind.THROUGH_SUMMARY
import org.jacodb.analysis.engine.PathEdgePredecessorKind.UNKNOWN
import org.jacodb.api.cfg.JcInst

class IfdsResult(
    val pathEdges: List<IfdsEdge>,
    val resultFacts: Map<JcInst, Set<DomainFact>>,
    val pathEdgesPreds: Map<IfdsEdge, Set<PathEdgePredecessor>>,
    val summaryEdgeToStartToEndEdges: Map<IfdsEdge, Set<IfdsEdge>>,
    val crossUnitCallees: Map<IfdsVertex, Set<IfdsVertex>>
) {
    private inner class RealisationsGraphBuilder(private val sink: IfdsVertex) {
        private val sources: MutableSet<IfdsVertex> = mutableSetOf()
        private val edges: MutableMap<IfdsVertex, MutableSet<IfdsVertex>> = mutableMapOf()
        private val visited: MutableSet<IfdsEdge> = mutableSetOf()

        private fun addEdge(from: IfdsVertex, to: IfdsVertex) {
            if (from != to) {
                edges.getOrPut(from) { mutableSetOf() }.add(to)
            }
        }

        private fun dfs(e: IfdsEdge, lastVertex: IfdsVertex, stopAtMethodStart: Boolean) {
            if (e in visited) {
                return
            }

            visited.add(e)

            if (stopAtMethodStart && e.u == e.v) {
                addEdge(e.u, lastVertex)
                return
            }

            val (_, v) = e
            if (v.domainFact == ZEROFact) {
                addEdge(v, lastVertex)
                sources.add(v)
                return
            }

            for (pred in pathEdgesPreds[e].orEmpty()) {
                when (pred.kind) {
                    CALL_TO_START -> {
                        if (!stopAtMethodStart) {
                            addEdge(pred.predEdge.v, lastVertex)
                            dfs(pred.predEdge, pred.predEdge.v, false)
                        }
                    }
                    SEQUENT -> {
                        if (pred.predEdge.v.domainFact == v.domainFact) {
                            dfs(pred.predEdge, lastVertex, stopAtMethodStart)
                        } else {
                            addEdge(pred.predEdge.v, lastVertex)
                            dfs(pred.predEdge, pred.predEdge.v, stopAtMethodStart)
                        }
                    }
                    THROUGH_SUMMARY -> {
                        val summaryEdge = IfdsEdge(pred.predEdge.v, v)
                        summaryEdgeToStartToEndEdges[summaryEdge]?.forEach { startToEndEdge ->
                            addEdge(startToEndEdge.v, lastVertex) // Return to next vertex
                            addEdge(pred.predEdge.v, startToEndEdge.u) // Call to start
                            dfs(startToEndEdge, startToEndEdge.v, true) // Expand summary edge
                            dfs(pred.predEdge, pred.predEdge.v, stopAtMethodStart) // Continue normal analysis
                        }
                    }
                    UNKNOWN -> {
                        addEdge(pred.predEdge.v, lastVertex) // Turning point
                        // TODO: ideally, we should analyze the place from which the edge was given to ifds,
                        //  for now we just go to method start
                        dfs(IfdsEdge(pred.predEdge.u, pred.predEdge.u), pred.predEdge.v, stopAtMethodStart)
                    }
                    NO_PREDECESSOR -> {
                        sources.add(v)
                        addEdge(pred.predEdge.v, lastVertex)
                    }
                }
            }
        }

        fun build(): TaintRealisationsGraph {
            val initEdges = pathEdges.filter { it.v == sink }
            initEdges.forEach {
                dfs(it, it.v, false)
            }
            return TaintRealisationsGraph(sink, sources, edges)
        }
    }

    fun resolveTaintRealisationsGraph(vertex: IfdsVertex): TaintRealisationsGraph {
        return RealisationsGraphBuilder(vertex).build()
    }
}