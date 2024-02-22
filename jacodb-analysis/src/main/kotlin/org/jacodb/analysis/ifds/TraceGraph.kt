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

package org.jacodb.analysis.ifds

import org.jacodb.api.common.CommonMethod
import org.jacodb.api.common.cfg.CommonInst

data class TraceGraph<Fact, Method, Statement>(
    val sink: Vertex<Fact, Method, Statement>,
    val sources: MutableSet<Vertex<Fact, Method, Statement>>,
    val edges: MutableMap<Vertex<Fact, Method, Statement>, MutableSet<Vertex<Fact, Method, Statement>>>,
    val unresolvedCrossUnitCalls: Map<Vertex<Fact, Method, Statement>, Set<Vertex<Fact, Method, Statement>>>,
) where Method : CommonMethod<Method, Statement>,
        Statement : CommonInst<Method, Statement> {

    /**
     * Returns all traces from [sources] to [sink].
     */
    fun getAllTraces(): Sequence<List<Vertex<Fact, Method, Statement>>> = sequence {
        for (v in sources) {
            yieldAll(getAllTraces(mutableListOf(v)))
        }
    }

    private fun getAllTraces(
        trace: MutableList<Vertex<Fact, Method, Statement>>,
    ): Sequence<List<Vertex<Fact, Method, Statement>>> = sequence {
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

    /**
     * Merges [upGraph] into this graph.
     */
    fun mergeWithUpGraph(
        upGraph: TraceGraph<Fact, Method, Statement>,
        entryPoints: Set<Vertex<Fact, Method, Statement>>,
    ) {
        sources.addAll(upGraph.sources)

        for (edge in upGraph.edges) {
            edges.getOrPut(edge.key) { hashSetOf() }.addAll(edge.value)
        }

        edges.getOrPut(upGraph.sink) { hashSetOf() }.addAll(entryPoints)
    }
}
