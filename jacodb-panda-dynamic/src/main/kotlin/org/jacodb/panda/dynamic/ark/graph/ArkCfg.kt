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

package org.jacodb.panda.dynamic.ark.graph

import org.jacodb.api.common.cfg.ControlFlowGraph
import org.jacodb.panda.dynamic.ark.base.ArkStmt
import org.jacodb.panda.dynamic.ark.base.ArkTerminatingStmt

class ArkCfg(
    val stmts: List<ArkStmt>,
    private val successorMap: Map<ArkStmt, List<ArkStmt>>,
) : ControlFlowGraph<ArkStmt> {

    private val predecessorMap: Map<ArkStmt, Set<ArkStmt>> by lazy {
        val map: MutableMap<ArkStmt, MutableSet<ArkStmt>> = hashMapOf()
        for ((stmt, nexts) in successorMap) {
            for (next in nexts) {
                map.getOrPut(next) { hashSetOf() } += stmt
            }
        }
        map
    }

    override val instructions: List<ArkStmt>
        get() = stmts

    override val entries: List<ArkStmt>
        get() = listOf(stmts.first())

    override val exits: List<ArkTerminatingStmt>
        get() = instructions.filterIsInstance<ArkTerminatingStmt>()

    override fun successors(node: ArkStmt): Set<ArkStmt> {
        return successorMap[node]!!.toSet()
    }

    override fun predecessors(node: ArkStmt): Set<ArkStmt> {
        return predecessorMap[node]!!
    }
}
