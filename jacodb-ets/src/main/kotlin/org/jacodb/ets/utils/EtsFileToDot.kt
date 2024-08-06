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

import org.jacodb.ets.base.EtsStmt
import org.jacodb.ets.model.EtsClass
import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.model.EtsMethod
import java.io.BufferedWriter
import java.io.File
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

fun EtsFile.toDot(useLR: Boolean = false): String {
    val lines: MutableList<String> = mutableListOf()
    lines += "digraph {"
    if (useLR) {
        lines += "  rankdir=LR;"
    }
    lines += "  compound=true;"
    for (x in listOf("graph", "node", "edge")) {
        val fontname = "JetBrains Mono,Source Code Pro,monospace"
        val fontsize = 12.0
        lines += "  $x [fontname=\"$fontname\", fontsize=$fontsize];"
    }

    fun classId(clazz: EtsClass): String {
        return clazz.signature.name
    }

    fun classLabel(clazz: EtsClass): String {
        val labelLines: MutableList<String> = mutableListOf()
        labelLines += clazz.signature.name
        labelLines += "Fields: (${clazz.fields.size})"
        clazz.fields.forEach { field ->
            labelLines += "  ${field.signature.name}: ${field.signature.type}"
        }
        labelLines += "Methods: (${clazz.methods.size})"
        clazz.methods.forEach { method ->
            // TODO: add modifiers (when `EtsMethod` have `modifiers` property)
            val name = method.signature.name
            val params = method.signature.parameters.joinToString()
            val returnType = method.signature.returnType
            // TODO: uncomment generics when `EtsMethod` have `typeParameters` property
            // val generics = if (method.typeParameters.isNotEmpty()) {
            //     "<${method.typeParameters.joinToString()}>"
            // } else {
            //     ""
            // }
            val generics = ""
            labelLines += "  $name$generics($params): $returnType"
        }
        return labelLines.joinToString("") { "$it\\l" }
    }

    fun methodId(clazz: EtsClass, method: EtsMethod): String {
        return "${clazz.signature.name}.${method.signature.name}"
    }

    fun methodLabel(clazz: EtsClass, method: EtsMethod): String {
        return "${clazz.signature.name}::${method.signature.name}"
    }

    fun stmtId(clazz: EtsClass, method: EtsMethod, stmt: EtsStmt): String {
        return "${methodId(clazz, method)}.${stmt.location.index}"
    }

    fun stmtLabel(stmt: EtsStmt): String {
        return stmt.toString().replace("\"", "\\\"")
    }

    classes.forEach { clazz ->
        // CLASS
        val c = classId(clazz)
        val clabel = classLabel(clazz)
        lines += ""
        lines += """  "$c" [shape=box,label="$clabel"]"""

        // Methods inside class:
        (clazz.methods + clazz.ctor).forEach { method ->
            // METHOD
            val m = methodId(clazz, method)
            val mlabel = methodLabel(clazz, method)
            lines += """  "$m" [shape=oval,label="$mlabel"];"""

            // Link class to method:
            lines += """  "$c" -> "$m" [dir=none];"""

            // Link method to the first statement:
            method.cfg.stmts.firstOrNull()?.let { first ->
                val f = stmtId(clazz, method, first)
                lines += """  "$m" -> "$f";"""
            }

            // Statements inside method:
            method.cfg.stmts.forEach { stmt ->
                // STATEMENT
                val s = stmtId(clazz, method, stmt)
                val slabel = stmtLabel(stmt)
                lines += """  "$s" [shape=box,label="$slabel"];"""

                // Link to successors:
                when (stmt) {
                    // TODO: uncomment when `IfStmt`s are fixed in ArkIR
                    // is EtsIfStmt -> {
                    //     val successors = method.cfg.successors(stmt).toList()
                    //     check(successors.size == 2)
                    //     val f = stmtId(clazz, method, successors[0]) // false branch
                    //     val t = stmtId(clazz, method, successors[1]) // true branch
                    //     // s - f + {
                    //     //     label = "false"
                    //     // }
                    //     // s - t + {
                    //     //     label = "true"
                    //     // }
                    //     lines += """  "$s" -> "$f" [label="false"];"""
                    //     lines += """  "$s" -> "$t" [label="true"];"""
                    // }

                    // TODO: handle EtsSwitchStmt

                    else -> {
                        method.cfg.successors(stmt).forEach { succ ->
                            val w = stmtId(clazz, method, succ)
                            lines += """  "$s" -> "$w";"""
                        }
                    }
                }
            }
        }
    }
    lines += "}"
    return lines.joinToString("\n")
}

fun EtsFile.dumpDot(path: Path) {
    path.parent?.createDirectories()
    path.writeText(toDot())
}

fun EtsFile.dumpDot(file: File) {
    dumpDot(file.toPath())
}

fun EtsFile.dumpDot(path: String) {
    dumpDot(File(path))
}
