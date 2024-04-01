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

data class SimpleDirectedGraph<T>(
    val nodes: MutableSet<T> = mutableSetOf(),
    private val predecessorsMap: MutableMap<T, MutableSet<T>> = mutableMapOf(),
    private val successorsMap: MutableMap<T, MutableSet<T>> = mutableMapOf(),
) : Graph<T> {
    override fun predecessors(node: T): Set<T> = predecessorsMap[node].orEmpty()
    override fun successors(node: T): Set<T> = successorsMap[node].orEmpty()

    fun withNode(node: T): SimpleDirectedGraph<T> {
        nodes.add(node)
        return this
    }

    fun withEdge(from: T, to: T): SimpleDirectedGraph<T> {
        nodes.add(from)
        nodes.add(to)
        predecessorsMap.getOrPut(to) { hashSetOf() }.add(from)
        successorsMap.getOrPut(from) { hashSetOf() }.add(to)
        return this
    }

    fun induced(subset: Set<T>): SimpleDirectedGraph<T> {
        val newNodes = nodes.toMutableSet().also { it.retainAll(subset) }
        val newPredecessorsMap: MutableMap<T, MutableSet<T>> = mutableMapOf()
        for ((key, value) in predecessorsMap) {
            if (key in subset) {
                newPredecessorsMap[key] = value.toMutableSet().also { it.retainAll(subset) }
            }
        }
        val newSuccessorsMap: MutableMap<T, MutableSet<T>> = mutableMapOf()
        for ((key, value) in successorsMap) {
            if (key in subset) {
                newSuccessorsMap[key] = value.toMutableSet().also { it.retainAll(subset) }
            }
        }
        return SimpleDirectedGraph(newNodes, newPredecessorsMap, newSuccessorsMap)
    }

    fun weaklyConnectedComponents(): List<SimpleDirectedGraph<T>> =
        components(nodes) { predecessors(it) + successors(it) }.map { induced(it) }

    companion object {
        fun <T> union(lhs: SimpleDirectedGraph<T>, rhs: SimpleDirectedGraph<T>) = SimpleDirectedGraph(
            (lhs.nodes + rhs.nodes).toMutableSet(),
            (lhs.predecessorsMap + rhs.predecessorsMap).toMutableMap(),
            (lhs.successorsMap + rhs.successorsMap).toMutableMap()
        )
    }

    override fun iterator(): Iterator<T> = nodes.asIterable().iterator()
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
        if (visited.add(node)) {
            stack.add(node)
            for (next in successors(node)) {
                if (next in stack)
                    foundCycle = true
                dfs(next)
            }
            stack.remove(node)
            order.add(node)
        }
    }

    val nodes = iterator().asSequence().toHashSet()
    for (node in nodes) {
        dfs(node)
    }
    return if (foundCycle) null else order.reversed().filter { it in nodes }
}

fun <T> Graph<T>.SCCs(): OneDirectionGraph<Set<T>> {
    val components = components(rpo(), this::predecessors)
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
