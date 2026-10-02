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

package org.jacodb.ets.model

class EtsLinearCfg(
    val stmts: List<EtsStmt>,
    val successors: List<List<Int>>, // for 'if-stmt', successors are (true, false) branches
    val exceptionalSuccessors: List<Set<Int>> = List(stmts.size) { emptySet() },
) : EtsBytecodeGraph<EtsStmt> {

    init {
        require(exceptionalSuccessors.size == stmts.size)
    }

    val predecessors: List<Set<Int>> by lazy {
        val result: List<MutableSet<Int>> = List(stmts.size) { hashSetOf() }
        for ((index, stmt) in stmts.withIndex()) {
            for (next in successors[index]) {
                result[next].add(index)
            }
        }
        result
    }

    override val instructions: List<EtsStmt>
        get() = stmts
    override val entries: List<EtsStmt> =
        stmts.take(1)
    override val exits: List<EtsTerminatingStmt> =
        stmts.filterIsInstance<EtsTerminatingStmt>().filter { exceptionalSuccessors[it.location.index].isEmpty() }

    private val successorsCache: MutableMap<EtsStmt, Set<EtsStmt>> = hashMapOf()

    override fun successors(node: EtsStmt): Set<EtsStmt> {
        return successorsCache.computeIfAbsent(node) {
            successors[node.location.index].mapTo(linkedSetOf()) { stmts[it] }
        }
    }

    private val predecessorsCache: MutableMap<EtsStmt, Set<EtsStmt>> = hashMapOf()

    private val exceptionalPredecessors: Map<Int, Set<Int>> by lazy {
        val result = mutableMapOf<Int, MutableSet<Int>>()
        for ((thrower, catchers) in exceptionalSuccessors.withIndex()) {
            for (catcher in catchers) {
                result.getOrPut(catcher) { linkedSetOf() }.add(thrower)
            }
        }
        result
    }

    override fun predecessors(node: EtsStmt): Set<EtsStmt> {
        return predecessorsCache.computeIfAbsent(node) {
            require(node.location.index >= 0) {
                "Invalid index: ${node.location.index}"
            }
            for (p in predecessors[node.location.index]) {
                require(p >= 0) {
                    "Invalid predecessor index: $p"
                }
            }
            predecessors[node.location.index].mapTo(hashSetOf()) { stmts[it] }
        }
    }

    override fun throwers(node: EtsStmt): Set<EtsStmt> {
        return exceptionalPredecessors[node.location.index].orEmpty().mapTo(linkedSetOf()) { stmts[it] }
    }

    override fun catchers(node: EtsStmt): Set<EtsStmt> {
        return exceptionalSuccessors[node.location.index].mapTo(linkedSetOf()) { stmts[it] }
    }

    companion object {
        val EMPTY: EtsLinearCfg by lazy {
            EtsLinearCfg(emptyList(), emptyList())
        }
    }
}
