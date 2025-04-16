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

import org.jacodb.ets.model.EtsFile

fun EtsFile.toText(): String {
    val lines: MutableList<String> = mutableListOf()
    lines += "EtsFile '${signature}':"
    classes.forEach { clazz ->
        lines += "= CLASS '${clazz.signature}':"
        lines += "  typeParameters = ${clazz.typeParameters}"
        lines += "  modifiers = ${clazz.modifiers}"
        lines += "  decorators = ${clazz.decorators}"
        lines += "  superClass = '${clazz.superClass}'"
        lines += "  fields: ${clazz.fields.size}"
        clazz.fields.forEach { field ->
            lines += "  - FIELD '${field.signature}'"
        }
        lines += "  methods: ${clazz.methods.size}"
        clazz.methods.forEach { method ->
            lines += "  - METHOD '${method.signature}':"
            lines += "    typeParameters = ${method.typeParameters}"
            lines += "    modifiers = ${method.modifiers}"
            lines += "    decorators = ${method.decorators}"
            lines += "    stmts: ${method.cfg.stmts.size}"
            method.cfg.stmts.forEach { stmt ->
                lines += "    ${stmt.location.index}. $stmt"
                val pad = " ".repeat("${stmt.location.index}".length + 2) // number + dot + space
                lines += "    ${pad}successors = ${method.cfg.successors(stmt).map { it.location.index }}"
            }
        }
    }
    return lines.joinToString("\n")
}
