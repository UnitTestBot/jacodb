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

import org.jacodb.api.core.cfg.Graph

data class SimpleDirectedGraph<T>(
    val nodes: MutableSet<T> = mutableSetOf(),
    private val predecessorsMap: MutableMap<T, MutableSet<T>> = mutableMapOf(),
    private val successorsMap: MutableMap<T, MutableSet<T>> = mutableMapOf()
) : Graph<T> {
    override fun predecessors(node: T): Set<T> = predecessorsMap.getOrDefault(node, emptySet())

    override fun successors(node: T): Set<T> = successorsMap.getOrDefault(node, emptySet())

    fun withNode(node: T): SimpleDirectedGraph<T> {
        nodes.add(node)
        return this
    }

    fun withEdge(from: T, to: T): SimpleDirectedGraph<T> {
        nodes.add(from)
        nodes.add(to)
        predecessorsMap.getOrPut(to, ::mutableSetOf).add(from)
        successorsMap.getOrPut(from, ::mutableSetOf).add(to)
        return this
    }

    private fun <K> Collection<K>.intersectMutable(other: Collection<K>) =
        toSet().intersect(other.toSet()).toMutableSet()

    fun induced(subset: Collection<T>) = SimpleDirectedGraph(
        nodes.intersectMutable(subset),
        predecessorsMap.mapNotNull { (key, value) -> if (subset.contains(key)) key to subset.intersectMutable(value) else null }
            .toMap().toMutableMap(),
        successorsMap.mapNotNull { (key, value) -> if (subset.contains(key)) key to subset.intersectMutable(value) else null }
            .toMap().toMutableMap()
    )
    fun weaklyConnectedComponents(): List<SimpleDirectedGraph<T>> = components(nodes) {
        predecessors(it) + successors(it)
    }.map(::induced)

    companion object {
        fun <T> union(lhs: SimpleDirectedGraph<T>, rhs: SimpleDirectedGraph<T>) = SimpleDirectedGraph(
            lhs.nodes.plus(rhs.nodes).toMutableSet(),
            lhs.predecessorsMap.plus(rhs.predecessorsMap).toMutableMap(),
            lhs.successorsMap.plus(rhs.successorsMap).toMutableMap()
        )
    }

    override fun iterator(): Iterator<T> = nodes.asIterable().iterator()
}

fun <T> search(start: T, successors: (T) -> List<T>, visitor: (T) -> Unit): Set<T> {
    val visited = hashSetOf<T>()
    fun dfs(node: T) = node.takeIf(visited::add)?.also(visitor)?.let { successors(it).forEach(::dfs) }
    dfs(start)
    return visited
}

fun <T> components(starts: Iterable<T>, successors: (T) -> Collection<T>): List<Set<T>> {
    val visited = hashSetOf<T>()
    return starts.mapNotNull { start ->
        if (start !in visited) search(start, { successors(it).filter { it !in visited } }, visited::add)
        else null
    }
}

/**
 * Applies [operation] to [initial] object with all arguments from [Iterable] consecutively
 * */
fun <T, A> Iterable<T>.applyFold(initial: A, operation: A.(T) -> Unit) =
    fold(initial) { acc, elem -> acc.apply { operation(elem) } }

fun <T> reachable(starts: Iterable<T>, successors: (T) -> Collection<T>): Set<T> =
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

    val nodes = iterator().asSequence().toSet()
    nodes.forEach { if (it !in visited) dfs(it) }
    return order.reversed()
}
fun <T> Graph<T>.inTopsortOrder(): List<T>? {
    val visited = hashSetOf<T>()
    val order = mutableListOf<T>()
    val stack = hashSetOf<T>()
    var foundCycle = false

    fun dfs(node: T) {
        stack.add(node)
        visited.add(node)
        successors(node).forEach {
            if (it in stack)
                foundCycle = true
            if (it !in visited)
                dfs(it)
        }
        stack.remove(node)
        order.add(node)
    }

    val nodes = iterator().asSequence().toHashSet()
    nodes.forEach { if (it !in visited) dfs(it) }
    return if (foundCycle) null else order.reversed().filter { it in nodes }
}

fun <T> Graph<T>.SCCs(): OneDirectionGraph<Set<T>> {
    val components = components(rpo(), this::predecessors)
    val colorMap = components.applyFold(hashMapOf<T, Set<T>>()) {nodes ->
        nodes.forEach { put(it, nodes) }
    }
    return OneDirectionGraph(components) { nodes ->
        nodes.flatMap(this::successors).map(colorMap::get).requireNoNulls()
            .filterNot { it == nodes }.toSet()
    }
}

fun <K, V> Map<K, V?>.removeNulls(): Map<K, V> = entries.filterIsInstance<Map.Entry<K, V>>().associate { (k, v) -> k to v }

fun <T, V> Graph<T>.runDP(transition: (T, Map<T, V>) -> V): Map<T, V> {
    val tsOrder = requireNotNull(inTopsortOrder()) { "Cannot run DP because of cycle found" }
    return tsOrder.reversed().applyFold(hashMapOf()) {v ->
        put(v, transition.invoke(v, successors(v).associateWith(this::get).removeNulls()))
    }
}
