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

fun <T> search(
    start: T,
    successors: (T) -> Iterable<T>,
    visitor: (T) -> Unit,
): Set<T> {
    val visited = hashSetOf<T>()
    fun dfs(node: T) = node.takeIf(visited::add)?.also(visitor)?.let { successors(it).forEach(::dfs) }
    dfs(start)
    return visited
}

fun <T> components(
    starts: Iterable<T>,
    successors: (T) -> Iterable<T>,
): List<Set<T>> {
    val visited = hashSetOf<T>()
    return starts.mapNotNull { start ->
        if (start !in visited) search(start, { successors(it).filter { it !in visited } }, visited::add)
        else null
    }
}

fun <T> reachable(
    starts: Iterable<T>,
    successors: (T) -> Iterable<T>,
): Set<T> =
    starts.applyFold(hashSetOf()) { start ->
        if (!contains(start))
            addAll(search(start, { successors(it).filterNot(this::contains) }, {}))
    }

fun <T> Graph<T>.rpo(): List<T> {
    val visited = hashSetOf<T>()
    val order = mutableListOf<T>()
    val onStack = hashSetOf<T>()

    fun dfs(node: T) = node.takeIf(visited::add)?.also(onStack::add)?.let {
        successors(it).forEach(::dfs)
        onStack.remove(it)
        order.add(it)
    }

    this.forEach { if (it !in visited) dfs(it) }
    return order.reversed()
}

fun <T> Graph<T>.inTopsortOrder(): List<T>? {
    val visited = hashSetOf<T>()
    val order = mutableListOf<T>()
    val stack = hashSetOf<T>()
    var foundCycle = false

    fun dfs(node: T) {
        if (visited.add(node)) {
            stack.add(node)
            for (next in successors(node)) {
                if (next in stack) {
                    foundCycle = true
                }
                dfs(next)
            }
            stack.remove(node)
            order.add(node)
        }
    }

    val nodes = this.toHashSet()
    for (node in nodes) {
        dfs(node)
    }
    return if (foundCycle) null else order.reversed().filter { it in nodes }
}

fun <T> Graph<T>.SCCs(): OneDirectionGraph<Set<T>> {
    val components = components(rpo()) { predecessors(it) }
    val colorMap = components.applyFold(hashMapOf<T, Set<T>>()) { nodes ->
        nodes.forEach { put(it, nodes) }
    }
    return OneDirectionGraph(components) { nodes ->
        nodes
            .asSequence()
            .flatMap { successors(it) }
            .map { colorMap[it] }
            .requireNoNulls()
            .filterNotTo(hashSetOf()) { it == nodes }
    }
}

fun <K, V> Map<K, V?>.removeNulls(): Map<K, V> =
    entries.filterIsInstance<Map.Entry<K, V>>().associate { (k, v) -> k to v }

fun <T, V> Graph<T>.runDP(transition: (T, Map<T, V>) -> V): Map<T, V> {
    val tsOrder = inTopsortOrder() ?: error("Cannot run DP because of cycle found")
    return tsOrder.reversed().applyFold(hashMapOf()) { v ->
        put(v, transition.invoke(v, successors(v).associateWith(this::get).removeNulls()))
    }
}
