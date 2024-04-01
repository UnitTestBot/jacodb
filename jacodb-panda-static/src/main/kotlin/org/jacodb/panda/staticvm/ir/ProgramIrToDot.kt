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

fun programToDot(program: PandaProgramIr): String {
    val lines: MutableList<String> = mutableListOf()
    lines += "digraph {"
    lines += "  compound=true;"
    lines += "  rankdir=LR;"

    // Classes with properties:
    program.classes.forEach { clazz ->
        if (clazz.name.startsWith("std.") || clazz.name.startsWith("FunctionalInterface") || clazz.name.startsWith("escompat.")) {
            return@forEach
        }

        // CLASS
        lines += ""
        lines += "  \"${clazz.name}\" [shape=diamond]"
        // TODO: add fields+methods to the label for class

        // // Fields inside class:
        // clazz.fields.forEach { field ->
        //     // FIELD
        //     lines += "  \"${clazz.name}.${field.name}\" [shape=box,label=\"${clazz.name}::${field.name}\"];"
        // }

        // Methods inside class:
        clazz.methods.forEach { method ->
            // METHOD
            lines += "  \"${clazz.name}.${method.name}\" [shape=triangle,label=\"${clazz.name}::${method.name}\"];"
        }
        clazz.methods.forEach { property ->
            lines += "  \"${clazz.name}\" -> \"${clazz.name}.${property.name}\""
        }

        // Basic blocks inside class:
        clazz.methods.forEach { method ->
            // Link to the first basic block inside method:
            if (method.basicBlocks.isNotEmpty()) {
                lines += "  \"${clazz.name}.${method.name}\" -> \"${clazz.name}.${method.name}.bb${method.basicBlocks.first().id}.0\" [lhead=\"${clazz.name}.${method.name}.bb${method.basicBlocks.first().id}\"];"
            }

            // Basic blocks successors:
            method.basicBlocks.forEach { bb ->
                bb.successors.forEach { succ ->
                    lines += "  \"${clazz.name}.${method.name}.bb${bb.id}.0\" -> \"${clazz.name}.${method.name}.bb${succ}.0\"" +
                        " [ltail=\"${clazz.name}.${method.name}.bb${bb.id}\",lhead=\"${clazz.name}.${method.name}.bb${succ}\"];"
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

                // sealed interface PandaInstIr {
                //     val id: String
                //     val inputs: List<String>
                //     val users: List<String>
                //     val opcode: String
                //     val type: String
                //     val catchers: List<Int>
                //
                //     fun <T> accept(visitor: PandaInstIrVisitor<T>): T
                // }


                // Instructions inside basic block:
                bb.insts.forEachIndexed { i, inst ->
                    val labelLines: MutableList<String> = mutableListOf()
                    labelLines += "${inst.id}: ${inst.opcode}: ${inst.type}"
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
    val s = programToDot(this)
    file.writeText(s)
}

fun PandaProgramIr.dumpDot(path: String) {
    dumpDot(File(path))
}
