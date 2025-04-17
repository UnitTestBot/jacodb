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

import org.jacodb.ets.utils.IdentityHashSet
import java.util.IdentityHashMap

data class Block(
    val id: Int,
    val statements: List<BlockStmt>,
)

data class BlockCfg(
    val blocks: List<Block>,
    val successors: Map<Int, List<Int>>, // for IF stmt, successors are (true, false) branches
)

fun Program.toBlockCfg(): BlockCfg {
    val labelToNode: MutableMap<String, Node> = hashMapOf()
    val targets: MutableSet<Node> = IdentityHashSet()

    fun findLabels(nodes: List<Node>) {
        if (nodes.lastOrNull() is Label) {
            error("Label at the end of the block: $nodes")
        }
        for ((stmt, next) in nodes.zipWithNext()) {
            if (stmt is Label) {
                check(next !is Label) { "Two labels in a row: $stmt, $next" }
                check(next !is Goto) { "Label followed by goto: $stmt, $next" }
                check(stmt.name !in labelToNode) { "Duplicate label: ${stmt.name}" }
                labelToNode[stmt.name] = next
            }
        }
        for (node in nodes) {
            if (node is If) {
                findLabels(node.thenBranch)
                findLabels(node.elseBranch)
            }
            if (node is Goto) {
                targets += labelToNode[node.targetLabel] ?: error("Unknown label: ${node.targetLabel}")
            }
        }
    }

    findLabels(nodes)

    val blocks: MutableList<Block> = mutableListOf()
    val successors: MutableMap<Int, List<Int>> = hashMapOf()
    val stmtToBlock: MutableMap<BlockStmt, Int> = IdentityHashMap()
    val nodeToStmt: MutableMap<Node, BlockStmt> = IdentityHashMap()

    fun buildBlocks(nodes: List<Node>): Pair<Int, Int>? {
        if (nodes.isEmpty()) return null

        lateinit var currentBlock: MutableList<BlockStmt>

        fun newBlock(): Block {
            currentBlock = mutableListOf()
            val block = Block(blocks.size, currentBlock)
            blocks += block
            return block
        }

        var block = newBlock()
        val firstBlockId = block.id

        for (node in nodes) {
            if (node is Label) continue

            if (node in targets && currentBlock.isNotEmpty()) {
                block.statements.forEach { stmtToBlock[it] = block.id }
                val prevBlock = block
                block = newBlock()
                successors[prevBlock.id] = listOf(block.id)
            }

            if (node !is Goto) {
                val stmt = when (node) {
                    Nop -> BlockNop
                    is Assign -> BlockAssign(node.target, node.expr)
                    is Return -> BlockReturn(node.expr)
                    is If -> BlockIf(node.condition)
                    else -> error("Unexpected node: $node")
                }
                nodeToStmt[node] = stmt
                currentBlock += stmt
            }

            if (node is If) {
                block.statements.forEach { stmtToBlock[it] = block.id }
                val ifBlock = block
                block = newBlock()

                val thenBlocks = buildBlocks(node.thenBranch)
                val elseBlocks = buildBlocks(node.elseBranch)

                when {
                    thenBlocks != null && elseBlocks != null -> {
                        val (thenStart, thenEnd) = thenBlocks
                        val (elseStart, elseEnd) = elseBlocks
                        successors[ifBlock.id] = listOf(thenStart, elseStart) // (true, false) branches
                        when (blocks[thenEnd].statements.lastOrNull()) {
                            is BlockReturn -> {}
                            is BlockIf -> error("Unexpected if statement at the end of the block")
                            else -> successors[thenEnd] = listOf(block.id)
                        }
                        when (blocks[elseEnd].statements.lastOrNull()) {
                            is BlockReturn -> {}
                            is BlockIf -> error("Unexpected if statement at the end of the block")
                            else -> successors[elseEnd] = listOf(block.id)
                        }
                    }

                    thenBlocks != null -> {
                        val (thenStart, thenEnd) = thenBlocks
                        successors[ifBlock.id] = listOf(thenStart, block.id) // (true, false) branches
                        when (blocks[thenEnd].statements.lastOrNull()) {
                            is BlockReturn -> {}
                            is BlockIf -> error("Unexpected if statement at the end of the block")
                            else -> successors[thenEnd] = listOf(block.id)
                        }
                    }

                    elseBlocks != null -> {
                        val (elseStart, elseEnd) = elseBlocks
                        successors[ifBlock.id] = listOf(block.id, elseStart) // (true, false) branches
                        when (blocks[elseEnd].statements.lastOrNull()) {
                            is BlockReturn -> {}
                            is BlockIf -> error("Unexpected if statement at the end of the block")
                            else -> successors[elseEnd] = listOf(block.id)
                        }
                    }

                    else -> {
                        successors[ifBlock.id] = listOf(block.id)
                    }
                }
            } else if (node is Goto) {
                val targetNode = labelToNode[node.targetLabel] ?: error("Unknown label: ${node.targetLabel}")
                val target = nodeToStmt[targetNode] ?: error("No statement for $targetNode")
                val targetBlockId = stmtToBlock[target] ?: error("No block for $target")
                successors[block.id] = listOf(targetBlockId)
                block.statements.forEach { stmtToBlock[it] = block.id }
                block = newBlock()
            } else if (node is Return) {
                successors[block.id] = emptyList()
                break
            }
        }

        block.statements.forEach { stmtToBlock[it] = block.id }
        val lastBlockId = block.id

        return Pair(firstBlockId, lastBlockId)
    }

    buildBlocks(nodes)

    return BlockCfg(blocks, successors)
}
