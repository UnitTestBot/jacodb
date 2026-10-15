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

private fun Node.toDotLabel(): String {
    val label = when (this) {
        is Nop -> "nop"
        is Assign -> "$target := $expr"
        is Return -> "return $expr"
        is If -> "if ($condition)"
        is Label -> "label $name"
        is Goto -> "goto $targetLabel"
        is CustomEts -> "???"
    }
    return label.replace("\"", "\\\"")
}

private class DotNode(
    val id: Int,
    val node: Node,
    val thenBranch: List<DotNode> = emptyList(),
    val elseBranch: List<DotNode> = emptyList(),
)

fun Program.toDot(): String {
    val lines = mutableListOf<String>()
    lines += "digraph cfg {"
    lines += "  node [shape=rect fontname=\"monospace\"]"

    val labelMap: MutableMap<String, DotNode> = hashMapOf()
    var freeId = 0

    fun processForNodes(nodes: List<Node>): List<DotNode> {
        return nodes.map { node ->
            val id = freeId++
            lines += "  $id [label=\"${node.toDotLabel()}\"]"

            val thenBranch = if (node is If) processForNodes(node.thenBranch) else emptyList()
            val elseBranch = if (node is If) processForNodes(node.elseBranch) else emptyList()
            val dotNode = DotNode(id = id, node = node, thenBranch = thenBranch, elseBranch = elseBranch)

            if (node is Label) {
                check(node.name !in labelMap) { "Duplicate label: ${node.name}" }
                labelMap[node.name] = dotNode
            }

            dotNode
        }
    }

    val dotNodes = processForNodes(nodes)

    fun processForEdges(nodes: List<DotNode>, continuation: DotNode? = null) {
        for ((index, dotNode) in nodes.withIndex()) {
            val next = nodes.getOrNull(index + 1) ?: continuation
            when (val node = dotNode.node) {
                is If -> {
                    val thenEntry = dotNode.thenBranch.firstOrNull() ?: next
                    if (thenEntry != null) {
                        lines += "  ${dotNode.id} -> ${thenEntry.id} [label=\"true\"]"
                    }
                    processForEdges(dotNode.thenBranch, next)

                    val elseEntry = dotNode.elseBranch.firstOrNull() ?: next
                    if (elseEntry != null) {
                        lines += "  ${dotNode.id} -> ${elseEntry.id} [label=\"false\"]"
                    }
                    processForEdges(dotNode.elseBranch, next)
                }

                is Goto -> {
                    val target = labelMap[node.targetLabel] ?: error("Unknown label: ${node.targetLabel}")
                    lines += "  ${dotNode.id} -> ${target.id}"
                }

                is Return -> Unit

                else -> {
                    if (next != null) {
                        lines += "  ${dotNode.id} -> ${next.id}"
                    }
                }
            }
        }
    }

    processForEdges(dotNodes)

    lines += "}"
    return lines.joinToString("\n")
}

private fun BlockStmt.toDotLabel(): String {
    val label = when (this) {
        is BlockAssign -> "$target := $expr"
        is BlockReturn -> "return $expr"
        is BlockIf -> "if ($condition)"
        is BlockNop -> "nop"
        is BlockCustomEts -> "???"
    }
    return label.replace("\"", "\\\"")
}

fun BlockCfg.toDot(): String {
    val lines = mutableListOf<String>()
    lines += "digraph cfg {"
    lines += "  node [shape=rect fontname=\"monospace\"]"

    // Nodes
    for (block in blocks) {
        val s = block.statements.joinToString("") { it.toDotLabel() + "\\l" }
        lines += "  ${block.id} [label=\"[#${block.id}]\\l${s}\"]"
    }

    // Edges
    for (block in blocks) {
        val succs = successors[block.id] ?: error("No successors for block ${block.id}")
        if (succs.isEmpty()) continue
        if (succs.size == 1) {
            lines += "  ${block.id} -> ${succs.single()}"
        } else {
            check(succs.size == 2)
            val (trueBranch, falseBranch) = succs // Note the order of successors: (true, false) branches
            lines += "  ${block.id} -> $trueBranch [label=\"true\"]"
            lines += "  ${block.id} -> $falseBranch [label=\"false\"]"
        }
    }

    lines += "}"
    return lines.joinToString("\n")
}

private fun Stmt.toDotLabel(): String {
    val label = when (this) {
        is NopStmt -> "nop"
        is AssignStmt -> "$target := $expr"
        is ReturnStmt -> "return $expr"
        is IfStmt -> "if ($condition)"
        is CustomEtsStmt -> "???"
    }
    return label.replace("\"", "\\\"")
}

fun LinearizedCfg.toDot(): String {
    val lines = mutableListOf<String>()
    lines += "digraph cfg {"
    lines += "  node [shape=rect fontname=\"monospace\"]"

    // Nodes
    for (stmt in statements) {
        val id = stmt.location
        lines += "  $id [label=\"$id: ${stmt.toDotLabel()}\"]"
    }

    // Edges
    for (stmt in statements) {
        when (stmt) {
            is IfStmt -> {
                val succs = successors[stmt.location] ?: error("No successors for $stmt")
                check(succs.size == 2) {
                    "Expected two successors for $stmt, but it has ${succs.size}: $succs"
                }
                val (thenBranch, elseBranch) = succs
                lines += "  ${stmt.location} -> $thenBranch [label=\"then\"]"
                lines += "  ${stmt.location} -> $elseBranch [label=\"else\"]"
            }

            else -> {
                val succs = successors[stmt.location] ?: error("No successors for $stmt")
                if (succs.isNotEmpty()) {
                    check(succs.size == 1) {
                        "Expected one successor for $stmt, but it has ${succs.size}: $succs"
                    }
                    val target = succs.single()
                    lines += "  ${stmt.location} -> $target"
                }
            }
        }
    }

    lines += "}"
    return lines.joinToString("\n")
}
