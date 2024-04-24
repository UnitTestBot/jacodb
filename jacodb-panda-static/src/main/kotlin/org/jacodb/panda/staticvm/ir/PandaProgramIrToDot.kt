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

package org.jacodb.panda.staticvm.ir

import java.io.File
import java.nio.file.Path
import kotlin.io.path.writeText

fun PandaProgramIr.toDot(): String {
    val lines: MutableList<String> = mutableListOf()
    lines += "digraph {"
    lines += "  rankdir=LR;"
    lines += "  compound=true;"

    val classes = this.classes
        .filterNot { it.name.startsWith("std.") }
        .filterNot { it.name.startsWith("escompat.") }
        .filterNot { it.name.startsWith("FunctionalInterface") }

    // Classes with properties:
    classes.forEach { clazz ->
        // CLASS
        lines += ""
        lines += "  \"${clazz.name}\" [shape=diamond]"
        // TODO: add fields+methods to the label for class

        val methods = clazz.methods

        // Methods inside class:
        methods.forEach { method ->
            // METHOD
            lines += "  \"${clazz.name}.${method.name}\" [shape=triangle,label=\"${clazz.name}::${method.name}\"];"
            lines += "  \"${clazz.name}\" -> \"${clazz.name}.${method.name}\""
        }

        // Basic blocks inside method:
        methods.forEach { method ->
            // Link to the first basic block inside method:
            if (method.basicBlocks.isNotEmpty()) {
                lines += "  \"${clazz.name}.${method.name}\" -> \"${clazz.name}.${method.name}.bb${method.basicBlocks.first().id}.0\" [lhead=\"${clazz.name}.${method.name}.bb${method.basicBlocks.first().id}\"];"
            }

            method.basicBlocks.forEach { bb ->
                val last = bb.insts.lastOrNull()
                val i = if (bb.insts.isNotEmpty()) bb.insts.lastIndex else 0
                when (last) {
                    is PandaIfImmInstIr -> {
                        for ((j, succ) in bb.successors.withIndex()) {
                            lines += "  \"${clazz.name}.${method.name}.bb${bb.id}.${i}\" -> \"${clazz.name}.${method.name}.bb${succ}.0\" [lhead=\"${clazz.name}.${method.name}.bb${succ}\", label=\"${if (j == 0) "true" else "false"}\"];"
                        }
                    }

                    is PandaTryInstIr -> {
                        for ((j, succ) in bb.successors.withIndex()) {
                            lines += "  \"${clazz.name}.${method.name}.bb${bb.id}.${i}\" -> \"${clazz.name}.${method.name}.bb${succ}.0\" [lhead=\"${clazz.name}.${method.name}.bb${succ}\", label=\"${if (j == 0) "try" else "catch"}\"];"
                        }
                    }

                    else -> {
                        // check(bb.successors.size <= 1)
                        for (succ in bb.successors) {
                            lines += "  \"${clazz.name}.${method.name}.bb${bb.id}.${i}\" -> \"${clazz.name}.${method.name}.bb${succ}.0\" [lhead=\"${clazz.name}.${method.name}.bb${succ}\"];"
                        }
                    }
                }
            }

            // Basic blocks with instructions:
            method.basicBlocks.forEach { bb ->
                // BASIC BLOCK
                lines += ""
                lines += "  subgraph \"${clazz.name}.${method.name}.bb${bb.id}\" {"
                lines += "    cluster=true;"
                lines += "    label=\"BB ${bb.id}\\nsuccessors = ${bb.successors}\";"

                if (bb.insts.isEmpty()) {
                    lines += "    \"${clazz.name}.${method.name}.bb${bb.id}.0\" [shape=box,label=\"NOP\"];"
                }

                // Instructions inside basic block:
                bb.insts.forEachIndexed { i, inst ->
                    val labelLines: MutableList<String> = mutableListOf()
                    labelLines += "${inst.id}: ${inst.opcode}: ${inst.type}"
                    if (inst is PandaConstantInstIr) {
                        labelLines += "value = ${inst.value}"
                    }
                    if (inst is PandaLoadStringInstIr) {
                        labelLines += "string = ${inst.string}"
                    }
                    if (inst.inputs.isNotEmpty()) {
                        labelLines += "inputs = ${inst.inputs}"
                    }
                    if (inst.users.isNotEmpty()) {
                        labelLines += "users = ${inst.users}"
                    }
                    if (inst.catchers.isNotEmpty()) {
                        labelLines += "catchers = ${inst.catchers}"
                    }
                    // INSTRUCTION
                    lines += "    \"${clazz.name}.${method.name}.bb${bb.id}.${i}\" [shape=box,label=\"${
                        labelLines.joinToString("") { "${it}\\l" }
                    }\"];"
                }

                // Instructions chain:
                if (bb.insts.isNotEmpty()) {
                    lines += "    ${
                        List(bb.insts.size) { i ->
                            "\"${clazz.name}.${method.name}.bb${bb.id}.${i}\""
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

fun PandaProgramIr.dumpDot(file: File) {
    file.writeText(toDot())
}

fun PandaProgramIr.dumpDot(path: Path) {
    path.writeText(toDot())
}

fun PandaProgramIr.dumpDot(path: String) {
    dumpDot(File(path))
}
