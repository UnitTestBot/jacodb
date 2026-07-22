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

import org.jacodb.ets.dto.ArrayTypeDto
import org.jacodb.ets.dto.AssignStmtDto
import org.jacodb.ets.dto.BooleanTypeDto
import org.jacodb.ets.dto.EtsFileDto
import org.jacodb.ets.dto.NewArrayExprDto
import org.jacodb.ets.dto.NumberTypeDto
import org.jacodb.ets.dto.toEtsFile
import org.jacodb.ets.model.EtsAssignStmt
import org.jacodb.ets.model.EtsCaughtExceptionRef
import org.jacodb.ets.model.EtsClosureFieldRef
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.DEFAULT_ARK_CLASS_NAME
import org.jacodb.ets.utils.DEFAULT_ARK_METHOD_NAME
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.defaultProviderFor
import org.jacodb.ets.utils.etsIrSerializerScript
import org.jacodb.ets.utils.generateEtsIR
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Tests for the native TypeScript frontend (`jacodb-ets/ts-frontend`).
 *
 * These tests spawn the standalone frontend bundled in the module resources on
 * TS/JS sources and verify that the produced JSON deserializes into [EtsFileDto]
 * and converts to a valid model. Node.js must be available on PATH.
 */
class EtsTsFrontendTest {

    companion object {
        private fun tsFrontendAvailable(): Boolean =
            try {
                etsIrSerializerScript(EtsIrProvider.TS_FRONTEND).exists()
            } catch (_: Exception) {
                false
            }

        /** Run the PRODUCTION integration path: generateEtsIR + EtsFileDto.loadFromJson. */
        private fun runFrontend(source: String, fileName: String = "test.ts"): EtsFileDto {
            assumeTrue(tsFrontendAvailable(), "bundled ts-frontend resource is unavailable")

            val dir = createTempDirectory("ts-frontend-test")
            val inputPath = dir.resolve(fileName)
            inputPath.writeText(source)

            val outputPath = generateEtsIR(
                inputPath,
                isProject = false,
                timeout = 60.seconds,
                provider = EtsIrProvider.TS_FRONTEND,
            )
            assertTrue(outputPath.exists(), "ts-frontend did not produce output: $outputPath")

            return EtsFileDto.loadFromJson(outputPath.readText())
        }
    }

    @Test
    fun `bundled frontend keeps matching TypeScript standard libraries`() {
        val script = etsIrSerializerScript(EtsIrProvider.TS_FRONTEND)
        assertTrue(
            script.parent.resolve("lib.es2020.d.ts").exists(),
            "the production frontend runtime must include TypeScript standard libraries next to index.js",
        )

        val dto = runFrontend(
            """
                const flags = new Array<boolean>(3);
                const values = Array.from([1, 2]);
            """.trimIndent(),
        )
        val defaultClass = dto.classes.single { it.signature.name == DEFAULT_ARK_CLASS_NAME }
        assertEquals(ArrayTypeDto(BooleanTypeDto, 1), defaultClass.fields.single { it.signature.name == "flags" }.signature.type)
        assertEquals(ArrayTypeDto(NumberTypeDto, 1), defaultClass.fields.single { it.signature.name == "values" }.signature.type)

        val allocation = defaultClass.methods
            .single { it.signature.name == DEFAULT_ARK_METHOD_NAME }
            .body!!
            .cfg
            .blocks
            .flatMap { it.stmts }
            .filterIsInstance<AssignStmtDto>()
            .map { it.right }
            .filterIsInstance<NewArrayExprDto>()
            .single { it.elementType == BooleanTypeDto }
        assertEquals(BooleanTypeDto, allocation.elementType)
    }

