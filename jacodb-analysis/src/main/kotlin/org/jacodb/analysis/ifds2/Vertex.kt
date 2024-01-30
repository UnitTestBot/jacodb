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

import org.jacodb.analysis.engine.IfdsVertex
import org.jacodb.analysis.ifds2.taint.TaintFact
import org.jacodb.analysis.ifds2.taint.toDomainFact
import org.jacodb.analysis.ifds2.taint.toFact
import org.jacodb.api.JcMethod
import org.jacodb.api.cfg.JcInst

data class Vertex<out Fact>(
    val statement: JcInst,
    val fact: Fact,
) {
    val method: JcMethod
        get() = statement.location.method

    companion object {
        // constructor
        operator fun invoke(vertex: IfdsVertex): Vertex<TaintFact> {
            return Vertex(vertex.statement, vertex.domainFact.toFact())
        }
    }

    override fun toString(): String = "$statement :: $fact"
}

fun Vertex<TaintFact>.toIfds(): IfdsVertex =
    IfdsVertex(statement, fact.toDomainFact())
