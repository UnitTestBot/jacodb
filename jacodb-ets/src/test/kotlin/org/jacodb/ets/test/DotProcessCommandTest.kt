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
import org.jacodb.ets.utils.checkRenderResult
import org.jacodb.ets.utils.dotRenderCommand
import org.jacodb.ets.utils.viewerLaunchCommand
import org.junit.jupiter.api.Test
import kotlin.io.path.Path
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DotProcessCommandTest {
    @Test
    fun `render command preserves paths with spaces as separate arguments`() {
        val dotFile = Path("graph files/input.dot")
        val outputFile = Path("graph files/output.svg")

        val command = dotRenderCommand(
            dotCommand = listOf("dot", "-v"),
            format = "svg",
            dotFile = dotFile,
            outputFile = outputFile,
        )

        assertEquals(
            listOf("dot", "-v", "-Tsvg", dotFile.toString(), "-o", outputFile.toString()),
            command,
        )
    }

    @Test
    fun `viewer command preserves the file path as one argument`() {
        val outputFile = Path("graph files/output.svg")

        val command = viewerLaunchCommand(
            viewerCommand = listOf("viewer", "--option"),
            outputFile = outputFile,
        )

        assertEquals(listOf("viewer", "--option", outputFile.toString()), command)
    }

    @Test
    fun `Windows start command reserves an empty title and quotes the path`() {
        val outputFile = Path("graph files/output.svg")

        val command = viewerLaunchCommand(
            viewerCommand = listOf("cmd", "/c", "start", ""),
            outputFile = outputFile,
        )

        assertEquals(
            listOf("cmd", "/c", "start", "", "\"${outputFile.toAbsolutePath()}\""),
            command,
        )
    }

    @Test
    fun `nonzero render result reports exit code and stderr`() {
        val outputFile = Path("graph files/output.svg")
        val result = ProcessUtil.Result(
            exitCode = 7,
            stdout = "",
            stderr = "invalid graph",
            isTimeout = false,
        )

        val error = assertFailsWith<IllegalStateException> {
            checkRenderResult(result, outputFile)
        }

        assertContains(error.message.orEmpty(), "exit code 7")
        assertContains(error.message.orEmpty(), "invalid graph")
    }

    @Test
    fun `timed out render result is not accepted`() {
        val outputFile = Path("graph files/output.svg")
        val result = ProcessUtil.Result(
            exitCode = 137,
            stdout = "",
            stderr = "stopped",
            isTimeout = true,
        )

        val error = assertFailsWith<IllegalStateException> {
            checkRenderResult(result, outputFile)
        }

        assertContains(error.message.orEmpty(), "timed out")
        assertContains(error.message.orEmpty(), "stopped")
    }
}
