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
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsTrap

fun EtsBlockCfg.toDot(
    useHtml: Boolean = true,
    showExceptional: Boolean = true,
): String {
    val lines = mutableListOf<String>()
    lines += "digraph cfg {"
    lines += "  node [shape=${if (useHtml) "none" else "rect"} fontname=\"monospace\"]"
    lines += "  edge [fontname=\"monospace\"]"

    // Nodes
    for (block in blocks) {
        if (useHtml) {
            val s = block.statements.joinToString("") { it.toDotLabel().htmlEncode() + "<br/>" }
            val h =
                "<table border=\"0\" cellborder=\"1\" cellspacing=\"0\">" +
                    "<tr><td><b>Block #${block.id}</b></td></tr>" +
                    "<tr><td align=\"left\" balign=\"left\" cellpadding=\"4\">$s</td></tr>" +
                    "</table>"
            val attrs = " color=gray"
            lines += "  ${block.id} [label=<${h}>$attrs]"
        } else {
            val s = block.statements.joinToString("") { it.toDotLabel() + "\\l" }
            val attrs = " color=lightgray"
            lines += "  ${block.id} [label=\"Block #${block.id}\\n$s\"$attrs]"
        }
    }

    // Edges
    // Normal control-flow edges
    for (block in blocks) {
        val succs = successors[block.id]
        if (succs == null || succs.isEmpty()) continue
        if (succs.size == 1) {
            lines += "  ${block.id} -> ${succs.single()}"
        } else {
            check(succs.size == 2)
            val (trueBranch, falseBranch) = succs
            lines += "  ${block.id} -> $trueBranch [label=\"true\"]"
            lines += "  ${block.id} -> $falseBranch [label=\"false\"]"
        }
    }

    // Exceptional edges: dashed edges from try blocks to handler entries
    if (showExceptional) {
        val traps = method?.traps
        if (traps != null) {
            val seen = hashSetOf<Pair<Int, Int>>()
            for (t in traps) {
                val handler = t.catchBlocks.firstOrNull() ?: continue
                for (b in t.tryBlocks) {
                    val edge = b.id to handler.id
                    if (seen.add(edge)) {
                        val attrs = " style=dashed color=gray"
                        lines += "  ${b.id} -> ${handler.id} [label=\"exc\"$attrs]"
                    }
                }
            }
        }
    }

    lines += "}"
    return lines.joinToString("\n")
}
