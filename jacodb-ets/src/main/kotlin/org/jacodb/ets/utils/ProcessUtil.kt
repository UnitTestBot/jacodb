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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.Reader
import java.nio.charset.Charset
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

private val logger = KotlinLogging.logger {}

/**
 * Cap on the captured stdout/stderr of a single process. Verbose frontends log a
 * line per file, so an unbounded buffer would grow to megabytes on big projects.
 */
private const val MAX_CAPTURED_OUTPUT_BYTES = 1 shl 20 // 1 MiB

private const val OUTPUT_TRUNCATION_NOTICE = "... (output truncated)"

private const val PROCESS_TERMINATION_GRACE_MILLIS = 250L

private const val PROCESS_FORCE_TERMINATION_TIMEOUT_MILLIS = 1_000L

private const val OUTPUT_READ_BUFFER_BYTES = 8 * 1024

private const val PROCESS_POLL_INTERVAL_MILLIS = 10L

class ProcessTerminationException(
    /** The caller owns this still-live process and its streams. */
    val process: Process,
) : IllegalStateException("Timed-out process did not terminate after destroyForcibly()")

private class BoundedOutput(private val charset: Charset) {
    private val output = ByteArrayOutputStream()
    private val buffer = ByteArray(OUTPUT_READ_BUFFER_BYTES)
    private var truncated = false

    fun append(bytes: ByteArray, length: Int) {
        val retained = minOf(length, MAX_CAPTURED_OUTPUT_BYTES - output.size())
        if (retained > 0) {
            output.write(bytes, 0, retained)
        }
        if (retained < length) {
            truncated = true
        }
    }

    fun drainAvailable(stream: InputStream) {
        while (true) {
            val available = try {
                stream.available()
            } catch (_: Exception) {
                return
            }
            if (available <= 0) return

            val bytesRead = try {
                stream.read(buffer, 0, minOf(buffer.size, available))
            } catch (_: Exception) {
                return
            }
            if (bytesRead < 0) return
            append(buffer, bytesRead)
        }
    }

    fun drain(stream: InputStream) {
        while (true) {
            val bytesRead = stream.read(buffer)
            if (bytesRead < 0) return
            append(buffer, bytesRead)
        }
    }

    override fun toString(): String {
        val captured = output.toString(charset.name())
        if (!truncated) return captured
        return buildString(captured.length + OUTPUT_TRUNCATION_NOTICE.length + 1) {
            append(captured)
            if (isNotEmpty() && last() != '\n') appendLine()
            append(OUTPUT_TRUNCATION_NOTICE)
        }
    }
}

private fun terminateTimedOutProcess(process: Process) {
    process.destroy()
    if (process.waitFor(PROCESS_TERMINATION_GRACE_MILLIS, TimeUnit.MILLISECONDS)) {
        return
    }

    process.destroyForcibly()
    if (!process.waitFor(PROCESS_FORCE_TERMINATION_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
        throw ProcessTerminationException(process)
    }
}

private fun waitForProcess(
    process: Process,
    timeout: Duration?,
    stdout: BoundedOutput,
    stderr: BoundedOutput,
): Boolean {
    val started = System.nanoTime()
    val timeoutNanos = timeout?.inWholeNanoseconds?.coerceAtLeast(0)
    while (true) {
        stdout.drainAvailable(process.inputStream)
        stderr.drainAvailable(process.errorStream)

        if (process.waitFor(0, TimeUnit.NANOSECONDS)) {
            return false
        }
        val elapsed = System.nanoTime() - started
        if (timeoutNanos != null && elapsed >= timeoutNanos) {
            return true
        }
        val waitNanos = if (timeoutNanos == null) {
            TimeUnit.MILLISECONDS.toNanos(PROCESS_POLL_INTERVAL_MILLIS)
        } else {
            minOf(
                TimeUnit.MILLISECONDS.toNanos(PROCESS_POLL_INTERVAL_MILLIS),
                timeoutNanos - elapsed,
            )
        }
        if (process.waitFor(waitNanos, TimeUnit.NANOSECONDS)) {
            return false
        }
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
        val stdinFile = Files.createTempFile("jacodb-process-stdin-", ".txt")
        try {
            Files.newBufferedWriter(stdinFile, charset).use { writer ->
                writer.write(input ?: "")
            }

            val process = ProcessBuilder(command)
                .redirectInput(stdinFile.toFile())
                .start()
            val stdout = BoundedOutput(charset)
            val stderr = BoundedOutput(charset)
            var callerOwnsProcess = false
            try {
                val isTimeout = waitForProcess(process, timeout, stdout, stderr)
                if (isTimeout) {
                    try {
                        terminateTimedOutProcess(process)
                    } catch (error: ProcessTerminationException) {
                        callerOwnsProcess = true
                        throw error
                    }
                }
                stdout.drainAvailable(process.inputStream)
                stderr.drainAvailable(process.errorStream)

                return Result(
                    exitCode = process.exitValue(),
                    stdout = stdout.toString(),
                    stderr = stderr.toString(),
                    isTimeout = isTimeout,
                )
            } finally {
                if (!callerOwnsProcess) {
                    process.inputStream.close()
                    process.errorStream.close()
                }
            }
        } finally {
            try {
                Files.deleteIfExists(stdinFile)
            } catch (_: Exception) {
                stdinFile.toFile().deleteOnExit()
            }
        }
    }

    /**
     * Streams [input] through [command]. Timed execution requires finite String input,
     * because arbitrary Reader I/O cannot be cancelled safely on the JDK 8 baseline.
     */
    fun run(
        command: List<String>,
        input: Reader,
        timeout: Duration? = null,
    ): Result {
        require(timeout == null) {
            "ProcessUtil.run with Reader input does not support timeout; use finite String input"
        }
        logger.debug { "Running command: $command" }
        val process = ProcessBuilder(command).start()
        val charset = Charset.defaultCharset()
        val stdout = BoundedOutput(charset)
        val stderr = BoundedOutput(charset)
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val stdinJob = scope.launch {
            process.outputStream.bufferedWriter(charset).use { writer ->
                input.copyTo(writer)
            }
        }
        val stdoutJob = scope.launch {
            process.inputStream.use { stream ->
                stdout.drain(stream)
            }
        }
        val stderrJob = scope.launch {
            process.errorStream.use { stream ->
                stderr.drain(stream)
            }
        }

        process.waitFor()
        runBlocking {
            listOf(stdinJob, stdoutJob, stderrJob).joinAll()
        }
        scope.cancel()
        return Result(
            exitCode = process.exitValue(),
            stdout = stdout.toString(),
            stderr = stderr.toString(),
            isTimeout = false,
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
