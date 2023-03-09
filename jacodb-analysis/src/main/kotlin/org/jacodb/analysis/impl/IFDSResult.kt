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

package org.jacodb.analysis.impl

import org.jacodb.api.analysis.ApplicationGraph

data class IFDSResult<Method, Statement, D>(
    private val graph: ApplicationGraph<Method, Statement>,
    val pathEdges: List<Edge<Statement, D>>,
    val summaryEdge: List<Edge<Statement, D>>,
    val resultFacts: Map<Statement, Set<D>>,
    val callToStartEdges: List<Edge<Statement, D>>,
) {
    /**
     * Given a vertex and a startMethod, returns a stacktrace that may have lead to this vertex
     */
    fun resolvePossibleStackTrace(vertex: Vertex<Statement, D>, startMethod: Method): List<Statement> {
        val result = mutableListOf(vertex.statement)
        var curVertex = vertex
        while (graph.methodOf(curVertex.statement) != startMethod) {
            // TODO: Note that taking not first element may cause to infinite loop in this implementation
            val startVertex = pathEdges.first { it.v == curVertex }.u
            curVertex = callToStartEdges.first { it.v == startVertex }.u
            result.add(curVertex.statement)
        }
        return result.reversed()
    }
}