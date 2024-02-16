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

data class TraceGraph<Fact>(
    val sink: Vertex<Fact>,
    val sources: Set<Vertex<Fact>>,
    val edges: Map<Vertex<Fact>, Set<Vertex<Fact>>>,
) {
    /**
     * Returns all traces from [sources] to [sink].
     */
    fun getAllTraces(): Sequence<List<Vertex<Fact>>> = sequence {
        for (v in sources) {
            yieldAll(getAllTraces(mutableListOf(v)))
        }
    }

    private fun getAllTraces(
        trace: MutableList<Vertex<Fact>>,
    ): Sequence<List<Vertex<Fact>>> = sequence {
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
     * Merges two graphs.
     *
     * [sink] will be chosen from receiver, and edges from both graphs will be merged.
     * Also, all edges from [upGraph]'s sink to [entryPoints] will be added
     * (these are edges "connecting" [upGraph] with receiver).
     *
     * Informally, this method extends receiver's traces from one side using [upGraph].
     */
    fun mergeWithUpGraph(
        upGraph: TraceGraph<Fact>,
        entryPoints: Set<Vertex<Fact>>,
    ): TraceGraph<Fact> {
        val validEntryPoints = entryPoints.intersect(edges.keys)
        if (validEntryPoints.isEmpty()) return this

        val newSources = sources + upGraph.sources
        val newEdges = edges.toMutableMap()
        for ((source, destinations) in upGraph.edges) {
            newEdges[source] = newEdges.getOrDefault(source, emptySet()) + destinations
        }
        newEdges[upGraph.sink] = newEdges.getOrDefault(upGraph.sink, emptySet()) + validEntryPoints
        return TraceGraph(sink, newSources, newEdges)
    }

    companion object {
        fun <Fact> bySink(
            sink: Vertex<Fact>,
        ): TraceGraph<Fact> {
            return TraceGraph(sink, setOf(sink), emptyMap())
        }
    }
}
