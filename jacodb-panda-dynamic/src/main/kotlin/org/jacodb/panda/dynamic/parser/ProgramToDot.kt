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

package org.jacodb.panda.dynamic.parser

import java.io.File
import java.nio.file.Path
import kotlin.io.path.writeText

fun IRParser.Program.toDot(): String {
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

        val properties = clazz.properties

        // Methods inside class:
        properties.forEach { property ->
            // METHOD
            lines += "  \"${clazz.name}.${property.name}\" [shape=triangle,label=\"${clazz.name}::${property.name}\"];"
            lines += "  \"${clazz.name}\" -> \"${clazz.name}.${property.name}\""
        }

        // Basic blocks inside method:
        properties.forEach { property ->
            // Link to the first basic block inside property:
            if (property.method.basicBlocks.isNotEmpty()) {
                lines += "  \"${clazz.name}.${property.name}\" -> \"${clazz.name}.${property.name}.bb${property.method.basicBlocks.first().id}.0\" [lhead=\"${clazz.name}.${property.name}.bb${property.method.basicBlocks.first().id}\"];"
            }

            // Basic blocks successors:
            // property.method.basicBlocks.forEach { bb ->
            //     bb.successors.forEach { succ ->
            //         lines += "  \"${clazz.name}.${property.name}.bb${bb.id}.0\" -> \"${clazz.name}.${property.name}.bb${succ}.0\"" +
            //             " [ltail=\"${clazz.name}.${property.name}.bb${bb.id}\",lhead=\"${clazz.name}.${property.name}.bb${succ}\"];"
            //     }
            // }

            property.method.basicBlocks.forEach { basicBlock ->
                val last = basicBlock.insts.lastOrNull()
                if (last != null) {
                    val i = basicBlock.insts.size - 1
                    when (last.opcode) {
                        "IfImm" -> {
                            for ((j, succ) in basicBlock.successors.withIndex()) {
                                lines += "  \"${clazz.name}.${property.name}.bb${basicBlock.id}.${i}\" -> \"${clazz.name}.${property.name}.bb${succ}.0\" [lhead=\"${clazz.name}.${property.name}.bb${succ}\", label=\"${if (j == 0) "true" else "false"}\"];"
                            }
                        }

                        "Try" -> {
                            for ((j, succ) in basicBlock.successors.withIndex()) {
                                lines += "  \"${clazz.name}.${property.name}.bb${basicBlock.id}.${i}\" -> \"${clazz.name}.${property.name}.bb${succ}.0\" [lhead=\"${clazz.name}.${property.name}.bb${succ}\", label=\"${if (j == 0) "try" else "catch"}\"];"
                            }
                        }

                        else -> {
                            check(basicBlock.successors.size <= 1)
                            for (succ in basicBlock.successors) {
                                lines += "  \"${clazz.name}.${property.name}.bb${basicBlock.id}.${i}\" -> \"${clazz.name}.${property.name}.bb${succ}.0\" [lhead=\"${clazz.name}.${property.name}.bb${succ}\"];"
                            }
                        }
                    }
                }
            }

            // Basic blocks with instructions:
            property.method.basicBlocks.forEach { bb ->
                // BASIC BLOCK
                lines += ""
                lines += "  subgraph \"${clazz.name}.${property.name}.bb${bb.id}\" {"
                lines += "    cluster=true;"
                lines += "    label=\"BB ${bb.id}\\nsuccessors = ${bb.successors}\";"

                if (bb.insts.isEmpty()) {
                    lines += "    \"${clazz.name}.${property.name}.bb${bb.id}.0\" [shape=box,label=\"NOP\"];"
                }

                // Instructions inside basic block:
                bb.insts.forEachIndexed { i, inst ->
                    val labelLines: MutableList<String> = mutableListOf()
                    labelLines += "${inst.id}: ${inst.opcode}: ${inst.type}"
                    if (inst.value != null) {
                        labelLines += "value = ${inst.value}"
                    }
                    if (inst.inputs.isNotEmpty()) {
                        labelLines += "inputs = ${inst.inputs}"
                    }
                    if (inst.users.isNotEmpty()) {
                        labelLines += "users = ${inst.users}"
                    }
                    // if (inst.catchers.isNotEmpty()) {
                    //     labelLines += "catchers = ${inst.catchers}"
                    // }
                    // INSTRUCTION
                    lines += "    \"${clazz.name}.${property.name}.bb${bb.id}.${i}\" [shape=box,label=\"${
                        labelLines.joinToString("") { "${it}\\l" }
                    }\"];"
                }

                // Instructions chain:
                if (bb.insts.isNotEmpty()) {
                    lines += "    ${
                        List(bb.insts.size) { i ->
                            "\"${clazz.name}.${property.name}.bb${bb.id}.${i}\""
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

fun IRParser.Program.dumpDot(file: File) {
    file.writeText(toDot())
}

fun IRParser.Program.dumpDot(path: Path) {
    path.writeText(toDot())
}

fun IRParser.Program.dumpDot(path: String) {
    dumpDot(File(path))
}
