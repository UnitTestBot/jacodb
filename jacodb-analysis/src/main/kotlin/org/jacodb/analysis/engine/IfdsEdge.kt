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

/**
 * Represents a directed (from [from] to [to]) edge between two ifds vertices
 */
data class IfdsEdge(
    val from: IfdsVertex,
    val to: IfdsVertex,
) {
    init {
        require(from.method == to.method)
    }

    var reason: IfdsEdge? = null

    val method: JcMethod
        get() = from.method
}

sealed interface PredecessorKind {
    object NoPredecessor : PredecessorKind
    object Unknown : PredecessorKind
    object Sequent : PredecessorKind
    object CallToStart : PredecessorKind
    class ThroughSummary(val summaryEdge: IfdsEdge) : PredecessorKind
}

/**
 * Contains info about predecessor of path edge.
 * Used mainly to restore traces.
 */
data class PathEdgePredecessor(
    val predEdge: IfdsEdge,
    val kind: PredecessorKind,
)
