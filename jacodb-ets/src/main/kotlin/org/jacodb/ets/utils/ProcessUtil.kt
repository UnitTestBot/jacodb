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
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
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

private const val PROCESS_TERMINATION_GRACE_MILLIS = 250L

private const val COMMUNICATION_SHUTDOWN_TIMEOUT_MILLIS = 250L

private const val UNKNOWN_EXIT_CODE = -1

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
        val pipeCloseJobs = if (isTimeout) {
            process.destroy()
            if (!process.waitFor(PROCESS_TERMINATION_GRACE_MILLIS, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly()
                process.waitFor(PROCESS_TERMINATION_GRACE_MILLIS, TimeUnit.MILLISECONDS)
            }
            // Descendants can retain the direct process's inherited pipe endpoints.
            // Close concurrently because one endpoint can itself block behind an active I/O job.
            listOf(
                scope.launch { runCatching { process.outputStream.close() } },
                scope.launch { runCatching { process.inputStream.close() } },
                scope.launch { runCatching { process.errorStream.close() } },
            )
        } else {
            emptyList()
        }
        runBlocking {
            val communicationJobs = listOf(stdinJob, stdoutJob, stderrJob)
            if (isTimeout) {
                val shutdownJobs = communicationJobs + pipeCloseJobs
                val completed = withTimeoutOrNull(COMMUNICATION_SHUTDOWN_TIMEOUT_MILLIS) {
                    shutdownJobs.joinAll()
                    true
                } == true
                if (!completed) {
                    shutdownJobs.forEach { it.cancel() }
                }
            } else {
                communicationJobs.joinAll()
            }
        }

        return Result(
            exitCode = if (process.isAlive) UNKNOWN_EXIT_CODE else process.exitValue(),
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
