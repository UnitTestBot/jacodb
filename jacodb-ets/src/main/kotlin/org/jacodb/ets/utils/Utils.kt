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

fun EtsFileDto.dumpContentTo(output: BufferedWriter): Unit = with(output) {
    writeln("EtsFileDto '${name}':")
    classes.forEach { clazz ->
        writeln("= CLASS '${clazz.signature}':")
        writeln("  superClass = '${clazz.superClassName}'")
        writeln("  typeParameters = ${clazz.typeParameters}")
        writeln("  modifiers = ${clazz.modifiers}")
        writeln("  fields: ${clazz.fields.size}")
        clazz.fields.forEach { field ->
            writeln("  - FIELD '${field.signature}'")
            writeln("    typeParameters = ${field.typeParameters}")
            writeln("    modifiers = ${field.modifiers}")
            writeln("    isOptional = ${field.isOptional}")
            writeln("    isDefinitelyAssigned = ${field.isDefinitelyAssigned}")
        }
        writeln("  methods: ${clazz.methods.size}")
        clazz.methods.forEach { method ->
            writeln("  - METHOD '${method.signature}':")
            writeln("    locals = ${method.body.locals}")
            writeln("    typeParameters = ${method.typeParameters}")
            writeln("    blocks: ${method.body.cfg.blocks.size}")
            method.body.cfg.blocks.forEach { block ->
                writeln("    - BLOCK ${block.id} with ${block.stmts.size} statements:")
                block.stmts.forEachIndexed { i, inst ->
                    writeln("      ${i + 1}. $inst")
                }
            }
        }
    }
}

fun EtsFile.dumpContentTo(output: BufferedWriter): Unit = with(output) {
    writeln("EtsFile '${name}':")
    classes.forEach { clazz ->
        writeln("= CLASS '${clazz.signature}':")
        writeln("  superClass = '${clazz.superClass}'")
        writeln("  fields: ${clazz.fields.size}")
        clazz.fields.forEach { field ->
            writeln("  - FIELD '${field.signature}'")
        }
        writeln("  constructor = '${clazz.ctor.signature}'")
        writeln("    stmts: ${clazz.ctor.cfg.stmts.size}")
        clazz.ctor.cfg.stmts.forEachIndexed { i, stmt ->
            writeln("    ${i + 1}. $stmt")
        }
        writeln("  methods: ${clazz.methods.size}")
        clazz.methods.forEach { method ->
            writeln("  - METHOD '${method.signature}':")
            writeln("    locals = ${method.localsCount}")
            writeln("    stmts: ${method.cfg.stmts.size}")
            method.cfg.stmts.forEachIndexed { i, stmt ->
                writeln("    ${i + 1}. $stmt")
            }
        }
    }
}
