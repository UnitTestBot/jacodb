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
import org.jacodb.analysis.paths.AccessPath
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.cfg.JcReturnInst

/**
 * Abstract implementation for [DomainFact] that can be used for analysis where dataflow facts correlate with
 * variables/values
 *
 * @property activation is the activation point, as described in ARF14. Null value means that activation point was
 * passed (so, for analyses that do not use backward runner to taint aliases, [activation] will always be null).
 */
abstract class TaintNode(val variable: AccessPath, val activation: JcInst? = null): DomainFact {
    protected abstract val nodeType: String

    abstract fun updateActivation(newActivation: JcInst?): TaintNode

    abstract fun moveToOtherPath(newPath: AccessPath): TaintNode

    val activatedCopy: TaintNode
        get() = updateActivation(null)

    override fun toString(): String {
        return "[$nodeType]: $variable, activation point=$activation"
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

class NpeTaintNode(variable: AccessPath, activation: JcInst? = null): TaintNode(variable, activation) {
    override val nodeType: String
        get() = "NPE"

    override fun updateActivation(newActivation: JcInst?): NpeTaintNode {
        return NpeTaintNode(variable, newActivation)
    }

    override fun moveToOtherPath(newPath: AccessPath): TaintNode {
        return NpeTaintNode(newPath, activation)
    }
}

/**
 * Implementation for [DomainFact] that is used for slicing analyses to indicate the importance
 * of a variable's value and dataflow dependencies.
 *
 * @property isFromReturn is the [JcReturnInst] instruction that is the primary source of the dataflow dependency
 * described by this [DomainFact] (early exit before some important instructions).
 * Null value means that the primary source of the dataflow dependency is one of the sink instructions.
 */
class SliceDataFlowNode(variable: AccessPath, activation: JcInst? = null, val isFromReturn: JcInst? = null): TaintNode(variable, activation) {
    override val nodeType: String
        get() = "Slicing"

    override fun updateActivation(newActivation: JcInst?): SliceDataFlowNode {
        return SliceDataFlowNode(variable, newActivation, isFromReturn)
    }

    override fun moveToOtherPath(newPath: AccessPath): TaintNode {
        return SliceDataFlowNode(newPath, activation, isFromReturn)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as SliceDataFlowNode

        if (isFromReturn != other.isFromReturn) return false
        if (nodeType != other.nodeType) return false
        if (variable != other.variable) return false

        return true
    }

}

/**
 * Implementation for [DomainFact] that is used for slicing analyses to indicate the importance
 * of some instructions and control-flow dependencies.
 *
 * @property inst is the [JcInst] instruction that is activation point for this fact. The control-flow
 * fact just passed on using flow functions until this instruction is reached.
 * Null value means that the control-flow fact is active.
 *
 * @property isFromReturn is the [JcReturnInst] instruction that is the primary source of the control-flow dependency
 * described by this [DomainFact] (early exit before some important instructions).
 * Null value means that the primary source of the dataflow dependency is one of the sink instructions.
 */
data class SliceControlFlowNode(val inst: JcInst? = null, val isFromReturn: JcInst? = null): DomainFact

data class UnusedVariableNode(val variable: AccessPath, val initStatement: JcInst): DomainFact

class TaintAnalysisNode(variable: AccessPath, activation: JcInst? = null): TaintNode(variable, activation) {
    override val nodeType: String
        get() = "Taint analysis"

    override fun updateActivation(newActivation: JcInst?): TaintAnalysisNode {
        return TaintAnalysisNode(variable, newActivation)
    }

    override fun moveToOtherPath(newPath: AccessPath): TaintNode {
        return TaintAnalysisNode(newPath, activation)
    }
}