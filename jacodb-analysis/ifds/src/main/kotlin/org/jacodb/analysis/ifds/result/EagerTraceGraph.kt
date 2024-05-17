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

package org.jacodb.analysis.ifds.result

import org.jacodb.analysis.ifds.domain.Edge
import org.jacodb.analysis.ifds.domain.Reason
import org.jacodb.analysis.ifds.domain.Vertex

data class EagerTraceGraph<Stmt, Fact>(
    override val sink: Vertex<Stmt, Fact>,
    override val sources: Set<Vertex<Stmt, Fact>>,
    override val edges: Map<Vertex<Stmt, Fact>, MutableSet<Vertex<Stmt, Fact>>>,
) : TraceGraph<Stmt, Fact> {
    /**
     * Returns all traces from [sources] to [sink].
     */
    override fun getAllTraces(): Sequence<List<Vertex<Stmt, Fact>>> = sequence {
        for (v in sources) {
            yieldAll(getAllTraces(mutableListOf(v)))
        }
    }

    private fun getAllTraces(
        trace: MutableList<Vertex<Stmt, Fact>>,
    ): Sequence<List<Vertex<Stmt, Fact>>> = sequence {
        val v = trace.last()
        if (v == sink) {
            yield(trace.toList()) // copy list
            return@sequence
        }
        for (u in edges[v].orEmpty()) {
            if (u !in trace) {
                trace.add(u)
                yieldAll(getAllTraces(trace))
                trace.removeLast()
            }
        }
    }
}

fun <Stmt, Fact> IfdsComputationData<Stmt, Fact, *>.buildTraceGraph(
    sink: Vertex<Stmt, Fact>,
    zeroFact: Fact? = null,
): TraceGraph<Stmt, Fact> {
    val sources: MutableSet<Vertex<Stmt, Fact>> = hashSetOf()
    val edges: MutableMap<Vertex<Stmt, Fact>, MutableSet<Vertex<Stmt, Fact>>> = hashMapOf()
    val visited: MutableSet<Pair<Edge<Stmt, Fact>, Vertex<Stmt, Fact>>> = hashSetOf()

    fun addEdge(
        from: Vertex<Stmt, Fact>,
        to: Vertex<Stmt, Fact>,
    ) {
        if (from != to) {
            edges.getOrPut(from) { hashSetOf() }.add(to)
        }
    }

    fun dfs(
        edge: Edge<Stmt, Fact>,
        lastVertex: Vertex<Stmt, Fact>,
        stopAtMethodStart: Boolean,
    ) {
        if (!visited.add(edge to lastVertex)) {
            return
        }

        // Note: loop-edge represents method start
        if (stopAtMethodStart && edge.from == edge.to) {
            addEdge(edge.from, lastVertex)
            return
        }

        val vertex = edge.to
        if (vertex.fact == zeroFact) {
            addEdge(vertex, lastVertex)
            sources.add(vertex)
            return
        }

        for (reason in reasonsByEdge[edge].orEmpty()) {
            when (reason) {
                Reason.Initial -> {
                    sources.add(vertex)
                    addEdge(edge.to, lastVertex)
                }

                is Reason.Sequent -> {
                    val predEdge = reason.edge
                    if (predEdge.to.fact == vertex.fact) {
                        dfs(predEdge, lastVertex, stopAtMethodStart)
                    } else {
                        addEdge(predEdge.to, lastVertex)
                        dfs(predEdge, predEdge.to, stopAtMethodStart)
                    }
                }

                is Reason.CallToReturn -> {
                    val predEdge = reason.edge
                    if (predEdge.to.fact == vertex.fact) {
                        dfs(predEdge, lastVertex, stopAtMethodStart)
                    } else {
                        addEdge(predEdge.to, lastVertex)
                        dfs(predEdge, predEdge.to, stopAtMethodStart)
                    }
                }

                is Reason.CallToStart -> {
                    val predEdge = reason.edge
                    if (!stopAtMethodStart) {
                        addEdge(predEdge.to, lastVertex)
                        dfs(predEdge, predEdge.to, false)
                    }
                }

                is Reason.ExitToReturnSite -> {
                    val predEdge = reason.callerEdge
                    val summaryEdge = reason.edge
                    addEdge(summaryEdge.from, lastVertex)
                    addEdge(predEdge.to, summaryEdge.from)
                    dfs(summaryEdge, summaryEdge.to, true)
                    dfs(predEdge, predEdge.to, stopAtMethodStart)
                }

                is Reason.FromOtherRunner -> {
                    TODO("Reason from other runner is not supported yet")
                }
            }
        }
    }

    for (edge in edgesByEnd[sink].orEmpty()) {
        dfs(edge, edge.to, false)
    }

    return EagerTraceGraph(sink, sources, edges)
}
