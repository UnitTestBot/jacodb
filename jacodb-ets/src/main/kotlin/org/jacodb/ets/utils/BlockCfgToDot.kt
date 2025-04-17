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

private fun String.htmlEncode(): String = this
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")

fun EtsBlockCfg.toDot(useHtml: Boolean = true): String {
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
