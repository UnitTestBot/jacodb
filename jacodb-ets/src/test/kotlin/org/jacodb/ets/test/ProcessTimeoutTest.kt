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

import org.jacodb.ets.utils.ProcessUtil
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.IOException
import java.io.Reader
import java.nio.file.Files
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class ProcessTimeoutTest {
    private val supportsDescendants = runCatching { Class.forName("java.lang.ProcessHandle") }.isSuccess

    @Test
    fun `timeout terminates and reaps the process before pipe readers finish`() {
        val pidFile = Files.createTempFile("process-timeout-test-", ".pid")
        val executor = Executors.newSingleThreadExecutor()
        val result = executor.submit<ProcessUtil.Result> {
            ProcessUtil.run(
                command = listOf("/bin/sh", "-c", "echo $$ > '$pidFile'; exec sleep 30"),
                timeout = 300.milliseconds,
            )
        }

        try {
            val completed = result.get(3, TimeUnit.SECONDS)
            val pid = Files.readAllLines(pidFile).joinToString(separator = "\n").trim().toLong()

            assertTrue(completed.isTimeout)
            assertFalse(isProcessAlive(pid))
        } finally {
            Files.readAllLines(pidFile).joinToString(separator = "\n").trim().toLongOrNull()?.let { pid ->
                terminateTestProcess(pid)
            }
            result.cancel(true)
            executor.shutdownNow()
            Files.deleteIfExists(pidFile)
        }
    }

    @Test
    fun `timeout also terminates a child holding inherited stdout and stderr`() {
        assumeTrue(
            supportsDescendants,
            "Java 8 supports parent-only cleanup; descendant cleanup requires Java 9 or later",
        )
        val pidFile = Files.createTempFile("process-tree-timeout-test-", ".pid")
        val executor = Executors.newSingleThreadExecutor()
        val result = executor.submit<ProcessUtil.Result> {
            ProcessUtil.run(
                command = listOf("/bin/sh", "-c", "echo $$ > '$pidFile'; sleep 30 & echo $! >> '$pidFile'; wait"),
                timeout = 300.milliseconds,
            )
        }

        try {
            val completed = result.get(3, TimeUnit.SECONDS)
            val pids = Files.readAllLines(pidFile).map(String::toLong)

            val allProcessesStopped = pids.all { pid -> !isProcessAlive(pid) }

            assertEquals(expected = 2, actual = pids.size)
            assertTrue(completed.isTimeout)
            assertTrue(allProcessesStopped)
        } finally {
            Files.readAllLines(pidFile).mapNotNull(String::toLongOrNull).forEach { pid ->
                terminateTestProcess(pid)
            }
            result.cancel(true)
            executor.shutdownNow()
            Files.deleteIfExists(pidFile)
        }
    }

    @Test
    fun `interrupting an unbounded wait terminates and reaps the process tree`() {
        assertInterruptCleanup(timeout = null)
    }

    @Test
    fun `interrupting a bounded wait terminates and reaps the process tree`() {
        assertInterruptCleanup(timeout = 30_000.milliseconds)
    }

    private fun assertInterruptCleanup(timeout: Duration?) {
        val pidFile = Files.createTempFile("process-tree-interruption-test-", ".pid")
        val outcome = CompletableFuture<Pair<Throwable?, Boolean>>()
        val command = if (supportsDescendants) {
            "echo $$ > '$pidFile'; sleep 30 & echo $! >> '$pidFile'; wait"
        } else {
            "echo $$ > '$pidFile'; exec sleep 30"
        }
        val expectedPidCount = if (supportsDescendants) 2 else 1
        val worker = Thread {
            try {
                ProcessUtil.run(
                    command = listOf("/bin/sh", "-c", command),
                    timeout = timeout,
                )
                outcome.complete(null to Thread.currentThread().isInterrupted)
            } catch (failure: Throwable) {
                outcome.complete(failure to Thread.currentThread().isInterrupted)
            }
        }
        worker.start()

        try {
            val startDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
            while (Files.readAllLines(pidFile).size < expectedPidCount && System.nanoTime() < startDeadline) {
                Thread.sleep(10)
            }
            val pids = Files.readAllLines(pidFile).map(String::toLong)
            assertEquals(expected = expectedPidCount, actual = pids.size)

            worker.interrupt()
            val (failure, interruptRestored) = outcome.get(3, TimeUnit.SECONDS)
            val allProcessesStopped = pids.all { pid -> !isProcessAlive(pid) }

            assertIs<InterruptedException>(failure)
            assertTrue(
                allProcessesStopped,
                "Interrupted wait must terminate the parent and any supported descendant",
            )
            assertTrue(interruptRestored)
        } finally {
            Files.readAllLines(pidFile).mapNotNull(String::toLongOrNull).forEach { pid ->
                terminateTestProcess(pid)
            }
            worker.interrupt()
            worker.join(1_000)
            Files.deleteIfExists(pidFile)
        }
    }

    private fun isProcessAlive(pid: Long): Boolean {
        if (supportsDescendants) {
            val handleClass = Class.forName("java.lang.ProcessHandle")
            val optionalHandle = handleClass.getMethod("of", Long::class.javaPrimitiveType)
                .invoke(null, pid) as java.util.Optional<*>
            val handle = optionalHandle.orElse(null) ?: return false
            return handleClass.getMethod("isAlive").invoke(handle) as Boolean
        }

        val process = ProcessBuilder("/bin/kill", "-0", pid.toString()).redirectErrorStream(true).start()
        process.inputStream.close()
        return process.waitFor() == 0
    }

    private fun terminateTestProcess(pid: Long) {
        val process = ProcessBuilder("/bin/kill", "-9", pid.toString()).redirectErrorStream(true).start()
        process.inputStream.close()
        process.waitFor()
    }

    @Test
    fun `parent exit does not wait for an orphan holding inherited output`() {
        val pidFile = Files.createTempFile("orphan-output-test-", ".pid")
        val executor = Executors.newSingleThreadExecutor()
        val result = executor.submit<ProcessUtil.Result> {
            ProcessUtil.run(
                command = listOf(
                    "/bin/sh", "-c",
                    "sleep 30 & echo $! > '$pidFile'; printf parent; printf diagnostic >&2; exit 7",
                ),
                timeout = 300.milliseconds,
            )
        }

        try {
            val completed = result.get(3, TimeUnit.SECONDS)

            assertEquals(expected = 7, actual = completed.exitCode)
            assertEquals(expected = "parent", actual = completed.stdout)
            assertEquals(expected = "diagnostic", actual = completed.stderr)
            assertFalse(completed.isTimeout)
        } finally {
            Files.readAllLines(pidFile).mapNotNull(String::toLongOrNull).forEach { pid ->
                terminateTestProcess(pid)
            }
            result.cancel(true)
            executor.shutdownNow()
            Files.deleteIfExists(pidFile)
        }
    }

    @Test
    fun `timeout interrupts and joins a cooperative input reader`() {
        val entered = CountDownLatch(1)
        val stopped = CountDownLatch(1)
        val release = CountDownLatch(1)
        val input = object : Reader() {
            override fun read(buffer: CharArray, offset: Int, length: Int): Int {
                entered.countDown()
                try {
                    release.await()
                    return -1
                } finally {
                    stopped.countDown()
                }
            }

            override fun close() = Unit
        }
        val executor = Executors.newSingleThreadExecutor()
        val result = executor.submit<ProcessUtil.Result> {
            ProcessUtil.run(
                command = listOf("/bin/sh", "-c", "exec sleep 30"),
                input = input,
                timeout = 300.milliseconds,
            )
        }

        try {
            assertTrue(entered.await(3, TimeUnit.SECONDS))
            val completed = result.get(3, TimeUnit.SECONDS)

            assertTrue(completed.isTimeout)
            assertEquals(expected = 0L, actual = stopped.count)
        } finally {
            release.countDown()
            result.cancel(true)
            executor.shutdownNow()
        }
    }

    @Test
    fun `noncooperative input fails within the cleanup deadline`() {
        val entered = CountDownLatch(1)
        val stopped = CountDownLatch(1)
        val release = CountDownLatch(1)
        val input = object : Reader() {
            override fun read(buffer: CharArray, offset: Int, length: Int): Int {
                entered.countDown()
                try {
                    while (release.count != 0L) {
                        try {
                            release.await()
                        } catch (_: InterruptedException) {
                            // Deliberately simulate a caller reader that ignores cancellation.
                        }
                    }
                    return -1
                } finally {
                    stopped.countDown()
                }
            }

            override fun close() = Unit
        }
        val executor = Executors.newSingleThreadExecutor()
        val result = executor.submit<ProcessUtil.Result> {
            ProcessUtil.run(
                command = listOf("/bin/sh", "-c", "exec sleep 30"),
                input = input,
                timeout = 300.milliseconds,
            )
        }

        try {
            assertTrue(entered.await(3, TimeUnit.SECONDS))
            val failure = assertFailsWith<ExecutionException> { result.get(3, TimeUnit.SECONDS) }
            val cause = assertIs<IllegalStateException>(failure.cause)

            assertEquals(
                expected = "Process input did not stop after interruption within the cleanup deadline",
                actual = cause.message,
            )
        } finally {
            release.countDown()
            result.cancel(true)
            executor.shutdownNow()
            assertTrue(stopped.await(3, TimeUnit.SECONDS))
        }
    }

    @Test
    fun `input read failures are preserved when the child exits`() {
        val input = object : Reader() {
            override fun read(buffer: CharArray, offset: Int, length: Int): Int {
                throw IOException("cannot read input")
            }

            override fun close() = Unit
        }

        val failure = assertFailsWith<IOException> {
            ProcessUtil.run(
                command = listOf("/bin/sh", "-c", "cat"),
                input = input,
                timeout = 1_000.milliseconds,
            )
        }

        assertEquals(expected = "cannot read input", actual = failure.message)
    }

    @Test
    fun `capture merges stderr when requested by the builder`() {
        val result = ProcessUtil.run(
            command = listOf("/bin/sh", "-c", "printf out; printf err >&2"),
            timeout = 1_000.milliseconds,
            builder = { redirectErrorStream(true) },
        )

        assertEquals(expected = "outerr", actual = result.stdout)
        assertEquals(expected = "", actual = result.stderr)
        assertFalse(result.isTimeout)
    }

    @Test
    fun `completed process preserves stdin stdout stderr and exit status`() {
        val result = ProcessUtil.run(
            command = listOf("/bin/sh", "-c", "cat; echo diagnostic >&2; exit 7"),
            input = "payload\n".reader(),
            timeout = 1_000.milliseconds,
        )

        assertEquals(expected = 7, actual = result.exitCode)
        assertEquals(expected = "payload\n", actual = result.stdout)
        assertEquals(expected = "diagnostic\n", actual = result.stderr)
        assertFalse(result.isTimeout)
    }
}
