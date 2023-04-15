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
data class TaintNode private constructor (val variable: AccessPath?, val activation: JcInst?): DomainFact {
    override val id: SpaceId = NpeAnalyzer
    companion object {
        val ZERO = TaintNode(null, null)

        fun fromPath(variable: AccessPath, activation: JcInst? = null) = TaintNode(variable, activation)
    }

    val activatedCopy: TaintNode
        get() = copy(activation = null)
}