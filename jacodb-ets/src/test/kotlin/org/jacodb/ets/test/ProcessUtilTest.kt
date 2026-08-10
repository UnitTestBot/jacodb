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
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.readText
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
