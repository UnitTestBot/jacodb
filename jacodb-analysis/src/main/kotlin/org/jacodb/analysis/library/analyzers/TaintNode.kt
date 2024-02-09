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

package org.jacodb.analysis.library.analyzers

import org.jacodb.analysis.engine.DomainFact
import org.jacodb.analysis.ifds2.taint.Tainted
import org.jacodb.analysis.paths.AccessPath
import org.jacodb.api.cfg.JcInst

/**
 * Abstract implementation for [DomainFact] that can be used for analysis where dataflow facts correlate with
 * variables/values
 *
 * @property activation is the activation point, as described in ARF14. Null value means that activation point was
 * passed (so, for analyses that do not use backward runner to taint aliases, [activation] will always be null).
 */
abstract class TaintNode(
    val variable: AccessPath,
    val activation: JcInst? = null,
) : DomainFact {
    internal abstract val nodeType: String

    abstract fun updateActivation(newActivation: JcInst?): TaintNode

    abstract fun moveToOtherPath(newPath: AccessPath): TaintNode

    val activatedCopy: TaintNode
        get() = updateActivation(null)

    override fun toString(): String {
        return if (activation != null) {
            "[$nodeType]: $variable, activation=$activation"
        } else {
            "[$nodeType]: $variable"
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TaintNode

        if (variable != other.variable) return false
        if (activation != other.activation) return false

        return true
    }

    override fun hashCode(): Int {
        var result = variable.hashCode()
        result = 31 * result + (activation?.hashCode() ?: 0)
        return result
    }
}

class NpeTaintNode(
    variable: AccessPath,
    activation: JcInst? = null,
) : TaintNode(variable, activation) {
    override val nodeType: String
        get() = "NPE"

    override fun updateActivation(newActivation: JcInst?): NpeTaintNode {
        return NpeTaintNode(variable, newActivation)
    }

    override fun moveToOtherPath(newPath: AccessPath): NpeTaintNode {
        return NpeTaintNode(newPath, activation)
    }
}

data class UnusedVariableNode(
    val variable: AccessPath,
    val initStatement: JcInst,
) : DomainFact

class TaintAnalysisNode(
    variable: AccessPath,
    activation: JcInst? = null,
    override val nodeType: String, // = "Taint analysis"
) : TaintNode(variable, activation) {

    constructor(fact: Tainted) : this(fact.variable, nodeType = fact.mark.name)

    override fun updateActivation(newActivation: JcInst?): TaintAnalysisNode {
        return TaintAnalysisNode(variable, newActivation, nodeType)
    }

    override fun moveToOtherPath(newPath: AccessPath): TaintAnalysisNode {
        return TaintAnalysisNode(newPath, activation, nodeType)
    }
}
