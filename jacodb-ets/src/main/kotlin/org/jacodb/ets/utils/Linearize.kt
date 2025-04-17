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

package org.jacodb.ets.utils

import org.jacodb.ets.model.BasicBlock
import org.jacodb.ets.model.EtsBlockCfg
import org.jacodb.ets.model.EtsLinearCfg
import org.jacodb.ets.model.EtsStmt

fun EtsBlockCfg.linearize(): EtsLinearCfg {
    val linearized: MutableList<EtsStmt> = mutableListOf()

    val queue: ArrayDeque<BasicBlock> = ArrayDeque()
    val visited: MutableSet<Int> = hashSetOf()

    if (blocks.isNotEmpty()) {
        queue.add(blocks.first())
    }

    while (queue.isNotEmpty()) {
        val block = queue.removeFirst()
        if (!visited.add(block.id)) continue

        for (stmt in block.statements) {
            stmt.location.index = linearized.size
            linearized += stmt
        }

        val successors = successors[block.id] ?: error("No successors for block ${block.id}")
        for (id in successors.asReversed()) {
            val next = blocks[id]
            queue.addFirst(next) // DFS
        }
    }

    val linearSuccessors = arrayOfNulls<List<Int>>(linearized.size)

    for (id in visited) {
        val block = blocks[id]

        for ((stmt, next) in block.statements.zipWithNext()) {
            linearSuccessors[stmt.location.index] = listOf(next.location.index)
        }

        check(block.statements.isNotEmpty()) {
            "Block ${block.id} is empty"
        }

        val last = block.statements.last()
        val successors = successors[block.id] ?: error("No successors for block ${block.id}")
        linearSuccessors[last.location.index] = successors.map {
            blocks[it].statements.first().location.index
        }
    }

    for (s in linearSuccessors) {
        checkNotNull(s)
    }
    @Suppress("UNCHECKED_CAST")
    linearSuccessors as Array<List<Int>>

    return EtsLinearCfg(
        stmts = linearized,
        successors = linearSuccessors.asList()
    )
}
