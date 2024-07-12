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

package org.jacodb.panda.dynamic.ets.utils

import org.jacodb.panda.dynamic.ets.dto.BasicBlockDto
import org.jacodb.panda.dynamic.ets.dto.ClassDto
import org.jacodb.panda.dynamic.ets.dto.EtsFileDto
import org.jacodb.panda.dynamic.ets.dto.IfStmtDto
import org.jacodb.panda.dynamic.ets.dto.MethodDto
import org.jacodb.panda.dynamic.ets.dto.NopStmtDto
import org.jacodb.panda.dynamic.ets.dto.StmtDto
import org.jacodb.panda.dynamic.ets.dto.SwitchStmtDto
import java.io.File
import java.nio.file.Path
import kotlin.io.path.writeText

fun EtsFileDto.toDot(useLR: Boolean = true): String {
    val lines: MutableList<String> = mutableListOf()
    lines += "digraph {"
    if (useLR) {
        lines += "  rankdir=LR;"
    }
    lines += "  compound=true;"

    fun classId(clazz: ClassDto): String {
        return clazz.signature.name
    }

    fun classLabel(clazz: ClassDto): String {
        val labelLines: MutableList<String> = mutableListOf()
        labelLines += clazz.signature.name
        labelLines += "Fields: (${clazz.fields.size})"
        clazz.fields.forEach { field ->
            labelLines += "  ${field.signature.name}: ${field.signature.type}"
        }
        labelLines += "Methods: (${clazz.methods.size})"
        clazz.methods.forEach { method ->
            labelLines += "  ${method.signature.name}: ${method.signature.returnType}"
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
        return "BB ${bb.id}\nsuccessors = ${bb.successors}"
    }

    fun statementLabel(stmt: StmtDto): String {
        val labelLines: MutableList<String> = mutableListOf()
        labelLines += "$stmt"
        return labelLines.joinToString("") { "${it}\\l" }
    }

    classes.forEach { clazz ->
        // CLASS
        run {
            val c = classId(clazz)
            val label = classLabel(clazz)
            lines += ""
            lines += """  "$c" [shape=rectangle,label="$label"]"""
        }

        // Methods inside class:
        clazz.methods.forEach { method ->
            val m = methodId(clazz, method)
            val label = methodLabel(clazz, method)
            lines += """  "$m" [shape=oval,label="$label"];"""
            val c = classId(clazz)
            lines += """  "$c" -> "$m";"""
        }

        // Instructions inside method:
        clazz.methods.forEach { method ->
            // Link to the first basic block inside etsMethod:
            if (method.body.cfg.blocks.isNotEmpty()) {
                val m = methodId(clazz, method)
                val b = blockId(clazz, method, method.body.cfg.blocks.first().id)
                lines += """  "$m" -> "${b}.0" [lhead="$b"];"""
            }

            method.body.cfg.blocks.forEach { bb ->
                val last = bb.stmts.lastOrNull()
                val i = if (bb.stmts.isNotEmpty()) bb.stmts.lastIndex else 0
                when (last ?: NopStmtDto) {
                    is IfStmtDto -> {
                        for ((j, succ) in bb.successors.withIndex()) {
                            val b = blockId(clazz, method, bb.id)
                            val bs = blockId(clazz, method, succ)
                            val label = if (j == 0) "true" else "false"
                            lines += """  "${b}.${i}" -> "${bs}.0" [lhead=$bs, label="$label"];"""
                        }
                    }

                    is SwitchStmtDto -> {
                        for ((j, succ) in bb.successors.withIndex()) {
                            val b = blockId(clazz, method, bb.id)
                            val bs = blockId(clazz, method, succ)
                            val label = if (j == 0) "default" else "case ${j - 1}"
                            lines += """  "${b}.${i}" -> "${bs}.0" [lhead="$b", label="$label"];"""
                        }
                    }

                    else -> {
                        // check(bb.successors.size <= 1)
                        for (succ in bb.successors) {
                            val b = blockId(clazz, method, bb.id)
                            val bs = blockId(clazz, method, succ)
                            lines += """  "${b}.${i}" -> "${bs}.0" [lhead="$bs"];"""
                        }
                    }
                }
            }

            // Basic blocks with instructions:
            method.body.cfg.blocks.forEach { bb ->
                val b = blockId(clazz, method, bb.id)

                run {
                    val label = blockLabel(bb)
                    lines += ""
                    lines += """  subgraph "$b" {"""
                    lines += """    cluster=true;"""
                    lines += """    label="$label";"""
                }

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
                    lines += "    ${ids.joinToString(" -> ") { """"$it"""" }};"
                }

                lines += "  }"
            }
        }
    }

    lines += "}"
    return lines.joinToString("\n")
}

fun EtsFileDto.dumpDot(file: File) {
    file.writeText(toDot())
}

fun EtsFileDto.dumpDot(path: Path) {
    path.writeText(toDot())
}

fun EtsFileDto.dumpDot(path: String) {
    dumpDot(File(path))
}
