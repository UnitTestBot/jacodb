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

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jacodb.ets.dto.ArrayRefDto
import org.jacodb.ets.dto.ArrayTypeDto
import org.jacodb.ets.dto.AssignStmtDto
import org.jacodb.ets.dto.BooleanTypeDto
import org.jacodb.ets.dto.CaughtExceptionRefDto
import org.jacodb.ets.dto.ClassTypeDto
import org.jacodb.ets.dto.ClassValueRefDto
import org.jacodb.ets.dto.ClassValueTypeDto
import org.jacodb.ets.dto.ConstantDto
import org.jacodb.ets.dto.EtsFileDto
import org.jacodb.ets.dto.IfStmtDto
import org.jacodb.ets.dto.LocalDto
import org.jacodb.ets.dto.NewArrayExprDto
import org.jacodb.ets.dto.NewExprDto
import org.jacodb.ets.dto.NumberTypeDto
import org.jacodb.ets.dto.Ops
import org.jacodb.ets.dto.RawStmtDto
import org.jacodb.ets.dto.RelationOperationDto
import org.jacodb.ets.dto.ReturnStmtDto
import org.jacodb.ets.dto.StringTypeDto
import org.jacodb.ets.dto.StaticCallExprDto
import org.jacodb.ets.dto.StaticFieldRefDto
import org.jacodb.ets.dto.ThrowStmtDto
import org.jacodb.ets.dto.UnaryOperationDto
import org.jacodb.ets.dto.UnknownTypeDto
import org.jacodb.ets.dto.dtoModule
import org.jacodb.ets.dto.toEtsFile
import org.jacodb.ets.model.EtsArrayAccess
import org.jacodb.ets.model.EtsAssignStmt
import org.jacodb.ets.model.EtsCaughtExceptionRef
import org.jacodb.ets.model.EtsCallStmt
import org.jacodb.ets.model.EtsClassValueRef
import org.jacodb.ets.model.EtsClassValueType
import org.jacodb.ets.model.EtsClosureFieldRef
import org.jacodb.ets.model.EtsEqExpr
import org.jacodb.ets.model.EtsIfStmt
import org.jacodb.ets.model.EtsInstanceFieldRef
import org.jacodb.ets.model.EtsInstanceOfExpr
import org.jacodb.ets.model.EtsRawStmt
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsNewArrayExpr
import org.jacodb.ets.model.EtsNumberConstant
import org.jacodb.ets.model.EtsThrowStmt
import org.jacodb.ets.utils.DEFAULT_ARK_CLASS_NAME
import org.jacodb.ets.utils.DEFAULT_ARK_METHOD_NAME
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.defaultProviderFor
import org.jacodb.ets.utils.generateEtsIR
import org.junit.jupiter.api.Test
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for the native TypeScript frontend (`jacodb-ets/ts-frontend`).
 *
 * These tests spawn the standalone frontend bundled in the module resources on
 * TS/JS sources and verify that the produced JSON deserializes into [EtsFileDto]
 * and converts to a valid model. Node.js must be available on PATH.
 */
class EtsTsFrontendTest {

    companion object {
        /** Run the PRODUCTION integration path: generateEtsIR + EtsFileDto.loadFromJson. */
        private fun runFrontend(source: String, fileName: String = "test.ts"): EtsFileDto {
            val dir = createTempDirectory("ts-frontend-test")
            val inputPath = dir.resolve(fileName)
            inputPath.writeText(source)

            val outputPath = generateEtsIR(
                inputPath,
                isProject = false,
                timeout = null,
                provider = EtsIrProvider.TS_FRONTEND,
            )
            assertTrue(outputPath.exists(), "ts-frontend did not produce output: $outputPath")

            return EtsFileDto.loadFromJson(outputPath.readText())
        }
    }

