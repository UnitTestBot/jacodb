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
import kotlin.test.assertFalse

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
}
