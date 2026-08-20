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
import org.junit.jupiter.api.Test
import java.net.ConnectException
import java.net.Socket
import java.nio.file.FileSystems
import java.nio.file.StandardWatchEventKinds
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.io.path.createDirectory
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ProcessUtilTest {
    private val node = System.getenv("NODE_EXECUTABLE") ?: "node"

    @Test
    fun `captures a non-zero exit with stderr`() {
        val result = ProcessUtil.run(
            listOf(node, "-e", "process.stderr.write('boom'); process.exit(7)"),
        )

        assertEquals(7, result.exitCode)
        assertEquals("", result.stdout)
        assertEquals("boom", result.stderr)
        assertFalse(result.isTimeout)
    }

    @Test
    fun `passes string input to stdin`() {
        val input = "finite string input\n"

        val result = ProcessUtil.run(
            listOf(node, "-e", "process.stdin.pipe(process.stdout)"),
            input = input,
        )

        assertEquals(0, result.exitCode)
        assertEquals(input, result.stdout)
        assertEquals("", result.stderr)
        assertFalse(result.isTimeout)
    }

    @Test
    fun `captures stdout and stderr from the same process`() {
        val result = ProcessUtil.run(
            listOf(
                node,
                "-e",
                "process.stdout.write('stdout'); process.stderr.write('stderr')",
            ),
        )

        assertEquals(0, result.exitCode)
        assertEquals("stdout", result.stdout)
        assertEquals("stderr", result.stderr)
        assertFalse(result.isTimeout)
    }

    @Test
    fun `retains output larger than one MiB in full`() {
        val outputSize = (1 shl 20) + 17
        val expected = "x".repeat(outputSize)

        val result = ProcessUtil.run(
            listOf(node, "-e", "process.stdout.write('x'.repeat($outputSize))"),
        )

        assertEquals(0, result.exitCode)
        assertEquals(expected, result.stdout)
        assertEquals("", result.stderr)
        assertFalse(result.isTimeout)
    }

    @Test
    fun `interruption terminates and reaps the child before it is propagated`() {
        val testDirectory = createTempDirectory("process-util-interruption")
        val stagingDirectory = testDirectory.resolve("staging").createDirectory()
        val stagedReadyFile = stagingDirectory.resolve("ready.tmp")
        val readyFile = testDirectory.resolve("ready")
        val failure = AtomicReference<Throwable>()
        val interruptPreserved = AtomicBoolean()
        val watcher = FileSystems.getDefault().newWatchService()
        testDirectory.register(watcher, StandardWatchEventKinds.ENTRY_CREATE)
        val runner = thread(start = false) {
            try {
                ProcessUtil.run(
                    listOf(
                        node,
                        "-e",
                        "const fs = require('fs'); const net = require('net'); " +
                            "const server = net.createServer((socket) => { socket.end(); server.close(); }); " +
                            "process.on('SIGTERM', () => {}); " +
                            "server.listen(0, '127.0.0.1', () => " +
                            "{ fs.writeFileSync(process.argv[1], String(server.address().port)); " +
                            "fs.renameSync(process.argv[1], process.argv[2]); });",
                        stagedReadyFile.toString(),
                        readyFile.toString(),
                    ),
                )
            } catch (error: Throwable) {
                failure.set(error)
                interruptPreserved.set(Thread.currentThread().isInterrupted)
            }
        }

        try {
            runner.start()
            watcher.take()
            assertTrue(readyFile.toFile().isFile, "child did not publish its listening port")

            runner.interrupt()
            runner.join()

            val port = readyFile.readText().toInt()
            val connection = runCatching {
                Socket("127.0.0.1", port).use { }
            }
            assertIs<InterruptedException>(failure.get())
            assertIs<ConnectException>(
                connection.exceptionOrNull(),
                "child still accepted connections after interruption cleanup",
            )
            assertTrue(interruptPreserved.get(), "caller interrupt status was not restored")
        } finally {
            watcher.close()
            testDirectory.toFile().deleteRecursively()
        }
    }
}
