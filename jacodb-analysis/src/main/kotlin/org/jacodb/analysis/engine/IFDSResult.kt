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
import org.jacodb.api.JcMethod
import org.jacodb.api.analysis.ApplicationGraph
import org.jacodb.api.cfg.JcInst

class IFDSResult(
    val graph: ApplicationGraph<JcMethod, JcInst>,
    val pathEdges: List<IFDSEdge<DomainFact>>,
    val summaryEdges: List<IFDSEdge<DomainFact>>,
    val resultFacts: Map<JcInst, Set<DomainFact>>,
    val pathEdgesPreds: Map<IFDSEdge<DomainFact>, Set<PathEdgePredecessor<DomainFact>>>,
    val summaryEdgeToStartToEndEdges: Map<IFDSEdge<DomainFact>, Set<IFDSEdge<DomainFact>>>
) {
    /**
     * Given a vertex and a startMethod, returns a stacktrace that may have lead to this vertex
     */
    fun resolvePossibleStackTrace(vertex: IFDSVertex<DomainFact>): List<JcInst> {
        val result = mutableListOf(vertex.statement)
        var curVertex = vertex
        while (curVertex.domainFact != ZEROFact) {
            // TODO: Note that taking not first element may cause to infinite loop in this implementation
            val startVertex = pathEdges.first { it.v == curVertex }.u
            if (startVertex.domainFact == ZEROFact) {
                break
            }
            val predEdge = pathEdgesPreds[IFDSEdge(startVertex, startVertex)]
                .orEmpty()
                .first { it.kind == CALL_TO_START }
                .predEdge

            curVertex = predEdge.v
            result.add(curVertex.statement)
        }
        return result.reversed()
    }

    private inner class RealisationsGraphBuilder(private val sink: IFDSVertex<DomainFact>) {
        private val sources: MutableSet<IFDSVertex<DomainFact>> = mutableSetOf()
        private val edges: MutableMap<IFDSVertex<DomainFact>, MutableSet<IFDSVertex<DomainFact>>> = mutableMapOf()
        private val visited: MutableSet<IFDSEdge<DomainFact>> = mutableSetOf()

        private fun addEdge(from: IFDSVertex<DomainFact>, to: IFDSVertex<DomainFact>) {
            if (from != to) {
                edges.getOrPut(from) { mutableSetOf() }.add(to)
            }
        }

        private fun dfs(e: IFDSEdge<DomainFact>, lastVertex: IFDSVertex<DomainFact>, stopAtMethodStart: Boolean) {
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
                        val summaryEdge = IFDSEdge(pred.predEdge.v, v)
                        summaryEdgeToStartToEndEdges[summaryEdge].orEmpty().forEach { startToEndEdge ->
                            addEdge(startToEndEdge.v, lastVertex) // Return to next vertex
                            addEdge(pred.predEdge.v, startToEndEdge.u) // Call to start
                            dfs(startToEndEdge, startToEndEdge.v, true) // Expand summary edge
                            if (startToEndEdge.u.domainFact != ZEROFact) {
                                dfs(pred.predEdge, pred.predEdge.v, stopAtMethodStart) // Continue normal analysis
                            }
                        }
                    }
                    UNKNOWN -> {
                        addEdge(pred.predEdge.v, lastVertex) // Turning point
                        // TODO: ideally, we should analyze the place from which the edge was given to ifds,
                        //  for now we just go to method start
                        dfs(IFDSEdge(pred.predEdge.u, pred.predEdge.u), pred.predEdge.v, stopAtMethodStart)
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

    fun resolveTaintRealisationsGraph(vertex: IFDSVertex<DomainFact>): TaintRealisationsGraph {
        return RealisationsGraphBuilder(vertex).build()
    }
}