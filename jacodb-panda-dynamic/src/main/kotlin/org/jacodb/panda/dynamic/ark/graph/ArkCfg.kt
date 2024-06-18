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
    val blocks: Map<Int, ArkBasicBlock>,
) : ControlFlowGraph<ArkStmt> {
    val startingStmt: ArkStmt
        get() = blocks[0]!!.stmts.first()

    override val instructions: List<ArkStmt> by lazy {
        traverse().toList()
    }

    override val entries: List<ArkStmt>
        get() = listOf(startingStmt)

    override val exits: List<ArkStmt>
        get() = instructions.filterIsInstance<ArkTerminatingStmt>()

    private fun traverse(): Sequence<ArkStmt> = sequence {
        val visited: MutableSet<Int> = hashSetOf()
        val queue: ArrayDeque<Int> = ArrayDeque()
        queue.add(0)
        while (queue.isNotEmpty()) {
            val block = blocks[queue.removeFirst()]!!
            yieldAll(block.stmts)
            for (next in block.successors) {
                if (visited.add(next)) {
                    queue.add(next)
                }
            }
        }
    }

    fun successors(block: ArkBasicBlock): List<ArkBasicBlock> {
        return block.successors.map { blocks[it]!! }
    }

    fun predecessors(block: ArkBasicBlock): List<ArkBasicBlock> {
        return block.predecessors.map { blocks[it]!! }
    }

    private val successorMap: Map<ArkStmt, List<ArkStmt>> = run {
        val map: MutableMap<ArkStmt, List<ArkStmt>> = hashMapOf()
        for (block in blocks.values) {
            for ((i, stmt) in block.stmts.withIndex()) {
                check(stmt !in map)
                if (i == block.stmts.lastIndex) {
                    map[stmt] = block.successors.mapNotNull { blocks[it]!!.head }
                } else {
                    map[stmt] = listOf(block.stmts[i + 1])
                }
            }
        }
        map
    }

    private val predecessorMap: Map<ArkStmt, List<ArkStmt>> = run {
        val map: MutableMap<ArkStmt, List<ArkStmt>> = hashMapOf()
        for (block in blocks.values) {
            for ((i, stmt) in block.stmts.withIndex()) {
                check(stmt !in map)
                if (i == 0) {
                    map[stmt] = block.predecessors.mapNotNull { blocks[it]!!.last }
                } else {
                    map[stmt] = listOf(block.stmts[i - 1])
                }
            }
        }
        map
    }

    override fun successors(node: ArkStmt): Set<ArkStmt> {
        return successorMap[node]!!.toSet()
    }

    override fun predecessors(node: ArkStmt): Set<ArkStmt> {
        return predecessorMap[node]!!.toSet()
    }
}
