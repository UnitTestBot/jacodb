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
import java.nio.charset.Charset
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

private val logger = KotlinLogging.logger {}

private fun terminateAndReap(
    process: Process,
    initialInterruption: InterruptedException? = null,
) {
    var interruption = initialInterruption
    process.destroy()
    if (process.isAlive) {
        process.destroyForcibly()
    }
    while (true) {
        try {
            process.waitFor()
            break
        } catch (error: InterruptedException) {
            if (interruption == null) {
                interruption = error
            } else if (interruption !== error) {
                interruption.addSuppressed(error)
            }
            if (process.isAlive) {
                process.destroyForcibly()
            }
        }
    }
    if (interruption != null) {
        Thread.currentThread().interrupt()
        throw interruption
    }
}

object ProcessUtil {
    data class Result(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
        val isTimeout: Boolean, // true if the process was terminated due to timeout
    )

    fun run(
        command: List<String>,
        input: String? = null,
        timeout: Duration? = null,
    ): Result {
        logger.debug { "Running command: $command" }
        val charset = Charset.defaultCharset()
        val tempDirectory = Files.createTempDirectory("jacodb-process-")
        try {
            val stdinFile = tempDirectory.resolve("stdin")
            val stdoutFile = tempDirectory.resolve("stdout")
            val stderrFile = tempDirectory.resolve("stderr")
            Files.newBufferedWriter(stdinFile, charset).use { writer ->
                writer.write(input ?: "")
            }

            val process = ProcessBuilder(command)
                .redirectInput(stdinFile.toFile())
                .redirectOutput(stdoutFile.toFile())
                .redirectError(stderrFile.toFile())
                .start()
            val isTimeout = try {
                if (timeout == null) {
                    process.waitFor()
                    false
                } else {
                    !process.waitFor(
                        timeout.inWholeNanoseconds.coerceAtLeast(0),
                        TimeUnit.NANOSECONDS,
                    )
                }
            } catch (error: InterruptedException) {
                terminateAndReap(process, error)
                throw error
            }

            if (isTimeout) {
                terminateAndReap(process)
            }

            return Result(
                exitCode = process.exitValue(),
                stdout = Files.readAllBytes(stdoutFile).toString(charset),
                stderr = Files.readAllBytes(stderrFile).toString(charset),
                isTimeout = isTimeout,
            )
        } finally {
            tempDirectory.toFile().deleteRecursively()
        }
    }
}
