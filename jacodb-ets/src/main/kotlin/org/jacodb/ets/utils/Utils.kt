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

import mu.KotlinLogging
import org.jacodb.ets.dto.EtsFileDto
import org.jacodb.ets.model.EtsFile
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

private val logger = KotlinLogging.logger {}

internal fun runProcess(cmd: List<String>, timeout: Duration? = null) {
    logger.info { "Running: '${cmd.joinToString(" ")}'" }
    val process = ProcessBuilder(cmd).start()
    val ok = if (timeout == null) {
        process.waitFor()
        true
    } else {
        process.waitFor(timeout.inWholeNanoseconds, TimeUnit.NANOSECONDS)
    }

    val stdout = process.inputStream.bufferedReader().readText().trim()
    if (stdout.isNotBlank()) {
        logger.info { "STDOUT:\n$stdout" }
    }
    val stderr = process.errorStream.bufferedReader().readText().trim()
    if (stderr.isNotBlank()) {
        logger.info { "STDERR:\n$stderr" }
    }

    if (!ok) {
        logger.info { "Timeout!" }
        process.destroy()
    }
}

/**
 * Returns the path to the sibling of this path with the given name.
 *
 * Usage:
 * ```
 * val path = Path("foo/bar.jpeg")
 * val sibling = path.resolveSibling { it.nameWithoutExtension + ".png" }
 * println(sibling) // foo/bar.png
 * ```
 */
internal fun Path.resolveSibling(name: (Path) -> String): Path {
    return resolveSibling(name(this))
}

fun EtsFileDto.toText(): String {
    val lines: MutableList<String> = mutableListOf()
    lines += "EtsFileDto '${name}':"
    classes.forEach { clazz ->
        lines += "= CLASS '${clazz.signature}':"
        lines += "  superClass = '${clazz.superClassName}'"
        lines += "  typeParameters = ${clazz.typeParameters}"
        lines += "  modifiers = ${clazz.modifiers}"
        lines += "  fields: ${clazz.fields.size}"
        clazz.fields.forEach { field ->
            lines += "  - FIELD '${field.signature}'"
            lines += "    typeParameters = ${field.typeParameters}"
            lines += "    modifiers = ${field.modifiers}"
            lines += "    isOptional = ${field.isOptional}"
            lines += "    isDefinitelyAssigned = ${field.isDefinitelyAssigned}"
        }
        lines += "  methods: ${clazz.methods.size}"
        clazz.methods.forEach { method ->
            lines += "  - METHOD '${method.signature}'"
            lines += "    typeParameters = ${method.typeParameters}"
            if (method.body != null) {
                lines += "    locals = ${method.body.locals}"
                lines += "    blocks: ${method.body.cfg.blocks.size}"
                method.body.cfg.blocks.forEach { block ->
                    lines += "    - BLOCK ${block.id}"
                    lines += "      successors = ${block.successors}"
                    lines += "      predecessors = ${block.predecessors}"
                    lines += "      statements: ${block.stmts.size}"
                    block.stmts.forEachIndexed { i, stmt ->
                        lines += "      ${i + 1}. $stmt"
                    }
                }
            }
        }
    }
    return lines.joinToString("\n")
}

fun EtsFile.toText(): String {
    val lines: MutableList<String> = mutableListOf()
    lines += "EtsFile '${name}':"
    classes.forEach { clazz ->
        lines += "= CLASS '${clazz.signature}':"
        lines += "  superClass = '${clazz.superClass}'"
        lines += "  fields: ${clazz.fields.size}"
        clazz.fields.forEach { field ->
            lines += "  - FIELD '${field.signature}'"
        }
        lines += "  constructor = '${clazz.ctor.signature}'"
        lines += "    stmts: ${clazz.ctor.cfg.stmts.size}"
        clazz.ctor.cfg.stmts.forEach { stmt ->
            lines += "    ${stmt.location.index}. $stmt"
            val pad = " ".repeat("${stmt.location.index}".length + 2) // number + dot + space
            lines += "    ${pad}successors = ${clazz.ctor.cfg.successors(stmt).map { it.location.index }}"
        }
        lines += "  methods: ${clazz.methods.size}"
        clazz.methods.forEach { method ->
            lines += "  - METHOD '${method.signature}':"
            lines += "    locals = ${method.localsCount}"
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
