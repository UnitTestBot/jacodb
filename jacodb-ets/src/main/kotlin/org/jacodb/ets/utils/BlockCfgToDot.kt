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
import org.jacodb.ets.model.EtsStmt
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

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
 *  - all callee CFGs discovered so far at call sites (keyed by statement and its parent block id)
 */
data class InterproceduralCfg(
    val main: EtsBlockCfg,
    val callees: Map<Pair<EtsStmt, Int>, EtsBlockCfg>
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
    lines += "digraph world {"
    lines += "  compound=true"
    lines += "  node [shape=${if (useHtml) "none" else "rect"} fontname=\"monospace\"]"

    // Main CFG
    for (block in main.blocks) {
        val id = "M_${block.id}"
        if (useHtml) {
            val table = buildHtmlTable(block, pathStmts, currentStmt)
            // embed as single-line label
            lines += "  $id [label=<$table>];"
        } else {
            val lbl = buildPlainLabel(block, pathStmts, currentStmt)
            lines += "  $id [label=\"$lbl\"];"
        }
    }
    // Main edges
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

    // Callee clusters
    for ((key, cfg) in callees) {
        val (stmt, parentId) = key
        val callId = stmt.hashCode().toString() + "_B$parentId"
        val cluster = "cluster_$callId"
        lines += "  subgraph $cluster {"
        lines += "    label=\"Callee of $callId\";"
        lines += "    style=dashed;"

        for (blk in cfg.blocks) {
            val nid = "C_${callId}_${blk.id}"
            if (useHtml) {
                val table = buildHtmlTable(blk, pathStmts, currentStmt)
                lines += "    $nid [label=<$table>];"
            } else {
                val lbl = buildPlainLabel(blk, pathStmts, currentStmt)
                lines += "    $nid [label=\"$lbl\"];"
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
        // call edge
        val caller = "M_$parentId"
        val entry = cfg.blocks.first().id
        lines += "  $caller -> C_${callId}_$entry [ltail=$cluster lhead=$cluster style=dotted label=\"call\"];"
    }

    lines += "}"
    return lines.joinToString("\n")
}

// ======== Helpers ========

private fun buildHtmlTable(
    block: BasicBlock,
    pathStmts: Set<EtsStmt>,
    currentStmt: EtsStmt?
): String {
    val rows = block.statements.joinToString(separator = "") { stmt ->
        val txt = stmt.toDotLabel().htmlEncode()
        val bg = when {
            stmt == currentStmt -> "lightblue"
            stmt in pathStmts -> "yellow"
            else -> "white"
        }
        "<tr><td balign=\"left\" bgcolor=\"$bg\">$txt</td></tr>"
    }
    return "<table border=\"0\" cellborder=\"1\" cellspacing=\"0\">" +
        "<tr><td><b>Block #${block.id}</b></td></tr>" + rows +
        "</table>"
}

private fun buildPlainLabel(
    block: BasicBlock,
    pathStmts: Set<EtsStmt>,
    currentStmt: EtsStmt?
): String {
    val body = block.statements.joinToString(separator = "") { stmt ->
        val raw = stmt.toDotLabel()
        val pfx = when {
            stmt == currentStmt -> "[▶] "
            stmt in pathStmts -> "[·] "
            else -> ""
        }
        "$pfx$raw\\l"
    }
    return "Block #${block.id}\\n" + body
}

fun renderDotOverwrite(
    dot: String,
    outputDir: Path = Paths.get("."),
    baseName: String = "interproc_cfg",
    dotCmd: String = "dot",
    viewerCmd: String = when {
        System.getProperty("os.name").startsWith("Mac") -> "open"
        System.getProperty("os.name").startsWith("Win") -> "cmd /c start"
        else -> "xdg-open"
    }
) {
    val dotFile = outputDir.resolve("$baseName.dot")
    val outSvg = outputDir.resolve("$baseName.svg")
    Files.write(dotFile, dot.toByteArray())
    Runtime.getRuntime().exec("$dotCmd -Tsvg -o $outSvg $dotFile").waitFor()
    Runtime.getRuntime().exec("$viewerCmd $outSvg").waitFor()
}