    @Test
    fun `ets files stay on the legacy provider by default`() {
        val dir = createTempDirectory("ets-provider-test")
        assertEquals(EtsIrProvider.ARKANALYZER, defaultProviderFor(dir.resolve("sample.ets"), isProject = false))
        assertEquals(EtsIrProvider.ARKANALYZER, defaultProviderFor(dir.resolve("sample.ETS"), isProject = false))
        dir.resolve("nested").createDirectories()
        dir.resolve("nested/sample.ets").writeText("")
        assertEquals(EtsIrProvider.ARKANALYZER, defaultProviderFor(dir, isProject = true))
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
        assertTrue(defaultClass.fields.any { it.name == "x" }, "module field 'x' must be declared")
        assertTrue(defaultClass.fields.any { it.name == "arr" }, "module field 'arr' must be declared")
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
    fun `classes, enums and namespaces convert to the model`() {
        val etsFileDto = runFrontend(
            """
                export interface Shape {
                    area(): number;
                }

                export class Circle implements Shape {
                    static count: number = 0;
                    radius: number = 1;

                    constructor(radius: number) {
                        this.radius = radius;
                        Circle.count++;
                    }

                    area(): number {
                        return 3.14 * this.radius * this.radius;
                    }
                }

                enum Color { Red, Green = 5, Blue }

                namespace Geometry {
                    export class Point {
                        x: number = 0;
                    }
                }

                let c = new Circle(2);
                console.log(c.area(), Color.Green);
            """.trimIndent()
        )

        val etsFile = etsFileDto.toEtsFile()
        val scene = EtsScene(listOf(etsFile))

        val circle = scene.projectClasses.single { it.name == "Circle" }
        assertTrue(circle.fields.any { it.name == "radius" })
        assertTrue(circle.fields.any { it.name == "count" })

        // ArkAnalyzer conventions: ctor + %instInit + %statInit present and linearizable.
        val ctor = circle.methods.single { it.name == "constructor" }
        assertTrue(ctor.cfg.stmts.isNotEmpty())
        val instInit = circle.methods.single { it.name == "%instInit" }
        assertTrue(instInit.cfg.stmts.isNotEmpty())
        val statInit = circle.methods.single { it.name == "%statInit" }
        assertTrue(statInit.cfg.stmts.isNotEmpty())

        val shape = scene.projectClasses.single { it.name == "Shape" }
        assertTrue(shape.methods.single { it.name == "area" }.cfg.stmts.isEmpty(), "interface methods have no body")

        val color = scene.projectClasses.single { it.name == "Color" }
        assertTrue(color.fields.any { it.name == "Green" })

        val point = etsFile.namespaces.single().classes.single { it.name == "Point" }
        assertTrue(point.methods.any { it.name == "%instInit" })
    }

    @Test
    fun `try-catch and imports-exports convert to the model`() {
        val etsFileDto = runFrontend(
            """
                import { helper as h } from "./helper";
                import * as fs from "fs";

                export function risky(x: number): number {
                    try {
                        if (x < 0) {
                            throw new Error("negative");
                        }
                        return x;
                    } catch (e) {
                        console.log(e);
                        return -1;
                    } finally {
                        console.log("done");
                    }
                }

                export { risky as saferRisky };
            """.trimIndent()
        )

        assertEquals(2, etsFileDto.importInfos.size)
        assertEquals("h", etsFileDto.importInfos[0].importName)
        assertEquals("helper", etsFileDto.importInfos[0].nameBeforeAs)
        assertEquals("NamespaceImport", etsFileDto.importInfos[1].importType)
        assertTrue(etsFileDto.exportInfos.any { it.exportName == "risky" })
        assertTrue(etsFileDto.exportInfos.any { it.exportName == "saferRisky" && it.nameBeforeAs == "risky" })

        val etsFile = etsFileDto.toEtsFile()
        val scene = EtsScene(listOf(etsFile))
        val method = scene.projectClasses
            .single { it.name == DEFAULT_ARK_CLASS_NAME }
            .methods.single { it.name == "risky" }

        // The catch handler must survive lowering (ArkAnalyzer used to drop it).
        val stmts = method.cfg.stmts
        assertTrue(
            stmts.filterIsInstance<EtsAssignStmt>().any { it.rhv is EtsCaughtExceptionRef },
            "expected a caught-exception binding in:\n${stmts.joinToString("\n")}"
        )
    }

    @Test
    fun `closures, destructuring and object literals convert to the model`() {
        val etsFileDto = runFrontend(
            """
                const config = { host: "localhost", port: 8080, describe(): string { return this.host; } };
                const { host, port: p = 80 } = config;

                const handlers = [1, 2, 3].map((x: number) => x * 2);

                function makeMultiplier(factor: number): (value: number) => number {
                    let total = factor;
                    return (value: number) => {
                        total += value;
                        return total;
                    };
                }

                function safeFirst(arr?: number[]): number | undefined {
                    return arr?.[0];
                }

                function* naturals(): Generator<number> {
                    let i = 0;
                    while (true) {
                        yield i++;
                    }
                }
            """.trimIndent()
        )

        val etsFile = etsFileDto.toEtsFile()
        val scene = EtsScene(listOf(etsFile))
        val defaultClass = scene.projectClasses.single { it.name == DEFAULT_ARK_CLASS_NAME }

        // Closure lifted into an anonymous method.
        val anonymousMethod = defaultClass.methods.single { it.name.startsWith("%AM0") }
        assertTrue(anonymousMethod.cfg.stmts.isNotEmpty(), "closure body must be lowered")
        val capturingMethod = defaultClass.methods.single {
            it.name.startsWith("%AM") && it.name.contains("makeMultiplier")
        }
        assertTrue(
            capturingMethod.cfg.stmts
                .filterIsInstance<EtsAssignStmt>()
                .any { it.rhv is EtsClosureFieldRef },
            "captured locals must be loaded from a lexical environment",
        )
        assertTrue(
            capturingMethod.cfg.stmts
                .filterIsInstance<EtsAssignStmt>()
                .any { it.lhv is EtsClosureFieldRef },
            "captured locals must be writable through the lexical environment",
        )

        // Object literal became an anonymous class with fields and a method.
        val anonymousClass = scene.projectClasses.single { it.name.startsWith("%AC0") }
        assertTrue(anonymousClass.fields.any { it.name == "host" })
        assertTrue(anonymousClass.methods.any { it.name == "describe" })

        // Destructured module bindings use the default class's shared storage.
        assertTrue(defaultClass.fields.any { it.name == "host" })
        assertTrue(defaultClass.fields.any { it.name == "p" })

        // Optional chaining and generators linearize fine.
        assertTrue(defaultClass.methods.single { it.name == "safeFirst" }.cfg.stmts.isNotEmpty())
        assertTrue(defaultClass.methods.single { it.name == "naturals" }.cfg.stmts.isNotEmpty())
    }

    @Test
    fun `arkanalyzer provider stays selectable`() {
        val arkAnalyzerAvailable = try {
            etsIrSerializerScript(EtsIrProvider.ARKANALYZER).exists()
        } catch (_: Exception) {
            false
        }
        assumeTrue(arkAnalyzerAvailable, "ArkAnalyzer is not available (set ARKANALYZER_DIR to enable)")

        val dir = createTempDirectory("arkanalyzer-provider-test")
        val inputPath = dir.resolve("simple.ts")
        inputPath.writeText(
            """
                function twice(x: number): number {
                    return x * 2;
                }
                let y = twice(21);
            """.trimIndent()
        )

        val etsFile = loadEtsFileAutoConvert(inputPath, provider = EtsIrProvider.ARKANALYZER)
        val scene = EtsScene(listOf(etsFile))
        val defaultClass = scene.projectClasses.single { it.name == DEFAULT_ARK_CLASS_NAME }
        assertTrue(defaultClass.methods.any { it.name == "twice" })
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
