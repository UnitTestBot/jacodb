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
import java.io.BufferedWriter
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

private val logger = KotlinLogging.logger {}

internal fun BufferedWriter.writeln(s: String) {
    write(s)
    newLine()
}

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

fun EtsFileDto.dumpContentTo(output: BufferedWriter) {
    output.writeln("EtsFileDto '${name}':")
    classes.forEach { clazz ->
        output.writeln("= CLASS '${clazz.signature}':")
        output.writeln("  superClass = '${clazz.superClassName}'")
        output.writeln("  typeParameters = ${clazz.typeParameters}")
        output.writeln("  modifiers = ${clazz.modifiers}")
        output.writeln("  fields: ${clazz.fields.size}")
        clazz.fields.forEach { field ->
            output.writeln("  - FIELD '${field.signature}'")
            output.writeln("    typeParameters = ${field.typeParameters}")
            output.writeln("    modifiers = ${field.modifiers}")
            output.writeln("    isOptional = ${field.isOptional}")
            output.writeln("    isDefinitelyAssigned = ${field.isDefinitelyAssigned}")
        }
        output.writeln("  methods: ${clazz.methods.size}")
        clazz.methods.forEach { method ->
            output.writeln("  - METHOD '${method.signature}':")
            output.writeln("    locals = ${method.body.locals}")
            output.writeln("    typeParameters = ${method.typeParameters}")
            output.writeln("    blocks: ${method.body.cfg.blocks.size}")
            method.body.cfg.blocks.forEach { block ->
                output.writeln("    - BLOCK ${block.id} with ${block.stmts.size} statements:")
                block.stmts.forEachIndexed { i, inst ->
                    output.writeln("      ${i + 1}. $inst")
                }
            }
        }
    }
}

fun EtsFile.dumpContentTo(output: BufferedWriter) {
    output.writeln("EtsFile '${name}':")
    classes.forEach { clazz ->
        output.writeln("= CLASS '${clazz.signature}':")
        output.writeln("  superClass = '${clazz.superClass}'")
        output.writeln("  fields: ${clazz.fields.size}")
        clazz.fields.forEach { field ->
            output.writeln("  - FIELD '${field.signature}'")
        }
        output.writeln("  constructor = '${clazz.ctor.signature}'")
        output.writeln("    stmts: ${clazz.ctor.cfg.stmts.size}")
        clazz.ctor.cfg.stmts.forEachIndexed { i, stmt ->
            output.writeln("    ${i + 1}. $stmt")
        }
        output.writeln("  methods: ${clazz.methods.size}")
        clazz.methods.forEach { method ->
            output.writeln("  - METHOD '${method.signature}':")
            output.writeln("    locals = ${method.localsCount}")
            output.writeln("    stmts: ${method.cfg.stmts.size}")
            method.cfg.stmts.forEachIndexed { i, stmt ->
                output.writeln("    ${i + 1}. $stmt")
            }
        }
    }
}
