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

package org.jacodb.panda.staticvm.utils

import org.jacodb.api.common.cfg.Graph

class OneDirectionGraph<T>(
    val nodes: Collection<T>,
    val successorsGetter: (T) -> Collection<T>,
) : Graph<T>, Collection<T> by nodes.toSet() {
    private val successorsMap = hashMapOf<T, Set<T>>()

    override fun successors(node: T): Set<T> = if (node in nodes) {
        successorsMap.getOrPut(node) { successorsGetter(node).toSet() }
    } else {
        emptySet()
    }

    private val predecessorsMap: Map<T, Set<T>> by lazy {
        nodes.applyFold(hashMapOf<T, HashSet<T>>()) {
            successors(it)
                .also { vs -> check(this@OneDirectionGraph.containsAll(vs)) { "Cannot reverse non-closed relation" } }
                .map { v -> getOrPut(v) { hashSetOf() }.add(it) }
        }
    }

    override fun predecessors(node: T) = reversed.successors(node)

    val reversed: OneDirectionGraph<T> by lazy {
        OneDirectionGraph(nodes) { predecessorsMap[it].orEmpty() }
    }
}
