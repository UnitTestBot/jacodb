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
import org.jacodb.api.JcMethod
import org.jacodb.api.analysis.ApplicationGraph
import org.jacodb.api.cfg.JcInst

class IFDSResult(
    val graph: ApplicationGraph<JcMethod, JcInst>,
    val pathEdges: List<IFDSEdge<DomainFact>>,
    val summaryEdge: List<IFDSEdge<DomainFact>>,
    val resultFacts: Map<JcInst, Set<DomainFact>>,
    val callToStartEdges: List<IFDSEdge<DomainFact>>,
) {
    /**
     * Given a vertex and a startMethod, returns a stacktrace that may have lead to this vertex
     */
    fun resolvePossibleStackTrace(vertex: IFDSVertex<DomainFact>): List<JcInst> {
        val result = mutableListOf(vertex.statement)
        // TODO: fix
        var curVertex = vertex
        while (curVertex.domainFact != ZEROFact) {
            // TODO: Note that taking not first element may cause to infinite loop in this implementation
            val startVertex = pathEdges.first { it.v == curVertex }.u
            if (startVertex.domainFact == ZEROFact) {
                break
            }
            curVertex = callToStartEdges.first { it.v == startVertex }.u
            result.add(curVertex.statement)
        }
        return result.reversed()
    }
}