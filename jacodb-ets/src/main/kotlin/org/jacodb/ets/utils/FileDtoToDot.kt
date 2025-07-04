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

import org.jacodb.ets.dto.BasicBlockDto
import org.jacodb.ets.dto.ClassDto
import org.jacodb.ets.dto.FileDto
import org.jacodb.ets.dto.IfStmtDto
import org.jacodb.ets.dto.MethodDto
import org.jacodb.ets.dto.NopStmtDto
import org.jacodb.ets.dto.StmtDto
import java.io.File
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

fun FileDto.toDot(useLR: Boolean = false): String {
    val lines: MutableList<String> = mutableListOf()
    lines += "digraph {"
    if (useLR) {
        lines += "  rankdir=LR;"
    }
    lines += "  compound=true;"
    for (x in listOf("graph", "node", "edge")) {
        lines += "  $x [fontname=\"JetBrains Mono,Source Code Pro,monospace\"];"
    }

    fun classId(clazz: ClassDto): String {
        return clazz.signature.name
    }

    fun classLabel(clazz: ClassDto): String {
        val labelLines: MutableList<String> = mutableListOf()
        run {
            val name = clazz.signature.name
            val typeParameters = clazz.typeParameters.orEmpty()
            val generics = if (typeParameters.isNotEmpty()) {
                "<${typeParameters.joinToString()}>"
            } else {
                ""
            }
            labelLines += "$name$generics"
        }
        labelLines += "Fields: (${clazz.fields.size})"
        clazz.fields.forEach { field ->
            val name = field.signature.name
            val returnType = field.signature.type
            labelLines += "  $name: $returnType"
        }
        labelLines += "Methods: (${clazz.methods.size})"
        clazz.methods.forEach { method ->
            val name = method.signature.name
            val params = method.signature.parameters.joinToString()
            val typeParameters = method.typeParameters.orEmpty()
            val generics = if (typeParameters.isNotEmpty()) {
                "<${typeParameters.joinToString()}>"
            } else {
                ""
            }
            val returnType = method.signature.returnType
            labelLines += "  $name$generics($params): $returnType"
        }
        return labelLines.joinToString("") { "$it\\l" }
    }

    fun methodId(clazz: ClassDto, method: MethodDto): String {
        return "${clazz.signature.name}.${method.signature.name}"
    }

    fun methodLabel(clazz: ClassDto, method: MethodDto): String {
        return "${clazz.signature.name}::${method.signature.name}"
    }

    fun blockId(clazz: ClassDto, method: MethodDto, id: Int): String {
        val m = methodId(clazz, method)
        return "${m}.bb${id}"
    }

    fun blockLabel(bb: BasicBlockDto): String {
        return "BB ${bb.id}\\nsuccessors = ${bb.successors}"
    }

    fun statementLabel(stmt: StmtDto): String {
        val labelLines: MutableList<String> = mutableListOf()
        labelLines += "$stmt".replace("\"", "\\\"")
        return labelLines.joinToString("") { "${it}\\l" }
    }

    classes.forEach { clazz ->
        // CLASS
        val c = classId(clazz)
        val clabel = classLabel(clazz)
        lines += ""
        lines += """  "$c" [shape=rect,label="$clabel"]"""

        // Methods inside class:
        clazz.methods.forEach { method ->
            // METHOD
            val m = methodId(clazz, method)
            val mlabel = methodLabel(clazz, method)
            lines += """  "$m" [shape=oval,label="$mlabel"];"""

            // Link class to method:
            lines += """  "$c" -> "$m" [dir=none];"""

            if (method.body != null) {
                // Link method to its first basic block:
                if (method.body.cfg.blocks.isNotEmpty()) {
                    val b = blockId(clazz, method, method.body.cfg.blocks.first().id)
                    lines += """  "$m" -> "${b}.0" [lhead="$b"];"""
                }

                // Basic blocks with instructions:
                method.body.cfg.blocks.forEach { bb ->
                    // BLOCK
                    val b = blockId(clazz, method, bb.id)
                    val blabel = blockLabel(bb)
                    lines += ""
                    lines += """  subgraph "$b" {"""
                    lines += """    cluster=true;"""
                    lines += """    label="$blabel";"""

                    // Empty block:
                    if (bb.stmts.isEmpty()) {
                        lines += """    "${b}.0" [shape=box, label="NOP"];"""
                    }

                    // Instructions inside basic block:
                    bb.stmts.forEachIndexed { i, inst ->
                        val label = statementLabel(inst)
                        lines += """    "${b}.${i}" [shape=box, label="$label"];"""
                    }

                    // Instructions chain:
                    if (bb.stmts.isNotEmpty()) {
                        val ids = List(bb.stmts.size) { i ->
                            "${b}.${i}"
                        }
                        lines += "    ${ids.joinToString(" -> ") { "\"$it\"" }};"
                    }

                    lines += "  }"
                }

                // Links to successor blocks:
                method.body.cfg.blocks.forEach { bb ->
                    val last = bb.stmts.lastOrNull()
                    val i = if (bb.stmts.isNotEmpty()) bb.stmts.lastIndex else 0
                    when (last ?: NopStmtDto) {
                        is IfStmtDto -> {
                            for ((j, succ) in bb.successors.withIndex()) {
                                val b = blockId(clazz, method, bb.id)
                                val bs = blockId(clazz, method, succ)
                                val label = if (j == 0) "true" else "false"
                                lines += """  "${b}.${i}" -> "${bs}.0" [lhead="$bs", label="$label"];"""
                            }
                        }

                        else -> {
                            for (succ in bb.successors) {
                                val b = blockId(clazz, method, bb.id)
                                val bs = blockId(clazz, method, succ)
                                lines += """  "${b}.${i}" -> "${bs}.0" [lhead="$bs"];"""
                            }
                        }
                    }
                }
            }
        }
    }

    lines += "}"
    return lines.joinToString("\n")
}

fun FileDto.dumpDot(path: Path) {
    path.parent?.createDirectories()
    path.writeText(toDot())
}

fun FileDto.dumpDot(file: File) {
    dumpDot(file.toPath())
}

fun FileDto.dumpDot(path: String) {
    dumpDot(File(path))
}
