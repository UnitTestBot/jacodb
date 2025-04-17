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

import org.jacodb.ets.model.EtsIfStmt
import org.jacodb.ets.model.EtsLinearCfg

fun EtsLinearCfg.toDot(): String {
    val lines = mutableListOf<String>()
    lines += "digraph cfg {"
    lines += "  node [shape=rect fontname=\"monospace\"]"

    // Nodes
    for (stmt in stmts) {
        val id = stmt.location.index
        val label = stmt.toDotLabel().replace("\"", "\\\"")
        lines += "  $id [label=\"$id: $label\"]"
    }

    // Edges
    for (stmt in stmts) {
        when (stmt) {
            is EtsIfStmt -> {
                val succs = successors(stmt)
                check(succs.size == 2) {
                    "Expected two successors for $stmt, but it has ${succs.size}: $succs"
                }
                val (thenBranch, elseBranch) = succs.toList()
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
