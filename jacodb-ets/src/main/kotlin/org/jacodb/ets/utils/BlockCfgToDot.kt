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

import org.jacodb.ets.model.EtsBlockCfg
import org.jacodb.ets.model.EtsStmt

private fun String.htmlEncode(): String = this
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")

fun EtsBlockCfg.toDot(
    useHtml: Boolean = true
): String {
    val lines = mutableListOf<String>()
    lines += "digraph cfg {"
    lines += "  node [shape=${if (useHtml) "none" else "rect"} fontname=\"monospace\"]"

    // Nodes
    for (block in blocks) {
        if (useHtml) {
            val s = block.statements.joinToString("") {
                it.toDotLabel().htmlEncode() + "<br/>"
            }
            val h = "<table border=\"0\" cellborder=\"1\" cellspacing=\"0\">" +
                "<tr><td>" + "<b>Block #${block.id}</b>" + "</td></tr>" +
                "<tr><td balign=\"left\">" + s + "</td></tr>" +
                "</table>"
            lines += "  ${block.id} [label=<${h}>]"
        } else {
            val s = block.statements.joinToString("") { it.toDotLabel() + "\\l" }
            lines += "  ${block.id} [label=\"Block #${block.id}\\n$s\"]"
        }
    }

    // Edges
    for (block in blocks) {
        val succs = successors[block.id]
        if (succs != null) {
            if (succs.isEmpty()) continue
            if (succs.size == 1) {
                lines += "  ${block.id} -> ${succs.single()}"
            } else {
                check(succs.size == 2)
                val (trueBranch, falseBranch) = succs
                lines += "  ${block.id} -> $trueBranch [label=\"true\"]"
                lines += "  ${block.id} -> $falseBranch [label=\"false\"]"
            }
        }
    }

    lines += "}"
    return lines.joinToString("\n")
}

fun EtsBlockCfg.toHighlightedDot(
    pathStmts: Set<EtsStmt>,
    currentStmt: EtsStmt?,
    useHtml: Boolean = true
): String {
    val lines = mutableListOf<String>()

    // Start the digraph and set default node attributes
    lines += "digraph cfg {"
    lines += "  node [shape=${if (useHtml) "none" else "rect"} fontname=\"monospace\"]"

    // Generate a node for each basic block
    for (block in blocks) {
        if (useHtml) {
            // Build HTML table rows: one <tr> per statement, with background color
            val rows = block.statements.joinToString("") { stmt ->
                val label = stmt.toDotLabel().htmlEncode()
                val bgColor = when {
                    stmt == currentStmt -> "lightblue"  // highlight the current statement
                    stmt in pathStmts   -> "yellow"     // highlight the path statements
                    else                -> "white"      // default background
                }
                // Create a table row with left-aligned text
                "<tr><td balign=\"left\" bgcolor=\"$bgColor\">$label</td></tr>"
            }

            // Assemble the complete HTML table for this block
            val table = buildString {
                append("<table border=\"0\" cellborder=\"1\" cellspacing=\"0\">")
                append("<tr><td><b>Block #${block.id}</b></td></tr>")
                append(rows)
                append("</table>")
            }

            // Emit the node with an HTML label
            lines += "  ${block.id} [label=<$table>]"
        } else {
            // Fallback: non-HTML label, prefix symbols for path and current
            val body = block.statements.joinToString("") { stmt ->
                val raw = stmt.toDotLabel()
                val prefix = when {
                    stmt == currentStmt -> "[▶] "  // mark current statement
                    stmt in pathStmts   -> "[·] "  // mark path statements
                    else                -> ""
                }
                "$prefix$raw\\l"
            }
            lines += "  ${block.id} [label=\"Block #${block.id}\\n$body\"]"
        }
    }

    // Generate edges between blocks based on successors map
    for (block in blocks) {
        successors[block.id]?.let { succs ->
            when (succs.size) {
                0 -> {
                    // No outgoing edges for this block
                }
                1 -> {
                    // Single successor: unconditional jump
                    lines += "  ${block.id} -> ${succs.single()}"
                }
                2 -> {
                    // Two successors: label them true/false
                    val (trueBranch, falseBranch) = succs
                    lines += "  ${block.id} -> $trueBranch [label=\"true\"]"
                    lines += "  ${block.id} -> $falseBranch [label=\"false\"]"
                }
                else -> {
                    // Should not happen in a well-formed CFG
                    error("Block ${block.id} has more than two successors")
                }
            }
        }
    }

    // Close the digraph
    lines += "}"
    return lines.joinToString("\n")
}
