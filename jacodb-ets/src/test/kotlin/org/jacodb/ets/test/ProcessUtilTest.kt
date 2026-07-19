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
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTime

class ProcessUtilTest {
    private val node = System.getenv("NODE_EXECUTABLE") ?: "node"

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
}
