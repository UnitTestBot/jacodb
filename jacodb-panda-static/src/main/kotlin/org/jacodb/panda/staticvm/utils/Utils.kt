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

/**
 * Applies [operation] to [initial] object with all arguments from [Iterable] consecutively
 */
fun <T, A> Iterable<T>.applyFold(initial: A, operation: A.(T) -> Unit) =
    fold(initial) { acc, elem -> acc.apply { operation(elem) } }

fun <T> search(start: T, successors: (T) -> Iterable<T>, visit: (T) -> Unit): Set<T> {
    val visited = hashSetOf<T>()
    fun dfs(node: T) {
        if (visited.add(node)) {
            visit(node)
            for (next in successors(node)) {
                dfs(next)
            }
        }
    }
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

fun <T> reachable(starts: Iterable<T>, successors: (T) -> Collection<T>): Set<T> =
    starts.applyFold(hashSetOf()) { start ->
        if (!contains(start))
            addAll(search(start, { successors(it).filterNot(this::contains) }, {}))
    }
