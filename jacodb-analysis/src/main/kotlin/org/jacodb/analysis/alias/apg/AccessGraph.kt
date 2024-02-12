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

package org.jacodb.analysis.alias.apg

import org.jacodb.api.JcField
import org.jacodb.api.cfg.JcLocal
import org.jacodb.api.cfg.JcValue

data class AccessGraph(
    val local: JcLocal,
    val fieldGraph: FieldGraph?,
) {
    override fun toString(): String {
        return "$local" + (fieldGraph?.toString() ?: "")
    }
}

fun accessGraphOf(local: JcLocal) = AccessGraph(local, fieldGraph = null)

fun AccessGraph.isEmpty(): Boolean =
    fieldGraph == null

fun AccessGraph.matches(local: JcValue): Boolean =
    local == this.local

fun AccessGraph.matches(local: JcValue, field: JcField): Boolean =
    local == this.local && fieldGraph?.matches(field) ?: false

fun AccessGraph.substituteLocal(newLocal: JcLocal): AccessGraph =
    copy(local = newLocal)

fun AccessGraph.removeHead(): Set<AccessGraph> {
    requireNotNull(fieldGraph) { "$this doesn't have a field graph, so it's impossible to remove the first field" }
    val fieldGraphs = fieldGraph.removeHead()

    if (fieldGraphs.isEmpty()) {
        return setOf(copy(fieldGraph = null))
    }

    return fieldGraphs.mapTo(hashSetOf()) { copy(fieldGraph = it) }
}

fun AccessGraph.removeTail(): Set<AccessGraph> {
    requireNotNull(fieldGraph) { "$this doesn't have a field graph, so it's impossible to remove the last field" }
    val fieldGraphs = fieldGraph.removeTail()

    if (fieldGraphs.isEmpty()) {
        return setOf(copy(fieldGraph = null))
    }

    return fieldGraphs.mapTo(hashSetOf()) { copy(fieldGraph = it) }
}

fun AccessGraph.prependHead(field: JcField): AccessGraph {
    val newFieldGraph = fieldGraph?.prependHead(field) ?: fieldGraphFrom(field)
    return AccessGraph(local, newFieldGraph)
}

fun AccessGraph.appendTail(field: JcField): AccessGraph {
    val newFieldGraph = fieldGraph?.appendTail(field) ?: fieldGraphFrom(field)
    return AccessGraph(local, newFieldGraph)
}

fun AccessGraph.concatenate(other: AccessGraph): AccessGraph {
    requireNotNull(fieldGraph) { "$this doesn't have a field graph, so it's impossible to concatenate" }
    requireNotNull(other.fieldGraph) { "$other doesn't have a field graph, so it's impossible to concatenate" }

    val newFieldGraph = fieldGraph.concatenate(other.fieldGraph)
    return AccessGraph(local, newFieldGraph)
}