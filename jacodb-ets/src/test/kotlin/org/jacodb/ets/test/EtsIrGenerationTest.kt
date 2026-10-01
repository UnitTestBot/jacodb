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

import org.jacodb.ets.utils.EtsIrGenerationException
import org.jacodb.ets.utils.EtsIrGenerationTimeoutException
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.generateEtsIR
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class EtsIrGenerationTest {
    @Test
    fun `successful generation returns output to caller`() {
        val frontend = createTempDirectory("successful-ets-frontend")
        frontend.resolve("dist").createDirectories()
        frontend.resolve("dist/index.js").writeText(
            "require('fs').writeFileSync(process.argv.at(process.argv.at(-1) === '-v' ? -2 : -1), '{}');"
        )
        val source = frontend.resolve("input.ts").also { it.writeText("const value = 1;") }

        System.setProperty("ets.frontend.dir", frontend.toString())
        try {
            val output = generateEtsIR(
                projectPath = source,
                provider = EtsIrProvider.TS_FRONTEND,
                keepPartialOutputOnFailure = false,
            )

            assertEquals("{}", output.readText())
            Files.delete(output)
        } finally {
            System.clearProperty("ets.frontend.dir")
        }
    }

    @Test
    fun `generation fails instead of returning an invalid output path`() {
        val frontend = createTempDirectory("failing-ets-frontend")
        frontend.resolve("dist").createDirectories()
        frontend.resolve("dist/index.js").writeText(
            "require('fs').writeFileSync(process.argv.at(process.argv.at(-1) === '-v' ? -2 : -1), 'partial'); console.error('frontend failed'); process.exit(7);"
        )
        val source = frontend.resolve("input.ts").also { it.writeText("const value = 1;") }

        System.setProperty("ets.frontend.dir", frontend.toString())
        try {
            val error = assertFailsWith<EtsIrGenerationException> {
                generateEtsIR(projectPath = source, provider = EtsIrProvider.TS_FRONTEND)
            }
            assertFalse(error is EtsIrGenerationTimeoutException)
            assertTrue(error.message.orEmpty().contains("exit code 7"))
            assertTrue(error.message.orEmpty().contains("frontend failed"))
            val output = outputPath(error)
            assertEquals("partial", output.readText())
            Files.delete(output)
        } finally {
            System.clearProperty("ets.frontend.dir")
        }
    }

    @Test
    fun `process deadline has a typed generation failure`() {
        val frontend = createTempDirectory("slow-ets-frontend")
        frontend.resolve("dist").createDirectories()
        frontend.resolve("dist/index.js").writeText(
            "require('fs').writeFileSync(process.argv.at(process.argv.at(-1) === '-v' ? -2 : -1), 'partial'); setTimeout(() => {}, 5000);"
        )
        val source = frontend.resolve("input.ts").also { it.writeText("const value = 1;") }

        System.setProperty("ets.frontend.dir", frontend.toString())
        try {
            val error = assertFailsWith<EtsIrGenerationTimeoutException> {
                generateEtsIR(
                    projectPath = source,
                    provider = EtsIrProvider.TS_FRONTEND,
                    timeout = 200.milliseconds,
                    keepPartialOutputOnFailure = false,
                )
            }

            assertTrue(error.message.orEmpty().contains("timed out"))
            assertFalse(Files.exists(outputPath(error)))
        } finally {
            System.clearProperty("ets.frontend.dir")
        }
    }

    @Test
    fun `failed generation removes partial output when requested`() {
        val frontend = createTempDirectory("cleaned-ets-frontend")
        frontend.resolve("dist").createDirectories()
        frontend.resolve("dist/index.js").writeText(
            "require('fs').writeFileSync(process.argv.at(process.argv.at(-1) === '-v' ? -2 : -1), 'partial'); process.exit(7);"
        )
        val source = frontend.resolve("input.ts").also { it.writeText("const value = 1;") }

        System.setProperty("ets.frontend.dir", frontend.toString())
        try {
            val error = assertFailsWith<EtsIrGenerationException> {
                generateEtsIR(
                    projectPath = source,
                    provider = EtsIrProvider.TS_FRONTEND,
                    keepPartialOutputOnFailure = false,
                )
            }

            assertTrue(error.message.orEmpty().contains("exit code 7"))
            assertFalse(Files.exists(outputPath(error)))
        } finally {
            System.clearProperty("ets.frontend.dir")
        }
    }

    @Test
    fun `interrupted generation removes partial output when requested`() {
        val frontend = createTempDirectory("interrupted-ets-frontend")
        frontend.resolve("dist").createDirectories()
        frontend.resolve("dist/index.js").writeText(
            "require('fs').writeFileSync(__dirname + '/../output-path', process.argv.at(process.argv.at(-1) === '-v' ? -2 : -1));" +
                "require('fs').writeFileSync(process.argv.at(process.argv.at(-1) === '-v' ? -2 : -1), 'partial'); setTimeout(() => {}, 5000);"
        )
        val source = frontend.resolve("input.ts").also { it.writeText("const value = 1;") }
        val outputMarker = frontend.resolve("output-path")
        val failure = AtomicReference<Throwable?>()

        System.setProperty("ets.frontend.dir", frontend.toString())
        try {
            val worker = Thread {
                try {
                    generateEtsIR(
                        projectPath = source,
                        provider = EtsIrProvider.TS_FRONTEND,
                        keepPartialOutputOnFailure = false,
                    )
                } catch (error: Throwable) {
                    failure.set(error)
                }
            }
            worker.start()
            try {
                val deadline = System.nanoTime() + 5_000_000_000L
                while (!Files.exists(outputMarker) && System.nanoTime() < deadline) {
                    Thread.sleep(10)
                }
                assertTrue(Files.exists(outputMarker))
            } finally {
                worker.interrupt()
                worker.join(5_000)
            }

            assertFalse(worker.isAlive)
            assertTrue(failure.get() is InterruptedException)
            assertFalse(Files.exists(Paths.get(outputMarker.readText())))
        } finally {
            System.clearProperty("ets.frontend.dir")
        }
    }

    private fun outputPath(error: Throwable): Path = Paths.get(
        error.message.orEmpty().substringAfter("Output: '").substringBefore("'"),
    )
}
