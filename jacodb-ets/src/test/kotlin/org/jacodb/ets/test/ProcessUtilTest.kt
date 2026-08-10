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
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.createTempDirectory
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
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
    fun `forcibly terminates a process that ignores the timeout signal`() {
        lateinit var result: ProcessUtil.Result
        val elapsed = measureTime {
            result = ProcessUtil.run(
                listOf(
                    node,
                    "-e",
                    "process.on('SIGTERM', () => {}); setInterval(() => {}, 1000)",
                ),
                timeout = 100.milliseconds,
            )
        }

        assertTrue(result.isTimeout)
        assertTrue(elapsed.inWholeSeconds < 5, "timed-out process took $elapsed to terminate")
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
    fun `timeout closes a descendant inherited output sink after reaping the direct process`() {
        val testDirectory = createTempDirectory("process-util-descendant-output")
        val triggerFile = testDirectory.resolve("trigger")
        val statusFile = testDirectory.resolve("status")
        val descendantScript =
            "const fs = require('fs'); " +
                "const timer = setInterval(() => { " +
                "if (!fs.existsSync(process.argv[1])) return; " +
                "clearInterval(timer); " +
                "let status = 'closed'; " +
                "try { const chunk = Buffer.alloc(65536, 120); " +
                "for (let i = 0; i < 64; i++) fs.writeSync(1, chunk); " +
                "status = 'writable'; } catch (_) {} " +
                "fs.writeFileSync(process.argv[2], status); " +
                "}, 10)"
        val directScript =
            "const { spawn } = require('child_process'); " +
                "spawn(process.execPath, ['-e', process.argv[1], process.argv[2], process.argv[3]], " +
                "{ stdio: ['inherit', 'inherit', 'inherit'] }); " +
                "process.on('SIGTERM', () => {}); " +
                "setInterval(() => {}, 1000)"

        try {
            val result = ProcessUtil.run(
                listOf(node, "-e", directScript, descendantScript, triggerFile.toString(), statusFile.toString()),
                timeout = 100.milliseconds,
            )
            assertTrue(result.isTimeout)

            triggerFile.writeText("")
            waitForFile(statusFile)
            assertEquals(
                "closed",
                statusFile.readText(),
                "descendant could keep writing after the direct process was reaped",
            )
        } finally {
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
