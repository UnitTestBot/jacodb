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

package org.jacodb.ets.dsl

data class LinearizedCfg(
    val statements: List<Stmt>,
    val successors: Map<StmtLocation, List<StmtLocation>>,
)

fun BlockCfg.linearize(): LinearizedCfg {
    val linearized: MutableList<Stmt> = mutableListOf()
    val successors: MutableMap<StmtLocation, List<StmtLocation>> = hashMapOf()

    fun process(statements: List<BlockStmt>): List<Stmt> {
        val processed: MutableList<Stmt> = mutableListOf()
        var loc = linearized.size
        for (stmt in statements) {
            when (stmt) {
                is BlockNop -> {
                    // Note: ignore NOP statements
                    // processed += NopStmt(loc++)
                }

                is BlockAssign -> {
                    processed += AssignStmt(loc++, stmt.target, stmt.expr)
                }

                is BlockReturn -> {
                    processed += ReturnStmt(loc++, stmt.expr)
                }

                is BlockIf -> {
                    processed += IfStmt(loc++, stmt.condition)
                }

                is BlockCustomEts -> {
                    processed += CustomEtsStmt(loc++, stmt.toEts)
                }
            }
        }
        if (processed.isEmpty()) {
            processed += NopStmt(loc++)
        }
        linearized += processed
        check(linearized.size == loc)
        return processed
    }

    val linearizedBlocks = blocks.associate { it.id to process(it.statements) }

    for ((id, statements) in linearizedBlocks) {
        for ((stmt, next) in statements.zipWithNext()) {
            check(stmt !is ReturnStmt) { "Return statement in the middle of the block: $stmt" }
            check(stmt !is IfStmt) { "If statement in the middle of the block: $stmt" }
            successors[stmt.location] = listOf(next.location)
        }
        if (statements.isNotEmpty()) {
            val last = statements.last()
            val nextBlocks = this@linearize.successors[id] ?: error("No successors for block $id")
            // TODO: handle empty blocks (next)
            successors[last.location] = nextBlocks.map { linearizedBlocks.getValue(it).first().location }
        }
    }

    return LinearizedCfg(linearized, successors)
}
