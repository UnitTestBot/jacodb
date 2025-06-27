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
import org.jacodb.ets.model.EtsCallExpr
import org.jacodb.ets.model.EtsCallStmt
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

/**
 * An interprocedural CFG that contains:
 *  - the main control-flow graph (main)
 *  - all callee CFGs discovered so far at call sites (keyed by the statement itself)
 */
data class InterproceduralCfg(
    val main: EtsBlockCfg,
    val callees: Map<EtsStmt, EtsBlockCfg>
)

/**
 * Render the interprocedural CFG (main + callees) as a single Graphviz DOT document,
 * highlighting the execution path and current statement, and drawing
 * each callee in its own dashed subgraph connected back to the call site.
 */
fun InterproceduralCfg.toHighlightedDotWithCalls(
    pathStmts: Set<EtsStmt>,
    currentStmt: EtsStmt?,
    useHtml: Boolean = true
): String {
    val lines = mutableListOf<String>()

    // Start the digraph and allow edges between clusters
    lines += "digraph world {"
    lines += "  compound=true"
    lines += "  node [shape=${if (useHtml) "none" else "rect"} fontname=\"monospace\"]"

    // ----- 1) Render the main CFG -----
    for (block in main.blocks) {
        val nodeId = "M_${block.id}"
        if (useHtml) {
            val table = buildHtmlTable(block, pathStmts, currentStmt)
            lines += "  $nodeId [label=<$table>];"
        } else {
            val label = buildPlainLabel(block, pathStmts, currentStmt)
            lines += "  $nodeId [label=\"$label\"];"
        }
    }
    for ((bid, succs) in main.successors) {
        val from = "M_$bid"
        when (succs.size) {
            1 -> lines += "  $from -> M_${succs[0]};"
            2 -> {
                val (t, f) = succs
                lines += "  $from -> M_$t [label=\"true\"];"
                lines += "  $from -> M_$f [label=\"false\"];"
            }
        }
    }

    // ----- 2) Render each discovered callee CFG as a dashed cluster -----
    for ((stmt, cfg) in callees) {
        val callId = stmt.hashCode().toString()
        val clusterName = "cluster_C_$callId"

        lines += "  subgraph $clusterName {"
        lines += "    label=\"Callee of stmt $callId\";"
        lines += "    style=dashed;"

        // nodes in callee
        for (block in cfg.blocks) {
            val nodeId = "C_${callId}_${block.id}"
            if (useHtml) {
                val table = buildHtmlTable(block, pathStmts, currentStmt)
                lines += "    $nodeId [label=<$table>];"
            } else {
                val label = buildPlainLabel(block, pathStmts, currentStmt)
                lines += "    $nodeId [label=\"$label\"];"
            }
        }
        for ((bid, succs) in cfg.successors) {
            val from = "C_${callId}_$bid"
            when (succs.size) {
                1 -> lines += "    $from -> C_${callId}_${succs[0]};"
                2 -> {
                    val (t, f) = succs
                    lines += "    $from -> C_${callId}_$t [label=\"true\"];"
                    lines += "    $from -> C_${callId}_$f [label=\"false\"];"
                }
            }
        }
        lines += "  }"

        // ----- 3) Connect call-site in main to callee entry -----
        // find which main block contains this stmt
        val callerBlockId = main.blocks.first { it.statements.contains(stmt) }.id
        val callerNode = "M_$callerBlockId"
        val entryNode  = "C_${callId}_${cfg.blocks.first().id}"
        lines += "  $callerNode -> $entryNode [ltail=$clusterName lhead=$clusterName style=dotted label=\"call\"];"
    }

    lines += "}"
    return lines.joinToString("\n")
}


/** Build an HTML table label for a block, coloring rows by path/current. */
private fun buildHtmlTable(
    block: BasicBlock,
    pathStmts: Set<EtsStmt>,
    currentStmt: EtsStmt?
): String {
    val rows = block.statements.joinToString("") { stmt ->
        val text = stmt.toDotLabel().htmlEncode()
        val bg = when {
            stmt == currentStmt -> "lightblue"
            stmt in pathStmts   -> "yellow"
            else                -> "white"
        }
        "<tr><td balign=\"left\" bgcolor=\"$bg\">$text</td></tr>"
    }
    return """
      <table border=\"0\" cellborder=\"1\" cellspacing=\"0\">
        <tr><td><b>Block #${block.id}</b></td></tr>
        $rows
      </table>
    """.trimIndent()
}

/** Build a plain-text label (non-HTML) for a block, prefixing rows. */
private fun buildPlainLabel(
    block: BasicBlock,
    pathStmts: Set<EtsStmt>,
    currentStmt: EtsStmt?
): String {
    val body = block.statements.joinToString("") { stmt ->
        val raw = stmt.toDotLabel()
        val prefix = when {
            stmt == currentStmt -> "[▶] "
            stmt in pathStmts   -> "[·] "
            else                -> ""
        }
        "$prefix$raw\\l"
    }
    return "Block #${block.id}\\n$body"
}
