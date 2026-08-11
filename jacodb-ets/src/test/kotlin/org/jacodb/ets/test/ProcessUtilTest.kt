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

package org.jacodb.ets.test

import org.jacodb.ets.utils.BoundedOutput
import org.jacodb.ets.utils.ProcessUtil
import org.jacodb.ets.utils.ProcessTerminationException
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Test
import java.io.InputStream
import java.nio.charset.Charset
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.createTempDirectory
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTime

class ProcessUtilTest {
    private val node = System.getenv("NODE_EXECUTABLE") ?: "node"

    private fun processIsAlive(pid: String): Boolean =
        ProcessBuilder(
            node,
            "-e",
            "try { process.kill($pid, 0); process.exit(0); } " +
                "catch (error) { process.exit(error.code === 'ESRCH' ? 1 : 2); }",
        ).start().waitFor() == 0

    private fun forceKill(pid: String) {
        ProcessBuilder(
            node,
            "-e",
            "try { process.kill($pid, 'SIGKILL'); } catch (error) { " +
                "if (error.code !== 'ESRCH') throw error; }",
        ).start().waitFor()
    }

    private fun waitForFile(path: Path) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
        while (!path.exists() && System.nanoTime() < deadline) {
            Thread.sleep(10)
        }
        assertTrue(path.exists(), "timed out waiting for $path")
    }

    @Test
    fun `captures a non-zero exit with stderr`() {
        val result = ProcessUtil.run(
            listOf(node, "-e", "console.error('boom'); process.exit(7)"),
        )

        assertEquals(7, result.exitCode)
        assertTrue(result.stderr.contains("boom"))
        assertTrue(!result.isTimeout)
    }

    @Test
    fun `interrupted termination transfers ownership of a live process`() {
        assumeFalse(System.getProperty("os.name").startsWith("Windows", ignoreCase = true))
        val testDirectory = createTempDirectory("process-util-interrupted-termination")
        val pidFile = testDirectory.resolve("pid")
        val signalFile = testDirectory.resolve("sigterm")
        val failure = AtomicReference<Throwable>()
        val interruptPreserved = AtomicBoolean()
        val runner = Thread {
            try {
                ProcessUtil.run(
                    listOf(
                        node,
                        "-e",
                        "const fs = require('fs'); " +
                            "fs.writeFileSync(process.argv[1], String(process.pid)); " +
                            "process.on('SIGTERM', () => fs.writeFileSync(process.argv[2], 'received')); " +
                            "setInterval(() => {}, 1000)",
                        pidFile.toString(),
                        signalFile.toString(),
                    ),
                    timeout = 100.milliseconds,
                )
            } catch (error: Throwable) {
                failure.set(error)
                interruptPreserved.set(Thread.currentThread().isInterrupted)
            }
        }

        runner.start()
        try {
            waitForFile(pidFile)
            waitForFile(signalFile)
            runner.interrupt()
            runner.join(TimeUnit.SECONDS.toMillis(2))

            assertFalse(runner.isAlive, "interrupted ProcessUtil runner did not return")
            val terminationError = assertIs<ProcessTerminationException>(failure.get())
            assertIs<InterruptedException>(terminationError.cause)
            assertTrue(interruptPreserved.get(), "interrupted status was not restored")
            assertTrue(terminationError.process.isAlive, "exception did not retain the live process")
            terminationError.process.destroyForcibly()
            assertTrue(
                terminationError.process.waitFor(2, TimeUnit.SECONDS),
                "caller could not reap the transferred process",
            )
        } finally {
            runner.interrupt()
            if (pidFile.exists()) {
                val pid = pidFile.readText().trim()
                if (processIsAlive(pid)) forceKill(pid)
            }
            runner.join(TimeUnit.SECONDS.toMillis(2))
            testDirectory.toFile().deleteRecursively()
        }
    }

    @Test
    fun `interrupting the initial wait reaps the process before propagating interruption`() {
        assumeFalse(System.getProperty("os.name").startsWith("Windows", ignoreCase = true))
        val testDirectory = createTempDirectory("process-util-interrupted-wait")
        val pidFile = testDirectory.resolve("pid")
        val signalFile = testDirectory.resolve("sigterm")
        val failure = AtomicReference<Throwable>()
        val interruptPreserved = AtomicBoolean()
        val runner = Thread {
            try {
                ProcessUtil.run(
                    listOf(
                        node,
                        "-e",
                        "const fs = require('fs'); " +
                            "fs.writeFileSync(process.argv[1], String(process.pid)); " +
                            "process.on('SIGTERM', () => fs.writeFileSync(process.argv[2], 'received')); " +
                            "setInterval(() => {}, 1000)",
                        pidFile.toString(),
                        signalFile.toString(),
                    ),
                    timeout = 30_000.milliseconds,
                )
            } catch (error: Throwable) {
                failure.set(error)
                interruptPreserved.set(Thread.currentThread().isInterrupted)
            }
        }

        runner.start()
        try {
            waitForFile(pidFile)
            val pid = pidFile.readText().trim()
            assertFalse(signalFile.exists(), "termination started before the test interruption")

            runner.interrupt()
            runner.join(TimeUnit.SECONDS.toMillis(3))

            assertFalse(runner.isAlive, "interrupted ProcessUtil runner did not return")
            assertIs<InterruptedException>(failure.get())
            assertFalse(
                processIsAlive(pid),
                "raw InterruptedException escaped while child process $pid was still alive",
            )
            assertTrue(interruptPreserved.get(), "interrupted status was not restored")
            assertTrue(signalFile.exists(), "interruption cleanup did not start graceful termination")
        } finally {
            runner.interrupt()
            if (pidFile.exists()) {
                val pid = pidFile.readText().trim()
                if (processIsAlive(pid)) forceKill(pid)
            }
            runner.join(TimeUnit.SECONDS.toMillis(2))
            testDirectory.toFile().deleteRecursively()
        }
    }

    @Test
    fun `reader input streams through a process without a timeout`() {
        val input = "reader-stream-input\n"

        val result = ProcessUtil.run(
            listOf(node, "-e", "process.stdin.pipe(process.stdout)"),
            input = input.reader(),
        )

        assertEquals(0, result.exitCode)
        assertEquals(input, result.stdout)
        assertFalse(result.isTimeout)
    }

    @Test
    fun `reader input with a timeout is rejected before process start`() {
        val startedFile = createTempFile("process-util-reader-started")
        startedFile.deleteIfExists()

        try {
            assertFailsWith<IllegalArgumentException> {
                ProcessUtil.run(
                    listOf(
                        node,
                        "-e",
                        "require('fs').writeFileSync(process.argv[1], 'started')",
                        startedFile.toString(),
                    ),
                    input = "finite reader".reader(),
                    timeout = 100.milliseconds,
                )
            }
            assertFalse(startedFile.exists(), "timed Reader command started before rejection")
        } finally {
            startedFile.deleteIfExists()
        }
    }

    @Test
    fun `one output pass returns after 64 KiB so the other stream can drain`() {
        class FiniteAvailableInputStream(private var remaining: Int) : InputStream() {
            var consumed = 0
                private set

            override fun available(): Int = remaining

            override fun read(): Int {
                if (remaining == 0) return -1
                remaining--
                consumed++
                return 0
            }

            override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                if (remaining == 0) return -1
                val bytesRead = minOf(length, remaining)
                bytes.fill(0, offset, offset + bytesRead)
                remaining -= bytesRead
                consumed += bytesRead
                return bytesRead
            }
        }

        val stdoutStream = FiniteAvailableInputStream(128 * 1024)
        val stderrStream = FiniteAvailableInputStream(128 * 1024)
        val stdout = BoundedOutput(Charset.defaultCharset())
        val stderr = BoundedOutput(Charset.defaultCharset())

        stdout.drainAvailable(stdoutStream)
        stderr.drainAvailable(stderrStream)

        assertEquals(64 * 1024, stdoutStream.consumed)
        assertEquals(64 * 1024, stderrStream.consumed)
        assertEquals(64 * 1024, stdoutStream.available())
        assertEquals(64 * 1024, stderrStream.available())
    }

    @Test
    fun `truncated UTF-8 output omits an incomplete trailing character`() {
        val output = BoundedOutput(Charsets.UTF_8)
        val asciiPrefix = ByteArray((1 shl 20) - 1) { 'a'.code.toByte() }
        val multibyteCharacter = "\u20ac".toByteArray(Charsets.UTF_8)

        output.append(asciiPrefix, asciiPrefix.size)
        output.append(multibyteCharacter, multibyteCharacter.size)

        val captured = output.toString()
        assertFalse(
            '\uFFFD' in captured,
            "byte-level truncation decoded an incomplete UTF-8 character as U+FFFD",
        )
        assertTrue(captured.endsWith("... (output truncated)"))
    }

    @Test
    fun `continuous stdout cannot starve timeout stderr or inherited sink closure`() {
        assumeFalse(System.getProperty("os.name").startsWith("Windows", ignoreCase = true))
        val testDirectory = createTempDirectory("process-util-continuous-output")
        val directPidFile = testDirectory.resolve("direct-pid")
        val descendantPidFile = testDirectory.resolve("descendant-pid")
        val triggerFile = testDirectory.resolve("trigger")
        val statusFile = testDirectory.resolve("status")
        val noisePidFiles = (0 until 8).map { testDirectory.resolve("noise-pid-$it") }
        val noisePidPrefix = testDirectory.resolve("noise-pid-")
        val descendantScript =
            "const fs = require('fs'); " +
                "fs.writeFileSync(process.argv[1], String(process.pid)); " +
                "const chunk = Buffer.alloc(1 << 20, 120); " +
                "const outputDeadline = Date.now() + 5000; " +
                "while (Date.now() < outputDeadline) { " +
                "try { fs.writeSync(1, chunk); } catch (_) { break; } " +
                "} " +
                "const timer = setInterval(() => { " +
                "if (!fs.existsSync(process.argv[2])) return; " +
                "clearInterval(timer); " +
                "let status = 'closed'; " +
                "try { fs.writeSync(1, chunk); status = 'writable'; } catch (_) {} " +
                "fs.writeFileSync(process.argv[3], status); " +
                "}, 10)"
        val noiseScript =
            "const fs = require('fs'); const chunk = Buffer.alloc(1 << 20, 121); " +
                "const outputDeadline = Date.now() + 5000; " +
                "while (Date.now() < outputDeadline) { " +
                "try { fs.writeSync(1, chunk); } catch (_) { break; } " +
                "}"
        val directScript =
            "const fs = require('fs'); const { spawn } = require('child_process'); " +
                "fs.writeFileSync(process.argv[1], String(process.pid)); " +
                "spawn(process.execPath, ['-e', process.argv[2], process.argv[3], process.argv[4], process.argv[5]], " +
                "{ stdio: ['inherit', 'inherit', 'inherit'] }); " +
                "for (let i = 0; i < 8; i++) { " +
                "const noise = spawn(process.execPath, ['-e', process.argv[6]], " +
                "{ stdio: ['inherit', 'inherit', 'inherit'] }); " +
                "fs.writeFileSync(process.argv[7] + i, String(noise.pid)); " +
                "} " +
                "const marker = setInterval(() => { " +
                "if (!fs.existsSync(process.argv[3])) return; " +
                "clearInterval(marker); console.error('stderr-during-continuous-stdout'); " +
                "}, 1); " +
                "process.on('SIGTERM', () => {}); " +
                "setInterval(() => {}, 1000)"

        try {
            lateinit var result: ProcessUtil.Result
            val elapsed = measureTime {
                result = ProcessUtil.run(
                    listOf(
                        node,
                        "-e",
                        directScript,
                        directPidFile.toString(),
                        descendantScript,
                        descendantPidFile.toString(),
                        triggerFile.toString(),
                        statusFile.toString(),
                        noiseScript,
                        noisePidPrefix.toString(),
                    ),
                    timeout = 1000.milliseconds,
                )
            }

            noisePidFiles.forEach(::waitForFile)
            val directPid = directPidFile.readText().trim()
            assertTrue(result.isTimeout)
            assertTrue(result.stderr.contains("stderr-during-continuous-stdout"))
            assertFalse(processIsAlive(directPid), "direct process $directPid was still alive after timeout")
            assertEquals(137, result.exitCode, "timeout must return the direct process's SIGKILL exit status")
            assertTrue(elapsed < 2500.milliseconds, "continuous stdout delayed timeout completion by $elapsed")

            triggerFile.writeText("")
            waitForFile(statusFile)
            assertEquals(
                "closed",
                statusFile.readText(),
                "continuous-output descendant retained a writable output sink",
            )
        } finally {
            for (pidFile in listOf(directPidFile, descendantPidFile) + noisePidFiles) {
                if (pidFile.exists()) {
                    val pid = pidFile.readText().trim()
                    if (processIsAlive(pid)) forceKill(pid)
                }
            }
            testDirectory.toFile().deleteRecursively()
        }
    }

    @Test
    fun `timeout reaps the direct process while a descendant retains inherited streams`() {
        assumeFalse(System.getProperty("os.name").startsWith("Windows", ignoreCase = true))
        val pidFile = createTempFile("process-util-direct-pid")
        lateinit var result: ProcessUtil.Result
        val elapsed = measureTime {
            result = ProcessUtil.run(
                listOf(
                    node,
                    "-e",
                    "const { spawn } = require('child_process'); " +
                        "require('fs').writeFileSync(process.argv[1], String(process.pid)); " +
                        "spawn(process.execPath, ['-e', 'setTimeout(() => {}, 5000)'], " +
                        "{ stdio: ['inherit', 'inherit', 'inherit'] }); " +
                        "console.log('stdout-before-timeout'); " +
                        "console.error('stderr-before-timeout'); " +
                        "process.on('SIGTERM', () => {}); " +
                        "setInterval(() => {}, 1000)",
                    pidFile.toString(),
                ),
                input = "x".repeat(1 shl 20),
                timeout = 100.milliseconds,
            )
        }

        val directPid = pidFile.readText().trim()
        try {
            assertTrue(result.isTimeout)
            assertTrue(result.stdout.contains("stdout-before-timeout"))
            assertTrue(result.stderr.contains("stderr-before-timeout"))
            assertFalse(processIsAlive(directPid), "direct process $directPid was still alive after timeout")
            assertEquals(137, result.exitCode, "timeout must return the direct process's SIGKILL exit status")
            assertTrue(elapsed.inWholeSeconds < 2, "inherited streams delayed timeout completion by $elapsed")
        } finally {
            if (processIsAlive(directPid)) {
                forceKill(directPid)
            }
            pidFile.deleteIfExists()
        }
    }
}
