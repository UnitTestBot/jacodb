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

class TaintRealisationsGraph(
    val sink: IFDSVertex,
    val sources: Set<IFDSVertex>,
    val edges: Map<IFDSVertex, Set<IFDSVertex>>,
) {

    private fun getAllPaths(curPath: MutableList<IFDSVertex>): Sequence<List<IFDSVertex>> = sequence {
        val v = curPath.last()

        if (v == sink) {
            yield(curPath.toList())
            return@sequence
        }

        for (u in edges[v].orEmpty()) {
            if (u !in curPath) {
                curPath.add(u)
                yieldAll(getAllPaths(curPath))
                curPath.removeLast()
            }
        }
    }

    fun getAllPaths(): Sequence<List<IFDSVertex>> = sequence {
        sources.forEach {
            yieldAll(getAllPaths(mutableListOf(it)))
        }
    }

    fun toVulnerability(vulnerabilityType: String, maxPathsCount: Int = 100): DumpableVulnerabilityInstance {
        return DumpableVulnerabilityInstance(
            vulnerabilityType,
            sources.map { it.statement.toString() },
            sink.statement.toString(),
            getAllPaths().take(maxPathsCount).map { intermediatePoints ->
                intermediatePoints.map { it.statement.toString() }
            }.toList()
        )
    }

    fun mergeWithUpGraph(upGraph: TaintRealisationsGraph, entryPoints: Set<IFDSVertex>): TaintRealisationsGraph {
        val validEntryPoints = entryPoints.intersect(edges.keys).ifEmpty {
            return this
        }

        val newSources = sources + upGraph.sources

        val newEdges = edges.toMutableMap()
        for ((source, dests) in upGraph.edges) {
            newEdges[source] = newEdges.getOrDefault(source, emptySet()) + dests
        }
        newEdges[upGraph.sink] = newEdges.getOrDefault(upGraph.sink, emptySet()) + validEntryPoints
        return TaintRealisationsGraph(sink, newSources, newEdges)
    }
}