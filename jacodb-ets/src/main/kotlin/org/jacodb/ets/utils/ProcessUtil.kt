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
import java.io.Reader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

private val logger = KotlinLogging.logger {}

/**
 * Cap on the captured stdout/stderr of a single process. Verbose frontends log a
 * line per file, so an unbounded buffer would grow to megabytes on big projects.
 */
private const val MAX_CAPTURED_OUTPUT_CHARS = 1 shl 20 // 1 MiB

private const val OUTPUT_TRUNCATION_NOTICE = "... (output truncated)"

private const val PROCESS_TERMINATION_GRACE_MILLIS = 250L

private const val PROCESS_FORCE_TERMINATION_TIMEOUT_MILLIS = 1_000L

private const val OUTPUT_READ_BUFFER_CHARS = 8 * 1024

private fun Path.readCapturedOutput(): String =
    Files.newBufferedReader(this, StandardCharsets.UTF_8).use { reader ->
        val result = StringBuilder()
        val buffer = CharArray(OUTPUT_READ_BUFFER_CHARS)
        while (result.length < MAX_CAPTURED_OUTPUT_CHARS) {
            val charsRead = reader.read(
                buffer,
                0,
                minOf(buffer.size, MAX_CAPTURED_OUTPUT_CHARS - result.length),
            )
            if (charsRead < 0) {
                return@use result.toString()
            }
            result.append(buffer, 0, charsRead)
        }
        if (reader.read() >= 0) {
            if (result.isNotEmpty() && result.last() != '\n') {
                result.appendLine()
            }
            result.append(OUTPUT_TRUNCATION_NOTICE)
        }
        result.toString()
    }

private fun terminateTimedOutProcess(process: Process) {
    process.destroy()
    if (process.waitFor(PROCESS_TERMINATION_GRACE_MILLIS, TimeUnit.MILLISECONDS)) {
        return
    }

    process.destroyForcibly()
    check(process.waitFor(PROCESS_FORCE_TERMINATION_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
        "Timed-out process did not terminate after destroyForcibly()"
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
        val reader = input?.reader() ?: "".reader()
        return run(command, reader, timeout)
    }

    /**
     * Runs [command] after streaming the finite [input] into a temporary file.
     * The process timeout starts after input reaches EOF and the process starts.
     */
    fun run(
        command: List<String>,
        input: Reader,
        timeout: Duration? = null,
    ): Result {
        logger.debug { "Running command: $command" }
        val ioDirectory = Files.createTempDirectory("jacodb-process-")
        val stdinFile = ioDirectory.resolve("stdin.txt")
        val stdoutFile = ioDirectory.resolve("stdout.txt")
        val stderrFile = ioDirectory.resolve("stderr.txt")
        ioDirectory.toFile().deleteOnExit()
        listOf(stdinFile, stdoutFile, stderrFile).forEach { it.toFile().deleteOnExit() }

        try {
            // A finite Reader is staged before the process starts, without loading it into memory.
            // Consequently, timeout measures process execution and not production of Reader input.
            Files.newBufferedWriter(stdinFile, StandardCharsets.UTF_8).use { writer ->
                input.copyTo(writer)
            }

            val process = ProcessBuilder(command)
                .redirectInput(stdinFile.toFile())
                .redirectOutput(stdoutFile.toFile())
                .redirectError(stderrFile.toFile())
                .start()

            val isTimeout = if (timeout != null) {
                !process.waitFor(timeout.inWholeNanoseconds, TimeUnit.NANOSECONDS)
            } else {
                process.waitFor()
                false
            }
            if (isTimeout) {
                terminateTimedOutProcess(process)
            }

            return Result(
                exitCode = process.exitValue(),
                stdout = stdoutFile.readCapturedOutput(),
                stderr = stderrFile.readCapturedOutput(),
                isTimeout = isTimeout,
            )
        } finally {
            ioDirectory.toFile().deleteRecursively()
        }
    }
}

fun main() {
    // Note: `ls -l /bin/` has big enough output to demonstrate the necessity
    //   of separate output capture threads/coroutines.
    val result = ProcessUtil.run(listOf("ls", "-l", "/bin/"))
    println("STDOUT: ${result.stdout}")
    println("STDERR: ${result.stderr}")
}
