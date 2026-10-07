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
import java.lang.reflect.InvocationTargetException
import java.nio.charset.Charset
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import java.util.stream.Stream
import kotlin.time.Duration

private val logger = KotlinLogging.logger {}

/** Java 8 exposes parent cleanup only; process-tree cleanup is available on Java 9 and later. */
private fun destroyDescendantsIfSupported(process: Process) {
    val handleClass = try {
        Class.forName("java.lang.ProcessHandle")
    } catch (_: ClassNotFoundException) {
        return
    }

    try {
        val handle = Process::class.java.getMethod("toHandle").invoke(process)
        val descendants = handleClass.getMethod("descendants").invoke(handle) as Stream<*>
        val destroy = handleClass.getMethod("destroyForcibly")
        descendants.use { children ->
            children.forEach { child -> destroy.invoke(child) }
        }
    } catch (error: InvocationTargetException) {
        val cause = error.targetException
        if (cause is RuntimeException) throw cause
        throw IllegalStateException("Failed to terminate process descendants", cause)
    } catch (error: ReflectiveOperationException) {
        throw IllegalStateException("Cannot access process-tree cleanup on this runtime", error)
    }
}

private fun terminateAndReap(
    process: Process,
    initialInterruption: InterruptedException? = null,
) {
    var interruption = initialInterruption
    var cleanupFailure: RuntimeException? = null
    try {
        destroyDescendantsIfSupported(process)
    } catch (error: RuntimeException) {
        cleanupFailure = error
    }
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
        cleanupFailure?.let { interruption.addSuppressed(it) }
        Thread.currentThread().interrupt()
        throw interruption
    }
    cleanupFailure?.let { throw it }
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
