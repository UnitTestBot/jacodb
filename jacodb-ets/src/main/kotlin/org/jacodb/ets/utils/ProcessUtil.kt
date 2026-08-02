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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import java.io.Reader
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

private val logger = KotlinLogging.logger {}

/**
 * Cap on the captured stdout/stderr of a single process. Verbose frontends log a
 * line per file, so an unbounded buffer would grow to megabytes on big projects.
 */
private const val MAX_CAPTURED_OUTPUT_CHARS = 1 shl 20 // 1 MiB

private const val OUTPUT_TRUNCATION_NOTICE = "... (output truncated)"

private fun StringBuilder.appendLineBounded(line: String) {
    if (length >= MAX_CAPTURED_OUTPUT_CHARS) return
    appendLine(line)
    if (length >= MAX_CAPTURED_OUTPUT_CHARS) {
        appendLine(OUTPUT_TRUNCATION_NOTICE)
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

    fun run(
        command: List<String>,
        input: Reader,
        timeout: Duration? = null,
    ): Result {
        logger.debug { "Running command: $command" }
        val process = ProcessBuilder(command).start()
        return communicate(process, input, timeout)
    }

    private fun communicate(
        process: Process,
        input: Reader,
        timeout: Duration? = null,
    ): Result {
        val stdout = StringBuilder()
        val stderr = StringBuilder()

        // SupervisorJob: a broken pipe in the stdin writer (typical after `destroy()`)
        // must not cancel the stdout/stderr readers, otherwise the captured logs
        // would be empty exactly in the timeout scenario where they matter most.
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        // Handle process input
        val stdinJob = scope.launch {
            process.outputStream.bufferedWriter().use { writer ->
                input.copyTo(writer)
            }
        }

        // Launch output capture coroutines
        val stdoutJob = scope.launch {
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { stdout.appendLineBounded(it) }
            }
        }
        val stderrJob = scope.launch {
            process.errorStream.bufferedReader().useLines { lines ->
                lines.forEach { stderr.appendLineBounded(it) }
            }
        }

        // Wait for completion
        val isTimeout = if (timeout != null) {
            !process.waitFor(timeout.inWholeNanoseconds, TimeUnit.NANOSECONDS)
        } else {
            process.waitFor()
            false
        }
        if (isTimeout) {
            process.destroy()
            if (!process.waitFor(250, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly()
                process.waitFor()
            }
        }
        runBlocking {
            stdinJob.join()
            stdoutJob.join()
            stderrJob.join()
        }

        return Result(
            exitCode = process.exitValue(),
            stdout = stdout.toString(),
            stderr = stderr.toString(),
            isTimeout = isTimeout,
        )
    }
}

fun main() {
    // Note: `ls -l /bin/` has big enough output to demonstrate the necessity
    //   of separate output capture threads/coroutines.
    val result = ProcessUtil.run(listOf("ls", "-l", "/bin/"))
    println("STDOUT: ${result.stdout}")
    println("STDERR: ${result.stderr}")
}
