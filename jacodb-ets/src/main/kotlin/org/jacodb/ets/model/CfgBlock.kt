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

import mu.KotlinLogging
import org.jacodb.ets.utils.linearize

private val logger = KotlinLogging.logger {}

data class BasicBlock(
    val id: Int,
    val statements: List<EtsStmt>,
) {
    init {
        require(statements.isNotEmpty()) { "Empty block $id" }
    }
}

class EtsBlockCfg(
    val blocks: List<BasicBlock>,
    val successors: Map<Int, List<Int>>, // for 'if-stmt' block, successors are (true, false) branches
) : EtsBytecodeGraph<EtsStmt> {
    init {
        for ((i, block) in blocks.withIndex()) {
            require(block.id == i) { "Block id ${block.id} mismatch index $i" }
        }
        for ((id, successorIds) in successors) {
            if (id !in 0..blocks.size) {
                logger.warn { "Block id $id is out of bounds (${0..blocks.size})" }
            }
            for (s in successorIds) {
                if (s !in 0..blocks.size) {
                    logger.warn { "Successor $s is out of bounds (${0..blocks.size})" }
                }
            }
        }
    }

    val linear: EtsLinearCfg by lazy {
        linearize()
    }

    val stmts: List<EtsStmt>
        get() = linear.stmts

    override val instructions: List<EtsStmt>
        get() = linear.instructions
    override val entries: List<EtsStmt>
        get() = linear.entries
    override val exits: List<EtsStmt>
        get() = linear.exits

    override fun successors(stmt: EtsStmt): Set<EtsStmt> = linear.successors(stmt)
    override fun predecessors(stmt: EtsStmt): Set<EtsStmt> = linear.predecessors(stmt)
    override fun throwers(node: EtsStmt): Set<EtsStmt> = linear.throwers(node)
    override fun catchers(node: EtsStmt): Set<EtsStmt> = linear.catchers(node)

    companion object {
        val EMPTY: EtsBlockCfg by lazy {
            EtsBlockCfg(emptyList(), emptyMap())
        }
    }
}
