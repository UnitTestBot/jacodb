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

package org.jacodb.analysis.ifds2

import org.jacodb.analysis.engine.IfdsEdge
import org.jacodb.analysis.ifds2.taint.TaintFact
import org.jacodb.api.JcMethod

data class Edge<out Fact>(
    val from: Vertex<Fact>,
    val to: Vertex<Fact>,
) {
    init {
        require(from.method == to.method)
    }

    val method: JcMethod
        get() = from.method

    companion object {
        // constructor
        operator fun invoke(edge: IfdsEdge): Edge<TaintFact> {
            return Edge(Vertex(edge.from), Vertex(edge.to))
        }
    }

    override fun toString(): String = "${method.name}: $from --> $to"
}

fun Edge<TaintFact>.toIfds(): IfdsEdge = IfdsEdge(from.toIfds(), to.toIfds())

sealed class Reason {

    object Initial : Reason()

    object External : Reason()

    data class Sequent<Fact>(
        val edge: Edge<Fact>,
    ) : Reason()

    data class CallToStart<Fact>(
        val edge: Edge<Fact>,
    ) : Reason()

    data class ThroughSummary<Fact>(
        val edge: Edge<Fact>,
        val summaryEdge: Edge<Fact>,
    ) : Reason()

}
