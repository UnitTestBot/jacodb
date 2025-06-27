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
import org.jacodb.ets.model.EtsAssignStmt
import org.jacodb.ets.model.EtsBlockCfg
import org.jacodb.ets.model.EtsCallExpr
import org.jacodb.ets.model.EtsCallStmt
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

    // --- 1) Main CFG with ports on call statements ---
    for (block in main.blocks) {
        val nodeId = "M_${block.id}"
        // compute all call hashes in this block
        val callHashes = callees.keys
            .filter { it.second == block.id }
            .map { sanitize(it.first.hashCode()) }
            .toSet()

        if (useHtml) {
            // build HTML table rows, adding port attribute on call lines
            val rows = block.statements.joinToString(separator = "") { stmt ->
                val txt = stmt.toDotLabel().htmlEncode()
                val bg = when (stmt) {
                    currentStmt -> "lightblue"
                    in pathStmts -> "yellow"
                    else -> "white"
                }
                val stmtHash = sanitize(stmt.hashCode())
                val portAttr = if (stmtHash in callHashes) " port=\"p$stmtHash\"" else ""
                "<tr><td balign=\"left\" bgcolor=\"$bg\"$portAttr>$txt</td></tr>"
            }
            val table = buildString {
                append("<table border=\"0\" cellborder=\"1\" cellspacing=\"0\">")
                append("<tr><td><b>Block #${block.id}</b></td></tr>")
                append(rows)
                append("</table>")
            }
            lines += "  $nodeId [label=<$table>];"
        } else {
            val lbl = buildPlainLabel(block, pathStmts, currentStmt)
            lines += "  $nodeId [label=\"$lbl\"];"
        }
    }
    // Main CFG edges
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

    // --- 2) Callee clusters with call-edge from port ---
    for ((key, cfg) in callees) {
        val (stmt, parentBlock) = key
        val h = sanitize(stmt.hashCode())
        val clusterName = "cluster_${h}_B${parentBlock}"
        // method signature label
        val methodSig = when (stmt) {
            is EtsCallStmt -> stmt.callExpr!!.callee
            is EtsAssignStmt -> (stmt.rhv as EtsCallExpr).callee
            else -> stmt.toDotLabel()
        }
        // open subgraph
        lines += "  subgraph \"$clusterName\" {"
        lines += "    label=\"$methodSig\";"
        lines += "    style=dashed;"

        // render callee nodes
        for (blk in cfg.blocks) {
            val calleeNode = "C_${h}_${blk.id}"
            if (useHtml) {
                val table = buildHtmlTable(blk, pathStmts, currentStmt)
                lines += "    $calleeNode [label=<$table>];"
            } else {
                val lbl = buildPlainLabel(blk, pathStmts, currentStmt)
                lines += "    $calleeNode [label=\"$lbl\"];"
            }
        }
        // render callee edges
        for ((bid, succs) in cfg.successors) {
            val from = "C_${h}_$bid"
            when (succs.size) {
                1 -> lines += "    $from -> C_${h}_${succs[0]};"
                2 -> {
                    val (t, f) = succs
                    lines += "    $from -> C_${h}_$t [label=\"true\"];"
                    lines += "    $from -> C_${h}_$f [label=\"false\"];"
                }
            }
        }
        lines += "  }"

        // connect from the specific port on the caller block
        // connect from the specific port on the caller block using tailport
        val caller = "M_${parentBlock}"
        val entryId  = cfg.blocks.first().id
        val calleeEntry = "C_${h}_$entryId"
        val stmtHash = sanitize(stmt.hashCode())
        lines += "  $caller:p$stmtHash -> $calleeEntry [tailport=\"p$stmtHash\" ltail=\"$clusterName\" lhead=\"$clusterName\" style=dotted label=\"call\"];"
    }

    lines += "}"
    return lines.joinToString("\n")
}

private fun buildHtmlTable(
    block: BasicBlock,
    pathStmts: Set<EtsStmt>,
    currentStmt: EtsStmt?
): String {
    var i = 0
    val rows = block.statements.joinToString(separator = "") { stmt ->
        val txt = stmt.toDotLabel().htmlEncode()
        val stmtHash = sanitize(stmt.hashCode())
        val portAttr = if (stmt.callExpr != null) " port=\"p$stmtHash\"" else ""

        val bg = when (stmt) {
            currentStmt -> "lightblue"
            in pathStmts -> "yellow"
            else -> "white"
        }
        i++
        "<tr><td balign=\"left\" bgcolor=\"$bg\"$portAttr>$txt</td></tr>"
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
        val pfx = when (stmt) {
            currentStmt -> "[▶] "
            in pathStmts -> "[·] "
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

// helper to sanitize negative hash codes for Graphviz IDs
fun sanitize(id: Int): String = id.toString().let { if (it.startsWith("-")) "N${it.substring(1)}" else it }
