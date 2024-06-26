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

package org.jacodb.panda.dynamic.ark.utils

import org.jacodb.panda.dynamic.ark.dto.ArkFileDto
import org.jacodb.panda.dynamic.ark.dto.IfStmtDto
import org.jacodb.panda.dynamic.ark.dto.NopStmtDto
import org.jacodb.panda.dynamic.ark.dto.SwitchStmtDto
import java.io.File
import java.nio.file.Path
import kotlin.io.path.writeText


fun ArkFileDto.toDot(): String {
    val lines: MutableList<String> = mutableListOf()
    lines += "digraph {"
    lines += "  rankdir=LR;"
    lines += "  compound=true;"

    val classes = this.classes

    classes.forEach { clazz ->
        run {
            val labelLines: MutableList<String> = mutableListOf()
            labelLines += clazz.signature.name
            labelLines += "Methods: (${clazz.methods.size})"
            for (method in clazz.methods) {
                labelLines += "  ${method.signature.name}: ${method.signature.returnType}"
            }
            lines += ""
            lines += "  \"${clazz.signature.name}\" [shape=rectangle,label=\"${
                labelLines.joinToString("") { "$it\\l" }
            }\"]"
            // TODO: add fields to the label for class
        }
        // Methods inside class:
        clazz.methods.forEach { arkMethod ->
            // METHOD
            lines += "  \"${clazz.signature.name}.${arkMethod.signature.name}\" [shape=diamond,label=\"${clazz.signature.name}::${arkMethod.signature.name}\"];"
            lines += "  \"${clazz.signature.name}\" -> \"${clazz.signature.name}.${arkMethod.signature.name}\""
        }

        // Instructions inside method:
        clazz.methods.forEach { arkMethod ->
            // Link to the first basic block inside arkMethod:
            if (arkMethod.body.cfg.blocks.isNotEmpty()) {
                lines += "  \"${clazz.signature.name}.${arkMethod.signature.name}\" -> \"${clazz.signature.name}.${arkMethod.signature.name}.bb${arkMethod.body.cfg.blocks.first().id}.0\" [lhead=\"${clazz.signature.name}.${arkMethod.signature.name}.bb${arkMethod.body.cfg.blocks.first().id}\"];"
            }

            arkMethod.body.cfg.blocks.forEach { bb ->
                val last = bb.stmts.lastOrNull()
                val i = if (bb.stmts.isNotEmpty()) bb.stmts.lastIndex else 0
                when (last ?: NopStmtDto) {
                    is IfStmtDto -> {
                        for ((j, succ) in bb.successors.withIndex()) {
                            lines += "  \"${clazz.signature.name}.${arkMethod.signature.name}.bb${bb.id}.${i}\" -> \"${clazz.signature.name}.${arkMethod.signature.name}.bb${succ}.0\" [lhead=\"${clazz.signature.name}.${arkMethod.signature.name}.bb${succ}\", label=\"${if (j == 0) "true" else "false"}\"];"
                        }
                    }

                    is SwitchStmtDto -> {
                        for ((j, succ) in bb.successors.withIndex()) {
                            lines += "  \"${clazz.signature.name}.${arkMethod.signature.name}.bb${bb.id}.${i}\" -> \"${clazz.signature.name}.${arkMethod.signature.name}.bb${succ}.0\" [lhead=\"${clazz.signature.name}.${arkMethod.signature.name}.bb${succ}\", label=\"${if (j == 0) "default" else "case ${j - 1}"}\"];"
                        }
                    }

                    else -> {
                        // check(bb.successors.size <= 1)
                        for (succ in bb.successors) {
                            lines += "  \"${clazz.signature.name}.${arkMethod.signature.name}.bb${bb.id}.${i}\" -> \"${clazz.signature.name}.${arkMethod.signature.name}.bb${succ}.0\" [lhead=\"${clazz.signature.name}.${arkMethod.signature.name}.bb${succ}\"];"
                        }
                    }
                }
            }

            // Basic blocks with instructions:
            arkMethod.body.cfg.blocks.forEach { bb ->
                // BASIC BLOCK
                lines += ""
                lines += "  subgraph \"${clazz.signature.name}.${arkMethod.signature.name}.bb${bb.id}\" {"
                lines += "    cluster=true;"
                lines += "    label=\"BB ${bb.id}\\nsuccessors = ${bb.successors}\";"

                if (bb.stmts.isEmpty()) {
                    lines += "    \"${clazz.signature.name}.${arkMethod.signature.name}.bb${bb.id}.0\" [shape=box,label=\"NOP\"];"
                }

                // Instructions inside basic block:
                bb.stmts.forEachIndexed { i, inst ->
                    val labelLines: MutableList<String> = mutableListOf()
                    labelLines += "${inst}"
                    lines += "    \"${clazz.signature.name}.${arkMethod.signature.name}.bb${bb.id}.${i}\" [shape=box,label=\"${
                        labelLines.joinToString("") { "${it}\\l" }
                    }\"];"
                }

                // Instructions chain:
                if (bb.stmts.isNotEmpty()) {
                    lines += "    ${
                        List(bb.stmts.size) { i ->
                            "\"${clazz.signature.name}.${arkMethod.signature.name}.bb${bb.id}.${i}\""
                        }.joinToString(" -> ")
                    };"
                }

                lines += "  }"
            }
        }
    }

    lines += "}"
    return lines.joinToString("\n")
}

fun ArkFileDto.dumpDot(file: File) {
    file.writeText(toDot())
}

fun ArkFileDto.dumpDot(path: Path) {
    path.writeText(toDot())
}

fun ArkFileDto.dumpDot(path: String) {
    dumpDot(File(path))
}
