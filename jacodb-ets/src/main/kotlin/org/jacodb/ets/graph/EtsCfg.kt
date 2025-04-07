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

package org.jacodb.ets.graph

import org.jacodb.ets.base.EtsStmt
import org.jacodb.ets.base.EtsTerminatingStmt
import org.jacodb.impl.cfg.graphs.GraphDominators

class EtsCfg(
    val stmts: List<EtsStmt>,
    successorMap: Map<EtsStmt, List<EtsStmt>>, // Note: EtsIfStmt successors are (false, true) branches
) : EtsBytecodeGraph<EtsStmt> {

    private val predecessorMap: Map<EtsStmt, Set<EtsStmt>> by lazy {
        val map: MutableMap<EtsStmt, MutableSet<EtsStmt>> = hashMapOf()
        for ((stmt, nexts) in successorMap) {
            for (next in nexts) {
                map.getOrPut(next) { hashSetOf() } += stmt
            }
        }
        map
    }

    private val successorsLists = stmts.map {
        successorMap[it].orEmpty().toSet()
    }

    private val predecessorLists = stmts.map {
        predecessorMap[it].orEmpty().toSet()
    }

    override fun throwers(node: EtsStmt): Set<EtsStmt> {
        TODO("Current version of IR does not contain try catch blocks")
    }

    override fun catchers(node: EtsStmt): Set<EtsStmt> {
        TODO("Current version of IR does not contain try catch blocks")
    }

    override val instructions: List<EtsStmt>
        get() = stmts

    override val entries: List<EtsStmt>
        get() = listOfNotNull(stmts.firstOrNull())

    override val exits: List<EtsTerminatingStmt> =
        instructions.filterIsInstance<EtsTerminatingStmt>()

    override fun successors(node: EtsStmt): Set<EtsStmt> {
        return successorsLists[node.location.index]
    }

    override fun predecessors(node: EtsStmt): Set<EtsStmt> {
        return predecessorLists[node.location.index]
    }

    companion object {
        val EMPTY: EtsCfg by lazy {
            EtsCfg(emptyList(), emptyMap())
        }
    }
}

fun EtsCfg.findDominators(): GraphDominators<EtsStmt> {
    return GraphDominators(this).also { it.find() }
}
