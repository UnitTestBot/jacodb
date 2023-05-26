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

import org.jacodb.analysis.VulnerabilityInstance

data class TaintRealisationsGraph(
    val sink: IFDSVertex<DomainFact>,
    val sources: Set<IFDSVertex<DomainFact>>,
    val edges: Map<IFDSVertex<DomainFact>, Set<IFDSVertex<DomainFact>>>,
) {
    private fun getAllPaths(curPath: MutableList<IFDSVertex<DomainFact>>): Sequence<List<IFDSVertex<DomainFact>>> = sequence {
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

    private fun getAllPaths(): Sequence<List<IFDSVertex<DomainFact>>> = sequence {
        sources.forEach {
            yieldAll(getAllPaths(mutableListOf(it)))
        }
    }

    fun toVulnerability(vulnerabilityType: String, maxPathsCount: Int = 100): VulnerabilityInstance {
        return VulnerabilityInstance(
            vulnerabilityType,
            sources.map { it.statement.toString() },
            sink.statement.toString(),
            getAllPaths().take(maxPathsCount).map { intermediatePoints ->
                intermediatePoints.map { it.statement.toString() }
            }.toList()
        )
    }
}