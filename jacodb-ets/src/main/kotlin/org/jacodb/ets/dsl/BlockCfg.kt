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

data class Block(
    val id: Int,
    val statements: List<BlockStmt>,
)

data class BlockCfg(
    val blocks: List<Block>,
    val successors: Map<Int, List<Int>>, // for IF stmt, successors are (true, false) branches
)

private class NodeOccurrence(
    val node: Node,
    val thenBranch: List<NodeOccurrence> = emptyList(),
    val elseBranch: List<NodeOccurrence> = emptyList(),
)

fun Program.toBlockCfg(): BlockCfg {
    fun collectOccurrences(nodes: List<Node>): List<NodeOccurrence> = nodes.map { node ->
        if (node is If) {
            NodeOccurrence(
                node = node,
                thenBranch = collectOccurrences(node.thenBranch),
                elseBranch = collectOccurrences(node.elseBranch),
            )
        } else {
            NodeOccurrence(node)
        }
    }

    val occurrences = collectOccurrences(nodes)
    val labelToOccurrence: MutableMap<String, NodeOccurrence> = hashMapOf()
    val targets: MutableSet<NodeOccurrence> = hashSetOf()

    fun findLabels(nodes: List<NodeOccurrence>) {
        if (nodes.lastOrNull()?.node is Label) {
            error("Label at the end of the block: $nodes")
        }
        for ((occurrence, next) in nodes.zipWithNext()) {
            val label = occurrence.node as? Label ?: continue

            check(next.node !is Label) { "Two labels in a row: $label, ${next.node}" }
            check(next.node !is Goto) { "Label followed by goto: $label, ${next.node}" }
            check(label.name !in labelToOccurrence) { "Duplicate label: ${label.name}" }
            labelToOccurrence[label.name] = next
        }
        for (occurrence in nodes) {
            findLabels(occurrence.thenBranch)
            findLabels(occurrence.elseBranch)
        }
    }

    findLabels(occurrences)

    fun findTargets(nodes: List<NodeOccurrence>) {
        for (occurrence in nodes) {
            when (val node = occurrence.node) {
                is If -> {
                    findTargets(occurrence.thenBranch)
                    findTargets(occurrence.elseBranch)
                }

                is Goto -> targets += labelToOccurrence[node.targetLabel]
                    ?: error("Unknown label: ${node.targetLabel}")

                else -> Unit
            }
        }
    }

    findTargets(occurrences)

    val blocks: MutableList<Block> = mutableListOf()
    val successors: MutableMap<Int, List<Int>> = hashMapOf()
    val occurrenceToBlock: MutableMap<NodeOccurrence, Int> = hashMapOf()
    val pendingGotos: MutableList<Pair<Int, String>> = mutableListOf()

    fun buildBlocks(nodes: List<NodeOccurrence>): Pair<Int, Int>? {
        if (nodes.isEmpty()) return null

        lateinit var currentBlock: MutableList<BlockStmt>

        fun newBlock(): Block {
            currentBlock = mutableListOf()
            val block = Block(blocks.size, currentBlock)
            blocks += block
            return block
        }

        fun newEmptyBranch(): Block {
            val branch = Block(blocks.size, emptyList())
            blocks += branch
            return branch
        }

        var block = newBlock()
        val firstBlockId = block.id

        for ((index, occurrence) in nodes.withIndex()) {
            val node = occurrence.node
            if (node is Label) continue

            if (occurrence in targets && currentBlock.isNotEmpty()) {
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
                    is CustomEts -> BlockCustomEts(node.toEts)
                    else -> error("Unexpected node: $node")
                }
                occurrenceToBlock[occurrence] = block.id
                currentBlock += stmt
            }

            if (node is If) {
                val ifBlock = block
                block = newBlock()

                val thenBlocks = buildBlocks(occurrence.thenBranch)
                val elseBlocks = buildBlocks(occurrence.elseBranch)

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
                        val trueBranch = newEmptyBranch()
                        val falseBranch = newEmptyBranch()
                        successors[ifBlock.id] = listOf(trueBranch.id, falseBranch.id)
                        successors[trueBranch.id] = listOf(block.id)
                        successors[falseBranch.id] = listOf(block.id)
                    }
                }
            } else if (node is Goto) {
                pendingGotos += block.id to node.targetLabel
                block = newBlock()
            } else if (node is Return) {
                successors[block.id] = emptyList()
                if (index < nodes.lastIndex) {
                    block = newBlock()
                }
            }
        }

        val lastBlockId = block.id

        return Pair(firstBlockId, lastBlockId)
    }

    buildBlocks(occurrences)

    for ((sourceBlockId, label) in pendingGotos) {
        val targetOccurrence = labelToOccurrence.getValue(label)
        val targetBlockId = occurrenceToBlock[targetOccurrence]
            ?: error("No block for ${targetOccurrence.node}")
        successors[sourceBlockId] = listOf(targetBlockId)
    }

    for (block in blocks) {
        successors.putIfAbsent(block.id, emptyList())
    }

    return BlockCfg(blocks, successors)
}
