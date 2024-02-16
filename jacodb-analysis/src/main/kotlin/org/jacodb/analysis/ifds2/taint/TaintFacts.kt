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

package org.jacodb.analysis.ifds2.taint

import org.jacodb.analysis.engine.DomainFact
import org.jacodb.analysis.engine.ZEROFact
import org.jacodb.analysis.library.analyzers.NpeTaintNode
import org.jacodb.analysis.library.analyzers.TaintAnalysisNode
import org.jacodb.analysis.library.analyzers.TaintNode
import org.jacodb.analysis.paths.AccessPath
import org.jacodb.taint.configuration.TaintMark

sealed interface TaintFact

object Zero : TaintFact {
    override fun toString(): String = this.javaClass.simpleName
}

data class Tainted(
    val variable: AccessPath,
    val mark: TaintMark,
) : TaintFact {
    constructor(fact: TaintNode) : this(fact.variable, TaintMark(fact.nodeType))
}

fun TaintFact.toDomainFact(): DomainFact = when (this) {
    Zero -> ZEROFact

    is Tainted -> {
        when (mark.name) {
            "NPE" -> NpeTaintNode(variable)
            else -> TaintAnalysisNode(variable, nodeType = mark.name)
        }
    }
}