    @Test
    fun `default export expression executes during module initialization and survives JSON conversion`() {
        val dto = runFrontend(
            """
                export let count = 0;
                function sideEffect(): number { count++; return count; }
                export default sideEffect();
                export const after = count;
            """.trimIndent()
        )

        val defaultClass = dto.classes.single { it.signature.name == DEFAULT_ARK_CLASS_NAME }
        assertEquals(NumberTypeDto, defaultClass.fields.single { it.signature.name == "default" }.signature.type)

        val stmts = defaultClass.methods.single { it.signature.name == DEFAULT_ARK_METHOD_NAME }
            .body!!.cfg.blocks.flatMap { it.stmts }
        val callIndex = stmts.indexOfFirst {
            it is AssignStmtDto && (it.right as? StaticCallExprDto)?.method?.name == "sideEffect"
        }
        val exportIndex = stmts.indexOfFirst {
            it is AssignStmtDto && (it.left as? StaticFieldRefDto)?.field?.name == "default"
        }
        val afterIndex = stmts.indexOfFirst {
            it is AssignStmtDto && (it.left as? StaticFieldRefDto)?.field?.name == "after"
        }

        assertTrue(callIndex >= 0)
        assertTrue(exportIndex > callIndex)
        assertTrue(afterIndex > exportIndex)
        assertEquals("default", dto.exportInfos.single { it.exportName == "default" }.exportName)

        val model = dto.toEtsFile()
        val export = model.exportInfos.single { it.isDefaultExport }
        assertEquals("default", export.name)
        assertEquals("default", export.originalName)
        assertTrue(model.classes.single { it.name == DEFAULT_ARK_CLASS_NAME }.fields.any { it.name == "default" })
    }

    @Test
    fun `namespaced constructor in default export survives JSON conversion`() {
        val dto = runFrontend(
            """
                namespace N { export class Box { constructor(public value: number) {} } }
                export default new N.Box(7);
            """.trimIndent()
        )

        val defaultClass = dto.classes.single { it.signature.name == DEFAULT_ARK_CLASS_NAME }
        val stmts = defaultClass.methods.single { it.signature.name == DEFAULT_ARK_METHOD_NAME }
            .body!!.cfg.blocks.flatMap { it.stmts }
        val allocation = stmts.filterIsInstance<AssignStmtDto>()
            .map { it.right }.filterIsInstance<NewExprDto>().single()
        val classType = allocation.classType as ClassTypeDto

        assertEquals("Box", classType.signature.name)
        assertEquals("N", classType.signature.declaringNamespace?.name)
        assertTrue(defaultClass.fields.any { it.signature.name == "default" })
        assertEquals("default", dto.toEtsFile().exportInfos.single { it.isDefaultExport }.name)
    }

    @Test
    fun `default export of a static method value remains explicitly unsupported in JSON`() {
        val dto = runFrontend(
            """
                class Box { static getCount(): number { return 1; } }
                export default Box.getCount;
            """.trimIndent()
        )

        val defaultClass = dto.classes.single { it.signature.name == DEFAULT_ARK_CLASS_NAME }
        val stmts = defaultClass.methods.single { it.signature.name == DEFAULT_ARK_METHOD_NAME }
            .body!!.cfg.blocks.flatMap { it.stmts }

        assertTrue(defaultClass.fields.none { it.signature.name == "default" })
        assertTrue(stmts.any { it is RawStmtDto && it.kind == "UnsupportedStmt" })
    }

    @Test
    fun `module lexical this in default export remains explicitly unsupported in JSON`() {
        val dto = runFrontend("export default (() => this);")

        val defaultClass = dto.classes.single { it.signature.name == DEFAULT_ARK_CLASS_NAME }
        val stmts = defaultClass.methods.single { it.signature.name == DEFAULT_ARK_METHOD_NAME }
            .body!!.cfg.blocks.flatMap { it.stmts }

        assertTrue(defaultClass.fields.none { it.signature.name == "default" })
        assertTrue(stmts.any { it is RawStmtDto && it.kind == "UnsupportedStmt" })
    }

    @Test
    fun `computed object method name in default export remains unsupported in JSON`() {
        val dto = runFrontend("export default ({ [this]() { return 1; } });")

        val defaultClass = dto.classes.single { it.signature.name == DEFAULT_ARK_CLASS_NAME }
        val stmts = defaultClass.methods.single { it.signature.name == DEFAULT_ARK_METHOD_NAME }
            .body!!.cfg.blocks.flatMap { it.stmts }

        assertTrue(defaultClass.fields.none { it.signature.name == "default" })
        assertTrue(stmts.any { it is RawStmtDto && it.kind == "UnsupportedStmt" })
    }

