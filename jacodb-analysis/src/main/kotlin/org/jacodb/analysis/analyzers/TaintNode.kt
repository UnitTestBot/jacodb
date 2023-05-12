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

package org.jacodb.analysis.analyzers

import org.jacodb.analysis.engine.DomainFact
import org.jacodb.analysis.engine.SpaceId
import org.jacodb.analysis.paths.AccessPath
import org.jacodb.api.cfg.JcInst

/**
 * activation == null <=> activation point is passed
 */
abstract class TaintNode(val variable: AccessPath, val activation: JcInst? = null): DomainFact {
    abstract fun updateActivation(newActivation: JcInst?): TaintNode

    abstract fun moveToOtherPath(newPath: AccessPath): TaintNode

    val activatedCopy: TaintNode
        get() = updateActivation(null)

    override fun toString(): String {
        return "[${id.value}]: $variable, activation point=$activation"
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

class NPETaintNode(variable: AccessPath, activation: JcInst? = null): TaintNode(variable, activation) {
    override fun updateActivation(newActivation: JcInst?): NPETaintNode {
        return NPETaintNode(variable, newActivation)
    }

    override fun moveToOtherPath(newPath: AccessPath): TaintNode {
        return NPETaintNode(newPath, activation)
    }

    override val id: SpaceId
        get() = NpeAnalyzer
}

data class UnusedVariableNode(val variable: AccessPath, val initStatement: JcInst): DomainFact {
    override val id: SpaceId
        get() = UnusedVariableAnalyzer
}

class TaintAnalysisNode(variable: AccessPath, activation: JcInst? = null): TaintNode(variable, activation) {
    override fun updateActivation(newActivation: JcInst?): TaintAnalysisNode {
        return TaintAnalysisNode(variable, newActivation)
    }

    override fun moveToOtherPath(newPath: AccessPath): TaintNode {
        return TaintAnalysisNode(newPath, activation)
    }

    override val id: SpaceId
        get() = TaintAnalyzer
}