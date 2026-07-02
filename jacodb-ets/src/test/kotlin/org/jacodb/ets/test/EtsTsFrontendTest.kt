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

import org.jacodb.ets.dto.EtsFileDto
import org.jacodb.ets.dto.toEtsFile
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.DEFAULT_ARK_CLASS_NAME
import org.jacodb.ets.utils.DEFAULT_ARK_METHOD_NAME
import org.jacodb.ets.utils.ProcessUtil
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolute
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.pathString
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Tests for the native TypeScript frontend (`jacodb-ets/ts-frontend`).
 *
 * These tests spawn `node ts-frontend/dist/index.js` on TS/JS sources and verify
 * that the produced JSON deserializes into [EtsFileDto] and converts to a valid model.
 *
 * Tests are skipped if the frontend is not built (`npm run build` in `ts-frontend`)
 * or `node` is not available.
 */
class EtsTsFrontendTest {

    companion object {
        private val tsFrontendDir: Path = run {
            val fromProperty = System.getProperty("ets.frontend.dir")
            val fromEnv = System.getenv("ETS_FRONTEND_DIR")
            Path(fromProperty ?: fromEnv ?: "ts-frontend").absolute()
        }

        private val script: Path = tsFrontendDir.resolve("dist/index.js")

        private val node: String = System.getenv("NODE_EXECUTABLE") ?: "node"

        private fun runFrontend(source: String, fileName: String = "test.ts"): EtsFileDto {
            assumeTrue(script.exists(), "ts-frontend is not built: $script does not exist")

            val dir = createTempDirectory("ts-frontend-test")
            val inputPath = dir.resolve(fileName)
            inputPath.writeText(source)
            val outputPath = dir.resolve("$fileName.json")

            val result = ProcessUtil.run(
                listOf(node, script.pathString, inputPath.pathString, outputPath.pathString),
                timeout = 60.seconds,
            )
            assertEquals(
                0, result.exitCode,
                "ts-frontend failed with exit code ${result.exitCode}\nSTDOUT:\n${result.stdout}\nSTDERR:\n${result.stderr}",
            )
            assertTrue(outputPath.exists(), "ts-frontend did not produce output: $outputPath")

            return EtsFileDto.loadFromJson(outputPath.readText())
        }
    }

    @Test
    fun `straight-line program lowers, converts and linearizes`() {
        val etsFileDto = runFrontend(
            """
                function add(a: number, b: number): number {
                    return a + b;
                }
                class C {}
                let x = add(1, 2);
                let arr = [1, 2, 3];
                arr[0] = x + 1;
                let s = "value: " + x;
                console.log(s);
            """.trimIndent()
        )

        val etsFile = etsFileDto.toEtsFile()
        val scene = EtsScene(listOf(etsFile))
        val defaultClass = scene.projectClasses.single { it.name == DEFAULT_ARK_CLASS_NAME }

        val addMethod = defaultClass.methods.single { it.name == "add" }
        assertEquals(2, addMethod.parameters.size)
        assertTrue(addMethod.cfg.stmts.isNotEmpty(), "'add' must have a non-empty body")

        val defaultMethod = defaultClass.methods.single { it.name == DEFAULT_ARK_METHOD_NAME }
        assertTrue(defaultMethod.cfg.stmts.size >= 8, "top-level code must be lowered into the default method")
        assertTrue(defaultMethod.locals.any { it.name == "x" }, "local 'x' must be declared")
        assertTrue(defaultMethod.locals.any { it.name == "arr" }, "local 'arr' must be declared")
    }

    @Test
    fun `control flow program converts and linearizes`() {
        val etsFileDto = runFrontend(
            """
                function classify(n: number): string {
                    if (n < 0) {
                        return "negative";
                    }
                    let result = "";
                    for (let i = 0; i < n; i++) {
                        if (i % 2 === 0) {
                            continue;
                        }
                        result += i;
                    }
                    switch (n) {
                        case 0: return "zero";
                        case 1: return "one";
                        default: break;
                    }
                    let arr = [1, 2, 3];
                    for (const v of arr) {
                        result = n > 5 ? result + v : result;
                    }
                    while (n > 0) {
                        n--;
                    }
                    return result;
                }
            """.trimIndent()
        )

        val etsFile = etsFileDto.toEtsFile()
        val scene = EtsScene(listOf(etsFile))
        val defaultClass = scene.projectClasses.single { it.name == DEFAULT_ARK_CLASS_NAME }
        val method = defaultClass.methods.single { it.name == "classify" }

        // Linearization walks the whole block CFG — this validates successor structure.
        val stmts = method.cfg.stmts
        assertTrue(stmts.size > 20, "expected a rich linearized body, got ${stmts.size} stmts")
        assertTrue(method.cfg.blocks.size > 10, "expected multiple basic blocks, got ${method.cfg.blocks.size}")
    }

    @Test
    fun `smoke - produced JSON deserializes and converts to a valid EtsFile`() {
        val etsFileDto = runFrontend("")

        val defaultClass = etsFileDto.classes.single { it.signature.name == DEFAULT_ARK_CLASS_NAME }
        val defaultMethod = defaultClass.methods.single { it.signature.name == DEFAULT_ARK_METHOD_NAME }
        checkNotNull(defaultMethod.body)

        val etsFile = etsFileDto.toEtsFile()
        val scene = EtsScene(listOf(etsFile))
        val clazz = scene.projectClasses.single { it.name == DEFAULT_ARK_CLASS_NAME }
        val method = clazz.methods.single { it.name == DEFAULT_ARK_METHOD_NAME }
        assertTrue(method.cfg.stmts.isNotEmpty(), "default method must have a non-empty body")
    }
}