    @Test
    fun `nested ordinary function this does not suppress default export in JSON`() {
        val dto = runFrontend(
            "export default () => { function inner() { return this; } return 1; };"
        )

        val defaultClass = dto.classes.single { it.signature.name == DEFAULT_ARK_CLASS_NAME }
        val stmts = defaultClass.methods.single { it.signature.name == DEFAULT_ARK_METHOD_NAME }
            .body!!.cfg.blocks.flatMap { it.stmts }

        assertTrue(defaultClass.fields.any { it.signature.name == "default" })
        assertTrue(stmts.none { it is RawStmtDto && it.kind == "UnsupportedStmt" })
    }

    @Test
    fun `default export identifier resolves to its snapshot binding`() {
        val dto = runFrontend(
            """
                let value = 1;
                export default value;
                value = 2;
            """.trimIndent()
        )

        val model = dto.toEtsFile()
        val export = model.exportInfos.single { it.isDefaultExport }
        assertEquals("default", export.originalName)

        val stmts = dto.classes.single { it.signature.name == DEFAULT_ARK_CLASS_NAME }
            .methods.single { it.signature.name == DEFAULT_ARK_METHOD_NAME }
            .body!!.cfg.blocks.flatMap { it.stmts }
        val exportWrite = stmts.indexOfFirst {
            it is AssignStmtDto && (it.left as? StaticFieldRefDto)?.field?.name == "default"
        }
        val laterWrite = stmts.indexOfLast {
            it is AssignStmtDto && (it.left as? StaticFieldRefDto)?.field?.name == "value"
        }

        assertTrue(exportWrite >= 0)
        assertTrue(laterWrite > exportWrite)
    }

    @Test
    fun `declared class constructor value survives frontend JSON and model conversion`() {
        val dto = runFrontend(
            """
                class A { static marker = 7; }
                export function constructorValue(): typeof A { return A; }
                export function copy(): typeof A { const saved = A; return saved; }
                export function create(): A { return new A(); }
                export function direct(value: object): boolean { return value instanceof A; }
                export function marker(): number { return A.marker; }
                export default A;
            """.trimIndent(),
        )
        val classSignature = dto.classes.single { it.signature.name == "A" }.signature
        val methods = dto.classes.single { it.signature.name == DEFAULT_ARK_CLASS_NAME }.methods
        val constructorValue = methods.single { it.signature.name == "constructorValue" }
        val copy = methods.single { it.signature.name == "copy" }
        val returnedClass = constructorValue.body!!.cfg.blocks.flatMap { it.stmts }
            .filterIsInstance<ReturnStmtDto>().single().arg as ClassValueRefDto

        assertEquals(classSignature, returnedClass.signature)
        assertEquals(ClassValueTypeDto(classSignature), returnedClass.type)
        assertEquals(ClassValueTypeDto(classSignature), constructorValue.signature.returnType)
        assertTrue(copy.body!!.cfg.blocks.flatMap { it.stmts }
            .filterIsInstance<AssignStmtDto>().any { it.right == returnedClass })
        assertTrue(dto.exportInfos.any { it.exportName == "A" })

        val model = dto.toEtsFile()
        val modelMethods = model.classes.single { it.name == DEFAULT_ARK_CLASS_NAME }.methods
        val modelConstructor = modelMethods.single { it.name == "constructorValue" }
        assertTrue(modelConstructor.signature.returnType is EtsClassValueType)
        assertTrue(modelConstructor.cfg.stmts.filterIsInstance<EtsAssignStmt>()
            .any { it.rhv is EtsClassValueRef })
        assertTrue(modelMethods.single { it.name == "create" }.cfg.stmts
            .filterIsInstance<EtsAssignStmt>().any { it.rhv is org.jacodb.ets.model.EtsNewExpr })
        assertTrue(modelMethods.single { it.name == "direct" }.cfg.stmts
            .filterIsInstance<EtsAssignStmt>().any { it.rhv is EtsInstanceOfExpr })
    }

