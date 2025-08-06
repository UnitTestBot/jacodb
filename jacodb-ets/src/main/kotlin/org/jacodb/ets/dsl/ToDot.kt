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

import java.util.IdentityHashMap

private fun Node.toDotLabel() = when (this) {
    is Nop -> "nop"
    is Assign -> "$target := $expr"
    is Return -> "return $expr"
    is If -> "if ($condition)"
    is Label -> "label $name"
    is Goto -> "goto $targetLabel"
}

fun Program.toDot(): String {
    val lines = mutableListOf<String>()
    lines += "digraph cfg {"
    lines += "  node [shape=rect fontname=\"monospace\"]"

    val labelMap: MutableMap<String, Label> = hashMapOf()
    val nodeToId: MutableMap<Node, Int> = IdentityHashMap()
    var freeId = 0

    fun processForNodes(nodes: List<Node>) {
        for (node in nodes) {
            val id = nodeToId.computeIfAbsent(node) { freeId++ }
            lines += "  $id [label=\"${node.toDotLabel()}\"]"
            if (node is If) {
                processForNodes(node.thenBranch)
                processForNodes(node.elseBranch)
            }
            if (node is Label) {
                check(node.name !in labelMap) { "Duplicate label: ${node.name}" }
                labelMap[node.name] = node
            }
        }
    }

    processForNodes(nodes)

    fun processForEdges(nodes: List<Node>) {
        for (node in nodes) {
            val id = nodeToId[node] ?: error("No ID for $node")
            when (node) {
                is If -> {
                    if (node.thenBranch.isNotEmpty()) {
                        val thenNode = node.thenBranch.first()
                        val thenId = nodeToId[thenNode] ?: error("No ID for $thenNode")
                        lines += "  $id -> $thenId [label=\"true\"]"
                        processForEdges(node.thenBranch)
                    }
                    if (node.elseBranch.isNotEmpty()) {
                        val elseNode = node.elseBranch.first()
                        val elseId = nodeToId[elseNode] ?: error("No ID for $elseNode")
                        lines += "  $id -> $elseId [label=\"false\"]"
                        processForEdges(node.elseBranch)
                    }
                }

                is Goto -> {
                    val labelNode = labelMap[node.targetLabel] ?: error("Unknown label: ${node.targetLabel}")
                    val labelId = nodeToId[labelNode] ?: error("No ID for $labelNode")
                    lines += "  $id -> $labelId"
                }

                else -> {
                    // See below.
                }
            }
        }

        for ((cur, next) in nodes.zipWithNext()) {
            val curId = nodeToId[cur] ?: error("No ID for $cur")
            val nextId = nodeToId[next] ?: error("No ID for $next")
            lines += "  $curId -> $nextId"
        }
    }

    processForEdges(nodes)

    lines += "}"
    return lines.joinToString("\n")
}

private fun BlockStmt.toDotLabel() = when (this) {
    is BlockAssign -> "$target := $expr"
    is BlockReturn -> "return $expr"
    is BlockIf -> "if ($condition)"
    is BlockNop -> "nop"
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

private fun Stmt.toDotLabel() = when (this) {
    is NopStmt -> "nop"
    is AssignStmt -> "$target := $expr"
    is ReturnStmt -> "return $expr"
    is IfStmt -> "if ($condition)"
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
