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