    @Test
    fun `bundled frontend lowers Array from using its TypeScript standard libraries`() {
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
    fun `array literal holes survive frontend JSON and model conversion`() {
        val dto = runFrontend(
            source = "const values = [, undefined, 3, ,];",
        )
        val dtoStmts = dto.classes
            .single { it.signature.name == DEFAULT_ARK_CLASS_NAME }
            .methods.single { it.signature.name == DEFAULT_ARK_METHOD_NAME }
            .body!!.cfg.blocks.flatMap { it.stmts }
            .filterIsInstance<AssignStmtDto>()
        val allocation = dtoStmts.map { it.right }.filterIsInstance<NewArrayExprDto>().single()
        val stores = dtoStmts.filter { it.left is ArrayRefDto }

        assertEquals("4", (allocation.size as ConstantDto).value)
        assertEquals(listOf("1", "2"), stores.map { ((it.left as ArrayRefDto).index as ConstantDto).value })
        assertEquals("undefined", (stores.first().right as ConstantDto).value)

        val scene = EtsScene(listOf(dto.toEtsFile()))
        val modelStmts = scene.projectClasses
            .single { it.name == DEFAULT_ARK_CLASS_NAME }
            .methods.single { it.name == DEFAULT_ARK_METHOD_NAME }
            .cfg.stmts.filterIsInstance<EtsAssignStmt>()
        val modelAllocation = modelStmts.map { it.rhv }.filterIsInstance<EtsNewArrayExpr>().single()
        val modelStores = modelStmts.mapNotNull { it.lhv as? EtsArrayAccess }

        assertEquals(EtsNumberConstant(4.0), modelAllocation.size)
        assertEquals(listOf(EtsNumberConstant(1.0), EtsNumberConstant(2.0)), modelStores.map { it.index })
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
    fun `dependency and hidden ets files do not switch a TypeScript project to arkanalyzer`() {
        val project = createTempDirectory("ts-provider-project")
        project.resolve("src").createDirectories()
        project.resolve("src/app.ts").writeText("export const answer = 42")
        project.resolve("node_modules/pkg").createDirectories()
        project.resolve("node_modules/pkg/index.ets").writeText("")
        project.resolve(".generated").createDirectories()
        project.resolve(".generated/cache.ets").writeText("")

        assertEquals(EtsIrProvider.TS_FRONTEND, defaultProviderFor(project, isProject = true))
    }

    @Test
    fun `visible ets file added to TypeScript project switches provider immediately`() {
        val project = createTempDirectory("mutable-ts-provider-project")
        project.resolve("src").createDirectories()
        project.resolve("src/app.ts").writeText("export const answer = 42")

        assertEquals(EtsIrProvider.TS_FRONTEND, defaultProviderFor(project, isProject = true))

        project.resolve("src/app.ets").writeText("")

        assertEquals(EtsIrProvider.ARKANALYZER, defaultProviderFor(project, isProject = true))
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
    fun `native frontend preserves source values in if conditions`() {
        val etsFileDto = runFrontend(
            """
                class Box {}

                function f(u: unknown, n: number, s: string, o: Box, b: boolean): number {
                    let result = 0;
                    if (u) result++;
                    if (n) result++;
                    if (s) result++;
                    if (o) result++;
                    if (!b) result++;
                    if (n > 0) result++;
                    return result;
                }
            """.trimIndent(),
        )

        val conditions = etsFileDto.classes
            .single { it.signature.name == DEFAULT_ARK_CLASS_NAME }
            .methods.single { it.signature.name == "f" }
            .body!!
            .cfg
            .blocks
            .flatMap { it.stmts }
            .filterIsInstance<IfStmtDto>()
            .map { it.condition }

        assertEquals(6, conditions.size)
        val directValues = conditions.filterIsInstance<LocalDto>().associateBy { it.name }
        assertEquals(setOf("u", "n", "s", "o"), directValues.keys)
        assertEquals(UnknownTypeDto, directValues.getValue("u").type)
        assertEquals(NumberTypeDto, directValues.getValue("n").type)
        assertEquals(StringTypeDto, directValues.getValue("s").type)
        assertTrue(directValues.getValue("o").type is ClassTypeDto)

        val unary = conditions.filterIsInstance<UnaryOperationDto>().single()
        assertEquals("!", unary.op)
        assertEquals("b", (unary.arg as LocalDto).name)

        val sourceComparison = conditions.filterIsInstance<RelationOperationDto>().single()
        assertEquals(">", sourceComparison.op)
        assertEquals("n", (sourceComparison.left as LocalDto).name)
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
    fun `catch edges survive JSON and reach the Kotlin graph only for throws`() {
        val frontendDto = runFrontend(
            """
                export function noThrow(): number {
                    try { return 1; } catch { return 2; }
                }

                export function mayThrow(fail: boolean): number {
                    try {
                        if (fail) throw 3;
                        return 1;
                    } catch (error) {
                        return error;
                    }
                }

                export function risky(): void { throw 3; }

                export function callThrow(): number {
                    try { risky(); return 1; } catch { return 2; }
                }
            """.trimIndent(),
        )

        val serialized = Json { serializersModule = dtoModule }.encodeToString(frontendDto)
        val roundTripped = EtsFileDto.loadFromJson(serialized)
        val dtoMethods = roundTripped.classes.single { it.signature.name == DEFAULT_ARK_CLASS_NAME }.methods
        val noThrowBlocks = dtoMethods.single { it.signature.name == "noThrow" }.body!!.cfg.blocks
        val throwingBlocks = dtoMethods.single { it.signature.name == "mayThrow" }.body!!.cfg.blocks
        val throwBlock = throwingBlocks.single { block -> block.stmts.any { it is ThrowStmtDto } }
        val edge = throwBlock.exceptionalSuccessors.single {
            throwBlock.stmts[it.stmtIndex] is ThrowStmtDto
        }

        assertTrue(noThrowBlocks.all { it.exceptionalSuccessors.isEmpty() })
        assertEquals(1, noThrowBlocks.flatMap { it.stmts }.count { it is org.jacodb.ets.dto.ReturnStmtDto })
        assertTrue(throwingBlocks[edge.target].stmts.any {
            it is AssignStmtDto && it.right is CaughtExceptionRefDto
        })

        val scene = EtsScene(listOf(roundTripped.toEtsFile()))
        val method = scene.projectClasses.single { it.name == DEFAULT_ARK_CLASS_NAME }
            .methods.single { it.name == "mayThrow" }
        val throwStmt = method.cfg.stmts.filterIsInstance<EtsThrowStmt>().single()
        val catchStmt = method.cfg.catchers(throwStmt).single()

        assertTrue(catchStmt is EtsAssignStmt && catchStmt.rhv is EtsCaughtExceptionRef)
        assertTrue(method.cfg.throwers(catchStmt).contains(throwStmt))

        val callMethod = scene.projectClasses.single { it.name == DEFAULT_ARK_CLASS_NAME }
            .methods.single { it.name == "callThrow" }
        val callStmt = callMethod.cfg.stmts.filterIsInstance<EtsCallStmt>().single()
        val callCatcher = callMethod.cfg.catchers(callStmt).single()

        assertTrue(callMethod.cfg.throwers(callCatcher).contains(callStmt))
    }

    @Test
    fun `raw fallback keeps its catch edge through JSON and the Kotlin graph`() {
        val frontendDto = runFrontend(
            """
                function raw(obj) {
                    try { with (obj) { x; } } catch { return 1; }
                    return 0;
                }
            """.trimIndent(),
            fileName = "test.js",
        )

        val methodDto = frontendDto.classes.single { it.signature.name == DEFAULT_ARK_CLASS_NAME }
            .methods.single { it.signature.name == "raw" }
        val rawBlock = methodDto.body!!.cfg.blocks.single { block ->
            block.stmts.any { it is RawStmtDto && it.kind == "UnsupportedStmt" }
        }
        val rawIndex = rawBlock.stmts.indexOfFirst { it is RawStmtDto && it.kind == "UnsupportedStmt" }
        val catcherId = rawBlock.exceptionalSuccessors.single { it.stmtIndex == rawIndex }.target

        assertTrue(methodDto.body!!.cfg.blocks[catcherId].stmts.any { it is org.jacodb.ets.dto.ReturnStmtDto })
        assertTrue(catcherId !in rawBlock.successors)

        val method = EtsScene(listOf(frontendDto.toEtsFile()))
            .projectClasses.single { it.name == DEFAULT_ARK_CLASS_NAME }
            .methods.single { it.name == "raw" }
        val rawStmt = method.cfg.stmts.filterIsInstance<EtsRawStmt>().single()
        val catchStmt = method.cfg.catchers(rawStmt).single()

        assertTrue(method.cfg.throwers(catchStmt).contains(rawStmt))
    }

    @Test
    fun `catch edge follows the instruction evaluating an if condition`() {
        val frontendDto = runFrontend(
            """
                export function risky(x: any): number {
                    try {
                        if (x == 1) return 1;
                        return 2;
                    } catch {
                        return 3;
                    }
                }
            """.trimIndent(),
        )

        val roundTripped = EtsFileDto.loadFromJson(Json { serializersModule = dtoModule }.encodeToString(frontendDto))
        val methodDto = roundTripped.classes.single { it.signature.name == DEFAULT_ARK_CLASS_NAME }
            .methods.single { it.signature.name == "risky" }
        val ifBlockDto = methodDto.body!!.cfg.blocks.single { block -> block.stmts.any { it is IfStmtDto } }
        assertTrue(ifBlockDto.exceptionalSuccessors.any { edge -> ifBlockDto.stmts[edge.stmtIndex] is IfStmtDto })

        val method = EtsScene(listOf(roundTripped.toEtsFile()))
            .projectClasses.single { it.name == DEFAULT_ARK_CLASS_NAME }
            .methods.single { it.name == "risky" }
        val conditionEvaluation = method.cfg.stmts.filterIsInstance<EtsAssignStmt>()
            .single { it.rhv is EtsEqExpr }
        val ifStmt = method.cfg.stmts.filterIsInstance<EtsIfStmt>().single()

        assertEquals(1, method.cfg.catchers(conditionEvaluation).size)
        assertTrue(method.cfg.catchers(ifStmt).isEmpty())
        assertTrue(method.cfg.throwers(method.cfg.catchers(conditionEvaluation).single()).contains(conditionEvaluation))
    }

    @Test
    fun `catch edge covers both generated instructions of a field assignment`() {
        val frontendDto = runFrontend(
            """
                export function assign(obj: any, x: any): number {
                    try {
                        obj.value = x;
                        return 1;
                    } catch {
                        return 2;
                    }
                }
            """.trimIndent(),
        )

        val originalClass = frontendDto.classes.single { it.signature.name == DEFAULT_ARK_CLASS_NAME }
        val originalMethod = originalClass.methods.single { it.signature.name == "assign" }
        val originalBody = originalMethod.body!!
        val originalBlock = originalBody.cfg.blocks.single { block ->
            block.stmts.any { it is AssignStmtDto && it.left !is LocalDto }
        }
        val storeIndex = originalBlock.stmts.indexOfFirst { it is AssignStmtDto && it.left !is LocalDto }
        assertTrue(originalBlock.exceptionalSuccessors.any { it.stmtIndex == storeIndex })

        // A single DTO assignment can carry a compound RHS even though the native frontend
        // usually lowers it to a separate DTO statement first.
        val store = originalBlock.stmts[storeIndex] as AssignStmtDto
        val x = originalBody.locals.single { it.name == "x" }
        val compoundStore = store.copy(
            right = RelationOperationDto(
                op = Ops.Relational.EQ,
                left = x,
                right = ConstantDto(value = "1", type = NumberTypeDto),
                type = BooleanTypeDto,
            ),
        )
        val modifiedBlock = originalBlock.copy(
            stmts = originalBlock.stmts.toMutableList().also { it[storeIndex] = compoundStore },
        )
        val modifiedMethod = originalMethod.copy(
            body = originalBody.copy(
                cfg = originalBody.cfg.copy(
                    blocks = originalBody.cfg.blocks.map { if (it.id == originalBlock.id) modifiedBlock else it },
                ),
            ),
        )
        val modifiedClass = originalClass.copy(
            methods = originalClass.methods.map { if (it.signature.name == "assign") modifiedMethod else it },
        )
        val modifiedDto = frontendDto.copy(
            classes = frontendDto.classes.map { if (it.signature.name == DEFAULT_ARK_CLASS_NAME) modifiedClass else it },
        )

        val method = EtsScene(listOf(modifiedDto.toEtsFile()))
            .projectClasses.single { it.name == DEFAULT_ARK_CLASS_NAME }
            .methods.single { it.name == "assign" }
        val comparison = method.cfg.stmts.filterIsInstance<EtsAssignStmt>().single { it.rhv is EtsEqExpr }
        val fieldStore = method.cfg.stmts.filterIsInstance<EtsAssignStmt>()
            .single { it.lhv is EtsInstanceFieldRef }
        val catcher = method.cfg.catchers(comparison).single()

        assertEquals(setOf(catcher), method.cfg.catchers(fieldStore))
        assertTrue(method.cfg.throwers(catcher).containsAll(listOf(comparison, fieldStore)))
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
