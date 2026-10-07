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
import java.nio.file.Files
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class ProcessTimeoutTest {
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
            val pid = Files.readString(pidFile).trim().toLong()

            assertTrue(completed.isTimeout)
            assertFalse(ProcessHandle.of(pid).map { process -> process.isAlive }.orElse(false))
        } finally {
            Files.readString(pidFile).trim().toLongOrNull()?.let { pid ->
                ProcessHandle.of(pid).ifPresent { process -> process.destroyForcibly() }
            }
            result.cancel(true)
            executor.shutdownNow()
            Files.deleteIfExists(pidFile)
        }
    }

    @Test
    fun `timeout also terminates a child holding inherited stdout and stderr`() {
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

            assertEquals(expected = 2, actual = pids.size)
            assertTrue(completed.isTimeout)
            assertTrue(pids.all { pid -> !ProcessHandle.of(pid).map { process -> process.isAlive }.orElse(false) })
        } finally {
            Files.readAllLines(pidFile).mapNotNull(String::toLongOrNull).forEach { pid ->
                ProcessHandle.of(pid).ifPresent { process -> process.destroyForcibly() }
            }
            result.cancel(true)
            executor.shutdownNow()
            Files.deleteIfExists(pidFile)
        }
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
