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

import org.jacodb.ets.base.EtsAssignStmt
import org.jacodb.ets.base.EtsIfStmt
import org.jacodb.ets.base.EtsNopStmt
import org.jacodb.ets.base.EtsReturnStmt
import org.jacodb.ets.base.EtsStmt
import org.jacodb.ets.graph.EtsCfg

private fun EtsStmt.toDotLabel(): String = when (this) {
    is EtsNopStmt -> "nop"
    is EtsAssignStmt -> "$lhv := $rhv"
    is EtsReturnStmt -> returnValue?.let { "return $it" } ?: "return"
    is EtsIfStmt -> "if ($condition)"
    else -> this.toString() // TODO: support more statement types
}

fun EtsCfg.toDot(): String {
    val lines = mutableListOf<String>()
    lines += "digraph cfg {"
    lines += "  node [shape=rect fontname=\"monospace\"]"

    // Nodes
    stmts.forEach { stmt ->
        val id = stmt.location.index
        val label = stmt.toDotLabel().replace("\"", "\\\"")
        lines += "  $id [label=\"$id: $label\"]"
    }

    // Edges
    stmts.forEach { stmt ->
        when (stmt) {
            is EtsIfStmt -> {
                val succs = successors(stmt)
                check(succs.size == 2) {
                    "Expected two successors for $stmt, but it has ${succs.size}: $succs"
                }
                // val (thenBranch, elseBranch) = succs.toList()
                val (thenBranch, elseBranch) = succs.toList().reversed() // TODO: check order of successors
                lines += "  ${stmt.location.index} -> ${thenBranch.location.index} [label=\"then\"]"
                lines += "  ${stmt.location.index} -> ${elseBranch.location.index} [label=\"else\"]"
            }

            else -> {
                val succs = successors(stmt)
                if (succs.isNotEmpty()) {
                    check(succs.size == 1) {
                        "Expected one successor for $stmt, but it has ${succs.size}: $succs"
                    }
                    val target = succs.single()
                    lines += "  ${stmt.location.index} -> ${target.location.index}"
                }
            }
        }
    }

    lines += "}"
    return lines.joinToString("\n")
}
