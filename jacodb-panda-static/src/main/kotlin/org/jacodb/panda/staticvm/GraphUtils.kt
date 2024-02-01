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

package org.jacodb.panda.staticvm

data class SimpleDirectedGraph<T>(
    val nodes: MutableSet<T> = mutableSetOf(),
    private val predecessorsMap: MutableMap<T, MutableList<T>> = mutableMapOf(),
    private val successorsMap: MutableMap<T, MutableList<T>> = mutableMapOf()
) {
    fun predecessors(t: T): List<T> = predecessorsMap.getOrDefault(t, emptyList())

    fun successors(t: T): List<T> = successorsMap.getOrDefault(t, emptyList())

    fun withNode(node: T): SimpleDirectedGraph<T> {
        nodes.add(node)
        return this
    }

    fun withEdge(from: T, to: T): SimpleDirectedGraph<T> {
        nodes.add(from)
        nodes.add(to)
        predecessorsMap.getOrPut(to, ::mutableListOf).add(from)
        successorsMap.getOrPut(from, ::mutableListOf).add(to)
        return this
    }

    fun induced(subset: Collection<T>) = SimpleDirectedGraph(
        nodes.intersect(subset.toSet()).toMutableSet(),
        predecessorsMap.mapNotNull { (key, value) -> if (subset.contains(key)) key to subset.intersect(value.toSet()).toMutableList() else null }
            .toMap().toMutableMap(),
        successorsMap.mapNotNull { (key, value) -> if (subset.contains(key)) key to subset.intersect(value.toSet()).toMutableList() else null }
            .toMap().toMutableMap()
    )
    fun weaklyConnectedComponents(): List<SimpleDirectedGraph<T>> = components(nodes) {
        predecessors(it) + successors(it)
    }.map(::induced)

    fun topsort(): List<T> {
        val visited = hashSetOf<T>()
        val order = mutableListOf<T>()
        val onStack = hashSetOf<T>()

        fun dfs(node: T) = node.takeIf(visited::add)?.also(onStack::add)?.let {
            successors(it).forEach(::dfs)
            order.add(node)
        }

        nodes.forEach { if (it !in visited) dfs(it) }
        return order.reversed()
    }

    companion object {
        fun <T> union(lhs: SimpleDirectedGraph<T>, rhs: SimpleDirectedGraph<T>) = SimpleDirectedGraph(
            lhs.nodes.plus(rhs.nodes).toMutableSet(),
            lhs.predecessorsMap.plus(rhs.predecessorsMap).toMutableMap(),
            lhs.successorsMap.plus(rhs.successorsMap).toMutableMap()
        )
    }
}

fun <T> search(start: T, successors: (T) -> List<T>, visitor: (T) -> Unit): List<T> {
    val visited = hashSetOf<T>()
    fun dfs(node: T) = node.takeIf(visited::add)?.also(visitor)?.let { successors(it).forEach(::dfs) }
    dfs(start)
    return visited.toList()
}

fun <T> components(starts: Iterable<T>, successors: (T) -> List<T>): List<List<T>> {
    val visited = hashSetOf<T>()
    return starts.mapNotNull { start ->
        if (start !in visited) search(start, { successors(it).filter { it !in visited } }, visited::add)
        else null
    }
}

fun <T> reachable(starts: Iterable<T>, successors: (T) -> List<T>): List<T> {
    val visited = hashSetOf<T>()
    starts.forEach { start ->
        if (start !in visited)
            visited.addAll(search(start, { successors(it).filter { it !in visited } }, {}))
    }
    return visited.toList()
}
