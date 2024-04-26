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

fun Program.toDot(): String {
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
        run {
            val labelLines: MutableList<String> = mutableListOf()
            labelLines += clazz.name
            labelLines += "Methods: (${clazz.properties.size})"
            for (prop in clazz.properties) {
                labelLines += "  ${prop.method.name}: ${prop.method.returnType}"
            }
            lines += ""
            lines += "  \"${clazz.name}\" [shape=rectangle,label=\"${
                labelLines.joinToString("") { "$it\\l" }
            }\"]"
            // TODO: add fields to the label for class
        }

        // Methods inside class:
        clazz.properties.forEach { property ->
            // METHOD
            lines += "  \"${clazz.name}.${property.method.name}\" [shape=diamond,label=\"${clazz.name}::${property.method.name}\"];"
            lines += "  \"${clazz.name}\" -> \"${clazz.name}.${property.method.name}\""
        }

        // Basic blocks inside method:
        clazz.properties.forEach { property ->
            // Link to the first basic block inside property:
            if (property.method.basicBlocks.isNotEmpty()) {
                lines += "  \"${clazz.name}.${property.method.name}\" -> \"${clazz.name}.${property.method.name}.bb${property.method.basicBlocks.first().id}.0\" [lhead=\"${clazz.name}.${property.method.name}.bb${property.method.basicBlocks.first().id}\"];"
            }

            property.method.basicBlocks.forEach { bb ->
                val last = bb.insts.lastOrNull()
                val i = if (bb.insts.isNotEmpty()) bb.insts.lastIndex else 0
                when (last?.opcode ?: "NOP") {
                    "IfImm" -> {
                        for ((j, succ) in bb.successors.withIndex()) {
                            lines += "  \"${clazz.name}.${property.method.name}.bb${bb.id}.${i}\" -> \"${clazz.name}.${property.method.name}.bb${succ}.0\" [lhead=\"${clazz.name}.${property.method.name}.bb${succ}\", label=\"${if (j == 0) "true" else "false"}\"];"
                        }
                    }

                    "Try" -> {
                        for ((j, succ) in bb.successors.withIndex()) {
                            lines += "  \"${clazz.name}.${property.method.name}.bb${bb.id}.${i}\" -> \"${clazz.name}.${property.method.name}.bb${succ}.0\" [lhead=\"${clazz.name}.${property.method.name}.bb${succ}\", label=\"${if (j == 0) "try" else "catch"}\"];"
                        }
                    }

                    else -> {
                        // check(bb.successors.size <= 1)
                        for (succ in bb.successors) {
                            lines += "  \"${clazz.name}.${property.method.name}.bb${bb.id}.${i}\" -> \"${clazz.name}.${property.method.name}.bb${succ}.0\" [lhead=\"${clazz.name}.${property.method.name}.bb${succ}\"];"
                        }
                    }
                }
            }

            // Basic blocks with instructions:
            property.method.basicBlocks.forEach { bb ->
                // BASIC BLOCK
                lines += ""
                lines += "  subgraph \"${clazz.name}.${property.method.name}.bb${bb.id}\" {"
                lines += "    cluster=true;"
                lines += "    label=\"BB ${bb.id}\\nsuccessors = ${bb.successors}\";"

                if (bb.insts.isEmpty()) {
                    lines += "    \"${clazz.name}.${property.method.name}.bb${bb.id}.0\" [shape=box,label=\"NOP\"];"
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
                    if (inst.stringData != null) {
                        labelLines += "string_data = ${inst.stringData}"
                    }
                    // if (inst.catchers.isNotEmpty()) {
                    //     labelLines += "catchers = ${inst.catchers}"
                    // }
                    // INSTRUCTION
                    lines += "    \"${clazz.name}.${property.method.name}.bb${bb.id}.${i}\" [shape=box,label=\"${
                        labelLines.joinToString("") { "${it}\\l" }
                    }\"];"
                }

                // Instructions chain:
                if (bb.insts.isNotEmpty()) {
                    lines += "    ${
                        List(bb.insts.size) { i ->
                            "\"${clazz.name}.${property.method.name}.bb${bb.id}.${i}\""
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

fun Program.dumpDot(file: File) {
    file.writeText(toDot())
}

fun Program.dumpDot(path: Path) {
    path.writeText(toDot())
}

fun Program.dumpDot(path: String) {
    dumpDot(File(path))
}
