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

import org.jacodb.analysis.DumpableVulnerabilityInstance

/**
 * A directed graph with selected [sink] and [sources], where each path from one of [sources] to [sink] is a trace.
 *
 * @property sink is usually some interesting vertex that we want to reach (e.g. vertex that produces vulnerability)
 * @property sources are the entry points, e.g. the vertices with [ZEROFact] or method starts
 */
data class TraceGraph(
    val sink: IfdsVertex,
    val sources: Set<IfdsVertex>,
    val edges: Map<IfdsVertex, Set<IfdsVertex>>,
) {

    private fun getAllTraces(curTrace: MutableList<IfdsVertex>): Sequence<List<IfdsVertex>> = sequence {
        val v = curTrace.last()

        if (v == sink) {
            yield(curTrace.toList())
            return@sequence
        }

        for (u in edges[v].orEmpty()) {
            if (u !in curTrace) {
                curTrace.add(u)
                yieldAll(getAllTraces(curTrace))
                curTrace.removeLast()
            }
        }
    }

    /**
     * Returns a sequence with all traces from [sources] to [sink]
     */
    fun getAllTraces(): Sequence<List<IfdsVertex>> = sequence {
        sources.forEach {
            yieldAll(getAllTraces(mutableListOf(it)))
        }
    }

    fun toVulnerability(vulnerabilityType: String, maxTracesCount: Int = 100): DumpableVulnerabilityInstance {
        return DumpableVulnerabilityInstance(
            vulnerabilityType,
            sources.map { it.statement.toString() },
            sink.statement.toString(),
            getAllTraces().take(maxTracesCount).map { intermediatePoints ->
                intermediatePoints.map { it.statement.toString() }
            }.toList()
        )
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
    fun mergeWithUpGraph(upGraph: TraceGraph, entryPoints: Set<IfdsVertex>): TraceGraph {
        val validEntryPoints = entryPoints.intersect(edges.keys).ifEmpty {
            return this
        }

        val newSources = sources + upGraph.sources

        val newEdges = edges.toMutableMap()
        for ((source, dests) in upGraph.edges) {
            newEdges[source] = newEdges.getOrDefault(source, emptySet()) + dests
        }
        newEdges[upGraph.sink] = newEdges.getOrDefault(upGraph.sink, emptySet()) + validEntryPoints
        return TraceGraph(sink, newSources, newEdges)
    }

    companion object {
        fun bySink(sink: IfdsVertex) = TraceGraph(sink, setOf(sink), emptyMap())
    }
}