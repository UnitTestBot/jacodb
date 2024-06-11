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
import org.jacodb.panda.dynamic.ark.base.Stmt
import org.jacodb.panda.dynamic.ark.base.TerminatingStmt

class Cfg(
    val blocks: Map<Int, BasicBlock>,
) : ControlFlowGraph<Stmt> {
    val startingStmt: Stmt
        get() = blocks[0]!!.stmts.first()

    override val instructions: List<Stmt>
        get() = statements().toList()
    override val entries: List<Stmt>
        get() = listOf(startingStmt)
    override val exits: List<Stmt>
        get() = instructions.filterIsInstance<TerminatingStmt>()

    private val stmtToBlock: Map<Stmt, BasicBlock> = run {
        val map: MutableMap<Stmt, BasicBlock> = hashMapOf()
        for (block in blocks.values) {
            for (stmt in block.stmts) {
                map[stmt] = block
            }
        }
        map
    }

    fun successors(block: BasicBlock): List<BasicBlock> {
        return block.successors.map { blocks[it]!! }
    }

    fun predecessors(block: BasicBlock): List<BasicBlock> {
        return block.predecessors.map { blocks[it]!! }
    }

    override fun successors(node: Stmt): Set<Stmt> {
        val block = stmtToBlock[node]!!
        val i = block.stmts.indexOf(node)
        if (i == -1) {
            error("stmt not in block: $node")
        }
        if (i == block.stmts.lastIndex) {
            return block.successors.mapNotNullTo(hashSetOf()) { blocks[it]!!.head }
        } else {
            return setOf(block.stmts[i + 1])
        }
    }

    override fun predecessors(node: Stmt): Set<Stmt> {
        TODO()
    }

    fun statements(): Sequence<Stmt> = sequence {
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
}
