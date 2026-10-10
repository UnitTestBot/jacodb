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

import java.math.BigInteger
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jacodb.ets.dto.ArrayRefDto
import org.jacodb.ets.dto.ArrayTypeDto
import org.jacodb.ets.dto.AssignStmtDto
import org.jacodb.ets.dto.AwaitExprDto
import org.jacodb.ets.dto.BigIntTypeDto
import org.jacodb.ets.dto.BooleanTypeDto
import org.jacodb.ets.dto.CaughtExceptionRefDto
import org.jacodb.ets.dto.ClassTypeDto
import org.jacodb.ets.dto.ClassValueRefDto
import org.jacodb.ets.dto.ClassValueTypeDto
import org.jacodb.ets.dto.ConstantDto
import org.jacodb.ets.dto.CopyDataPropertiesStmtDto
import org.jacodb.ets.dto.DefineAccessorStmtDto
import org.jacodb.ets.dto.DefineDataPropertyStmtDto
import org.jacodb.ets.dto.EtsFileDto
import org.jacodb.ets.dto.FunctionTypeDto
import org.jacodb.ets.dto.IfStmtDto
import org.jacodb.ets.dto.InstanceCallExprDto
import org.jacodb.ets.dto.InstanceOfExprDto
import org.jacodb.ets.dto.LexicalEnvTypeDto
import org.jacodb.ets.dto.LocalDto
import org.jacodb.ets.dto.NewArrayExprDto
import org.jacodb.ets.dto.NewExprDto
import org.jacodb.ets.dto.NumberTypeDto
import org.jacodb.ets.dto.Ops
import org.jacodb.ets.dto.PropertyRefDto
import org.jacodb.ets.dto.PtrCallExprDto
import org.jacodb.ets.dto.RawStmtDto
import org.jacodb.ets.dto.RawValueDto
import org.jacodb.ets.dto.RelationOperationDto
import org.jacodb.ets.dto.RequireObjectCoercibleExprDto
import org.jacodb.ets.dto.ReturnStmtDto
import org.jacodb.ets.dto.StaticCallExprDto
import org.jacodb.ets.dto.StaticFieldRefDto
import org.jacodb.ets.dto.StringTypeDto
import org.jacodb.ets.dto.SymbolTypeDto
import org.jacodb.ets.dto.ThrowStmtDto
import org.jacodb.ets.dto.ToPropertyKeyExprDto
import org.jacodb.ets.dto.UnaryOperationDto
import org.jacodb.ets.dto.UnknownTypeDto
import org.jacodb.ets.dto.ValueDto
import org.jacodb.ets.dto.YieldExprDto
import org.jacodb.ets.dto.dtoModule
import org.jacodb.ets.dto.toEtsFile
import org.jacodb.ets.model.EtsArrayAccess
import org.jacodb.ets.model.EtsAssignStmt
import org.jacodb.ets.model.EtsAwaitExpr
import org.jacodb.ets.model.EtsBigIntConstant
import org.jacodb.ets.model.EtsBigIntType
import org.jacodb.ets.model.EtsCallStmt
import org.jacodb.ets.model.EtsCaughtExceptionRef
import org.jacodb.ets.model.EtsClassValueRef
import org.jacodb.ets.model.EtsClassValueType
import org.jacodb.ets.model.EtsClosureFieldRef
import org.jacodb.ets.model.EtsCopyDataPropertiesStmt
import org.jacodb.ets.model.EtsDefineAccessorStmt
import org.jacodb.ets.model.EtsDefineDataPropertyStmt
import org.jacodb.ets.model.EtsEqExpr
import org.jacodb.ets.model.EtsFunctionType
import org.jacodb.ets.model.EtsIfStmt
import org.jacodb.ets.model.EtsInstanceFieldRef
import org.jacodb.ets.model.EtsInstanceOfExpr
import org.jacodb.ets.model.EtsLocal
import org.jacodb.ets.model.EtsNewArrayExpr
import org.jacodb.ets.model.EtsNewExpr
import org.jacodb.ets.model.EtsNumberConstant
import org.jacodb.ets.model.EtsPropertyRef
import org.jacodb.ets.model.EtsPtrCallExpr
import org.jacodb.ets.model.EtsRawEntity
import org.jacodb.ets.model.EtsRawStmt
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsStringConstant
import org.jacodb.ets.model.EtsSymbolType
import org.jacodb.ets.model.EtsThrowStmt
import org.jacodb.ets.model.EtsToPropertyKeyExpr
import org.jacodb.ets.model.EtsYieldExpr
import org.jacodb.ets.utils.DEFAULT_ARK_CLASS_NAME
import org.jacodb.ets.utils.DEFAULT_ARK_METHOD_NAME
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.defaultProviderFor
import org.jacodb.ets.utils.generateEtsIR
import org.jacodb.ets.utils.getOperands
import org.junit.jupiter.api.Test

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
    fun `symbol parameter and return types survive frontend JSON and model conversion`() {
        val frontendDto = runFrontend("export function same(value: symbol): symbol { return value; }")
        val roundTripped = EtsFileDto.loadFromJson(
            Json { serializersModule = dtoModule }.encodeToString(frontendDto),
        )
        val methodDto = roundTripped.classes.single { it.signature.name == DEFAULT_ARK_CLASS_NAME }
            .methods.single { it.signature.name == "same" }

        assertEquals(SymbolTypeDto, methodDto.signature.parameters.single().type)
        assertEquals(SymbolTypeDto, methodDto.signature.returnType)

        val modelMethod = roundTripped.toEtsFile().classes.single { it.name == DEFAULT_ARK_CLASS_NAME }
            .methods.single { it.name == "same" }

        assertEquals(EtsSymbolType, modelMethod.signature.parameters.single().type)
        assertEquals(EtsSymbolType, modelMethod.signature.returnType)
    }

    @Test
    fun `stored arrow lexical receiver survives JSON and model conversion`() {
        val frontendDto = runFrontend(
            """
                class Box {
                    offset = 11;
                    installOn(target: Box) {
                        target.callback = (value: number) => this.offset + value;
                    }
                    installOrdinaryOn(target: Box) {
                        target.callback = function(value: number) { return this.offset + value; };
                    }
                }
            """.trimIndent(),
        )
        val roundTripped = EtsFileDto.loadFromJson(
            Json { serializersModule = dtoModule }.encodeToString(frontendDto),
        )
        val box = roundTripped.toEtsFile().classes.single { it.name == "Box" }
        val install = box.methods.single { it.name == "installOn" }
        val arrowValue = install.cfg.stmts.filterIsInstance<EtsAssignStmt>()
            .map { it.rhv }
            .filterIsInstance<EtsLocal>()
            .single { it.name.startsWith("%AM") }
        val arrow = box.methods.single { it.name == arrowValue.name }
        val ordinary = box.methods.single { it.name.endsWith("\$installOrdinaryOn") }

        assertTrue((arrowValue.type as EtsFunctionType).isArrow)
        val receiver = arrow.cfg.stmts.filterIsInstance<EtsAssignStmt>()
            .single { (it.lhv as? EtsLocal)?.name == "this" }.rhv
        assertTrue(receiver is EtsClosureFieldRef)
        assertEquals("this", receiver.fieldName)
        assertTrue(ordinary.cfg.stmts.filterIsInstance<EtsAssignStmt>()
            .single { (it.lhv as? EtsLocal)?.name == "this" }.rhv is org.jacodb.ets.model.EtsThis)
    }

    @Test
    fun `bigint types and exact constants survive frontend JSON and model conversion`() {
        val frontendDto = runFrontend(
            """
                export function increment(x: bigint): bigint { return x + 1n; }
                export const large = 9007199254740993n;
                export const negative = -0x20n;
            """.trimIndent(),
        )
        val roundTripped = EtsFileDto.loadFromJson(
            Json { serializersModule = dtoModule }.encodeToString(frontendDto),
        )
        val defaultClass = roundTripped.classes.single { it.signature.name == DEFAULT_ARK_CLASS_NAME }
        val incrementDto = defaultClass.methods.single { it.signature.name == "increment" }
        val constantsDto = defaultClass.methods.flatMap { it.body?.cfg?.blocks.orEmpty() }
            .flatMap { it.stmts }
            .filterIsInstance<AssignStmtDto>()
            .mapNotNull { it.right as? ConstantDto }
            .filter { it.type == BigIntTypeDto }

        assertEquals(BigIntTypeDto, incrementDto.signature.parameters.single().type)
        assertEquals(BigIntTypeDto, incrementDto.signature.returnType)
        assertTrue(constantsDto.any { it.value == "9007199254740993" })
        assertTrue(constantsDto.any { it.value == "-32" })

        val modelClass = roundTripped.toEtsFile().classes.single { it.name == DEFAULT_ARK_CLASS_NAME }
        val incrementModel = modelClass.methods.single { it.name == "increment" }
        val bigInts = modelClass.methods.flatMap { it.cfg.stmts }
            .filterIsInstance<EtsAssignStmt>()
            .mapNotNull { it.rhv as? EtsBigIntConstant }

        assertEquals(EtsBigIntType, incrementModel.signature.parameters.single().type)
        assertEquals(EtsBigIntType, incrementModel.signature.returnType)
        assertTrue(bigInts.any { it.value == BigInteger("9007199254740993") })
        assertTrue(bigInts.any { it.value == BigInteger("-32") })
    }

    @Test
    fun `for await preserves async acquisition and sync value unwrapping`() {
        val frontendDto = runFrontend(
            """
                export async function first(input: AsyncIterable<number>): Promise<number> {
                    for await (const value of input) return value;
                    return -1;
                }
                export async function fromArray(input: number[]): Promise<number> {
                    for await (const value of input) return value;
                    return -1;
                }
            """.trimIndent(),
        )
        val roundTripped = EtsFileDto.loadFromJson(
            Json { serializersModule = dtoModule }.encodeToString(frontendDto),
        )
        for (name in listOf("first", "fromArray")) {
            val methodDto = roundTripped.classes.single { it.signature.name == DEFAULT_ARK_CLASS_NAME }
                .methods.single { it.signature.name == name }
            val assignments = methodDto.body!!.cfg.blocks.flatMap { it.stmts }.filterIsInstance<AssignStmtDto>()
            val calls = assignments.mapNotNull { it.right as? PtrCallExprDto }
            val awaitAssignments = assignments.filter { it.right is AwaitExprDto }
            val next = assignments.single { (it.right as? PtrCallExprDto)?.method?.name == "next" }
            val iterator = (next.right as PtrCallExprDto).receiver
            val nextAwait = awaitAssignments.single { (it.right as AwaitExprDto).arg == next.left }
            val nextValueReads = assignments.filter {
                (it.right as? org.jacodb.ets.dto.InstanceFieldRefDto)?.let { field ->
                    field.field.name == "value" && field.instance == nextAwait.left
                } == true
            }
            val syncValueAwait = awaitAssignments.single {
                (it.right as AwaitExprDto).arg == nextValueReads.first().left
            }
            val returnLookup = assignments.single {
                (it.right as? org.jacodb.ets.dto.InstanceFieldRefDto)?.let { field ->
                    field.field.name == "return" && field.instance == iterator
                } == true
            }
            val close = assignments.single { (it.right as? PtrCallExprDto)?.method?.name == "return" }
            val closeCall = close.right as PtrCallExprDto
            val closeValueRead = assignments.single {
                (it.right as? org.jacodb.ets.dto.InstanceFieldRefDto)?.let { field ->
                    field.field.name == "value" && field.instance == close.left
                } == true
            }
            val asyncCloseAwait = awaitAssignments.single { (it.right as AwaitExprDto).arg == close.left }
            val syncCloseAwait = awaitAssignments.single { (it.right as AwaitExprDto).arg == closeValueRead.left }
            val missingReturnAwait = awaitAssignments.single {
                ((it.right as AwaitExprDto).arg as? org.jacodb.ets.dto.ConstantDto)?.value == "undefined"
            }

            assertTrue(calls.any { it.method.name == "Symbol.asyncIterator" && it.receiver != null })
            assertTrue(calls.any { it.method.name == "Symbol.iterator" && it.receiver != null })
            assertTrue(iterator != null)
            assertEquals(2, nextValueReads.size)
            assertTrue(nextValueReads.all { it.left == syncValueAwait.left })
            assertEquals(returnLookup.left, closeCall.ptr)
            assertEquals(iterator, closeCall.receiver)
            assertTrue(assignments.any {
                (it.right as? org.jacodb.ets.dto.InstanceFieldRefDto)?.let { field ->
                    field.field.name == "done" && field.instance == close.left
                } == true
            })
            assertEquals(
                setOf(nextAwait, syncValueAwait, asyncCloseAwait, syncCloseAwait, missingReturnAwait),
                awaitAssignments.toSet(),
            )
            assertEquals(5, awaitAssignments.size)
            assertTrue(assignments.none { it.right is RawValueDto })

            val modelMethod = roundTripped.toEtsFile().classes.single { it.name == DEFAULT_ARK_CLASS_NAME }
                .methods.single { it.name == name }
            val modelAssignments = modelMethod.cfg.stmts.filterIsInstance<EtsAssignStmt>()
            val modelAwaits = modelAssignments.mapNotNull { it.rhv as? EtsAwaitExpr }
            val modelNext = modelAssignments.single {
                (it.rhv as? org.jacodb.ets.model.EtsPtrCallExpr)?.callee?.name == "next"
            }
            val modelClose = modelAssignments.single {
                (it.rhv as? org.jacodb.ets.model.EtsPtrCallExpr)?.callee?.name == "return"
            }

            assertEquals(awaitAssignments.size, modelAwaits.size)
            assertEquals(
                (iterator as org.jacodb.ets.dto.LocalDto).name,
                (modelNext.rhv as org.jacodb.ets.model.EtsPtrCallExpr).receiver?.name,
            )
            assertEquals(
                (modelNext.rhv as org.jacodb.ets.model.EtsPtrCallExpr).receiver,
                (modelClose.rhv as org.jacodb.ets.model.EtsPtrCallExpr).receiver,
            )
            for (dtoAwait in listOf(nextAwait, syncValueAwait, asyncCloseAwait, syncCloseAwait)) {
                val argument = (dtoAwait.right as AwaitExprDto).arg as org.jacodb.ets.dto.LocalDto
                assertTrue(modelAwaits.any { it.arg.name == argument.name })
            }
        }
    }

    @Test
    fun `computed object keys survive the frontend JSON round trip`() {
        val frontendDto = runFrontend(
            """
                export function read(key: string): number {
                    const object = { [key]: 1 };
                    const { [key]: value } = object;
                    return value;
                }
            """.trimIndent(),
        )

        val serialized = Json { serializersModule = dtoModule }.encodeToString(frontendDto)
        val roundTripped = EtsFileDto.loadFromJson(serialized)
        val dtoStmts = roundTripped.classes.single { it.signature.name == DEFAULT_ARK_CLASS_NAME }
            .methods.single { it.signature.name == "read" }
            .body!!.cfg.blocks.flatMap { it.stmts }
        val assignments = dtoStmts.filterIsInstance<AssignStmtDto>()

        assertEquals(1, dtoStmts.filterIsInstance<DefineDataPropertyStmtDto>().size)
        assertTrue(assignments.any { it.right is PropertyRefDto })
        assertEquals(2, assignments.count { it.right is ToPropertyKeyExprDto })
        assertTrue(assignments.none { it.right is RawValueDto })

        val scene = EtsScene(listOf(roundTripped.toEtsFile()))
        val method = scene.projectClasses.single { it.name == DEFAULT_ARK_CLASS_NAME }
            .methods.single { it.name == "read" }
        val converted = method.cfg.stmts.filterIsInstance<EtsAssignStmt>()
        assertEquals(1, method.cfg.stmts.filterIsInstance<EtsDefineDataPropertyStmt>().size)
        assertTrue(converted.any { it.rhv is EtsPropertyRef })
        assertEquals(2, converted.count { it.rhv is EtsToPropertyKeyExpr })
    }

    @Test
    fun `object spread and rest preserve copy operations through JSON conversion`() {
        val frontendDto = runFrontend(
            """
                export function copy(input: { x: number; y: number }): number {
                    const object = { ...input, y: 7 };
                    const { x, ...rest } = object;
                    return rest.y + x;
                }
            """.trimIndent(),
        )

        val serialized = Json { serializersModule = dtoModule }.encodeToString(frontendDto)
        val roundTripped = EtsFileDto.loadFromJson(serialized)
        val dtoStmts = roundTripped.classes.single { it.signature.name == DEFAULT_ARK_CLASS_NAME }
            .methods.single { it.signature.name == "copy" }
            .body!!.cfg.blocks.flatMap { it.stmts }
        val copies = dtoStmts.filterIsInstance<CopyDataPropertiesStmtDto>()

        assertEquals(2, copies.size)
        assertFalse(copies[0].throwOnNullishSource)
        assertTrue(copies[1].throwOnNullishSource)
        assertEquals("x", (copies[1].excludedKeys.single() as ConstantDto).value)

        val scene = EtsScene(listOf(roundTripped.toEtsFile()))
        val method = scene.projectClasses.single { it.name == DEFAULT_ARK_CLASS_NAME }
            .methods.single { it.name == "copy" }
        val converted = method.cfg.stmts.filterIsInstance<EtsCopyDataPropertiesStmt>()
        assertEquals(2, converted.size)
        assertEquals("x", (converted[1].excludedKeys.single() as EtsStringConstant).value)
    }

    @Test
    fun `object getter descriptor and lifted body survive JSON conversion`() {
        val frontendDto = runFrontend(
            """
                export function value(seed: number): number {
                    const object = { y: 2, get x() { return seed + this.y; } };
                    return object.x;
                }
            """.trimIndent(),
        )

        val serialized = Json { serializersModule = dtoModule }.encodeToString(frontendDto)
        val roundTripped = EtsFileDto.loadFromJson(serialized)
        val dtoMethods = roundTripped.classes.flatMap { it.methods }
        val descriptor = dtoMethods.single { it.signature.name == "value" }
            .body!!.cfg.blocks.flatMap { it.stmts }.filterIsInstance<DefineAccessorStmtDto>().single()
        val getterType = (descriptor.getter as LocalDto).type as FunctionTypeDto
        val getterBody = dtoMethods.single { it.signature.name == getterType.signature.name }.body!!

        assertEquals("x", (descriptor.key as ConstantDto).value)
        assertTrue(getterBody.cfg.blocks.flatMap { it.stmts }.any { it is ReturnStmtDto })
        assertTrue(getterBody.locals.any { it.type is LexicalEnvTypeDto })

        val scene = EtsScene(listOf(roundTripped.toEtsFile()))
        val method = scene.projectClasses.flatMap { it.methods }.single { it.name == "value" }
        val converted = method.cfg.stmts.filterIsInstance<EtsDefineAccessorStmt>().single()
        assertEquals("x", (converted.key as EtsStringConstant).value)
        assertTrue(method.cfg.stmts.filterIsInstance<EtsAssignStmt>().any { it.rhv is EtsPropertyRef })
    }

    @Test
    fun `array expansion and parameter defaults survive frontend JSON conversion`() {
        val dto = runFrontend(
            """
            export function defaults(x = 3): number { return x; }
            export function spread(input: number[]): number[] { return [0, ...input]; }
            export function tail(input: number[]): number[] { const [first, ...rest] = input; return rest; }
            export function count(...values: number[]): number { return values.length; }
            """.trimIndent(),
        )
        val serialized = Json { serializersModule = dtoModule }.encodeToString(dto)
        val roundTripped = EtsFileDto.loadFromJson(serialized)
        val defaultClass = roundTripped.classes.single { it.signature.name == DEFAULT_ARK_CLASS_NAME }
        val modelClass = roundTripped.toEtsFile().classes.single { it.name == DEFAULT_ARK_CLASS_NAME }

        assertTrue(defaultClass.methods.single { it.signature.name == "count" }.signature.parameters.single().isRest)
        assertTrue(modelClass.methods.single { it.name == "defaults" }.cfg.stmts.any { it is EtsIfStmt })
        assertTrue(modelClass.methods.single { it.name == "spread" }.cfg.stmts.any {
            it is EtsAssignStmt && it.rhv is EtsNewArrayExpr
        })
        assertTrue(modelClass.methods.single { it.name == "tail" }.cfg.stmts.any {
            it is EtsAssignStmt && it.rhv is EtsNewArrayExpr
        })
        val spreadCalls = defaultClass.methods.single { it.signature.name == "spread" }.body!!.cfg.blocks
            .flatMap { it.stmts }.filterIsInstance<AssignStmtDto>().mapNotNull { it.right as? PtrCallExprDto }
        val modelCalls = modelClass.methods.single { it.name == "spread" }.cfg.stmts
            .filterIsInstance<EtsAssignStmt>().mapNotNull { it.rhv as? EtsPtrCallExpr }

        assertEquals(1, spreadCalls.size)
        assertEquals(1, modelCalls.size)
        assertTrue(spreadCalls.single().receiver is LocalDto)
        assertEquals((spreadCalls.single().receiver as LocalDto).name, modelCalls.single().receiver!!.name)
        assertTrue(modelCalls.single().getOperands().any { it == modelCalls.single().receiver })
    }

    @Test
    fun `regular expression literal preserves pattern and flags through JSON conversion`() {
        val dto = runFrontend("export function containsX(value: string): boolean { return /x+/gi.test(value); }")
        val serialized = Json { serializersModule = dtoModule }.encodeToString(dto)
        val roundTripped = EtsFileDto.loadFromJson(serialized)
        val method = roundTripped.classes.single { it.signature.name == DEFAULT_ARK_CLASS_NAME }
            .methods.single { it.signature.name == "containsX" }
        val assignments = method.body!!.cfg.blocks.flatMap { it.stmts }.filterIsInstance<AssignStmtDto>()
        val constructor = assignments.mapNotNull { it.right as? InstanceCallExprDto }
            .single { it.method.name == "constructor" }
        val testCall = assignments.mapNotNull { it.right as? PtrCallExprDto }
            .single { it.method.name == "test" }

        assertEquals(listOf("x+", "gi"), constructor.args.map { (it as ConstantDto).value })
        assertTrue(testCall.receiver is LocalDto)
        assertTrue(roundTripped.toEtsFile().classes.single { it.name == DEFAULT_ARK_CLASS_NAME }
            .methods.single { it.name == "containsX" }.cfg.stmts.any {
                it is EtsAssignStmt && it.rhv is EtsNewExpr
            })
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
    fun `mutable namespaced constructor in default export remains unsupported in JSON`() {
        val dto = runFrontend(
            """
                namespace N { export class Box { constructor(public value: number) {} } }
                export default new N.Box(7);
            """.trimIndent()
        )

        val defaultClass = dto.classes.single { it.signature.name == DEFAULT_ARK_CLASS_NAME }
        val stmts = defaultClass.methods.single { it.signature.name == DEFAULT_ARK_METHOD_NAME }
            .body!!.cfg.blocks.flatMap { it.stmts }

        assertTrue(stmts.any { it is RawStmtDto && it.kind == "UnsupportedStmt" })
        assertTrue(stmts.filterIsInstance<AssignStmtDto>().none { it.right is NewExprDto })
        assertTrue(defaultClass.fields.none { it.signature.name == "default" })
        assertTrue(dto.toEtsFile().classes.single { it.name == DEFAULT_ARK_CLASS_NAME }.fields.none { it.name == "default" })
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
        assertTrue(dto.exportInfos.any { it.exportName == "default" && it.nameBeforeAs == "A" })
        val defaultClass = dto.classes.single { it.signature.name == DEFAULT_ARK_CLASS_NAME }
        assertEquals(ClassValueTypeDto(classSignature), defaultClass.fields.single { it.signature.name == "default" }.signature.type)
        assertTrue(defaultClass.methods.single { it.signature.name == DEFAULT_ARK_METHOD_NAME }
            .body!!.cfg.blocks.flatMap { it.stmts }.filterIsInstance<AssignStmtDto>()
            .any { (it.left as? StaticFieldRefDto)?.field?.name == "default" && it.right == returnedClass })

        val model = dto.toEtsFile()
        assertEquals("default", model.exportInfos.single { it.isDefaultExport }.name)
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
    fun `dynamic new retains evaluated constructor after JSON and model conversion`() {
        val frontendDto = runFrontend(
            """
                class A { constructor(value: number) {} }
                class B { constructor(value: number) {} }
                let selected: typeof A | typeof B = A;
                function pick(): typeof A | typeof B { return selected; }
                function argument(): number { selected = B; return 7; }
                export function check(): A | B { return new (pick())(argument()); }
            """.trimIndent(),
        )
        val roundTripped = EtsFileDto.loadFromJson(
            Json { serializersModule = dtoModule }.encodeToString(frontendDto),
        )
        val methodDto = roundTripped.classes.single { it.signature.name == DEFAULT_ARK_CLASS_NAME }
            .methods.single { it.signature.name == "check" }
        val assignments = methodDto.body!!.cfg.blocks.flatMap { it.stmts }.filterIsInstance<AssignStmtDto>()
        val pickCall = assignments.single { (it.right as? StaticCallExprDto)?.method?.name == "pick" }
        val argumentCall = assignments.single { (it.right as? StaticCallExprDto)?.method?.name == "argument" }
        val allocation = assignments.single { it.right is NewExprDto }
        val allocationValue = allocation.right as NewExprDto

        assertEquals(pickCall.left as LocalDto, allocationValue.constructorValue as LocalDto)
        assertTrue(assignments.indexOf(pickCall) < assignments.indexOf(argumentCall))
        assertTrue(assignments.indexOf(argumentCall) < assignments.indexOf(allocation))

        val modelMethod = roundTripped.toEtsFile().classes.single { it.name == DEFAULT_ARK_CLASS_NAME }
            .methods.single { it.name == "check" }
        val modelAllocation = modelMethod.cfg.stmts.filterIsInstance<EtsAssignStmt>()
            .single { it.rhv is EtsNewExpr }.rhv as EtsNewExpr
        val constructorValue = modelAllocation.constructorValue as EtsLocal

        assertEquals((pickCall.left as LocalDto).name, constructorValue.name)
        assertEquals(constructorValue, modelAllocation.getOperands().single())
    }

    @Test
    fun `computed constructor access keeps property semantics through JSON and model conversion`() {
        val frontendDto = runFrontend(
            """
                class A {}
                export function make(holder: { Ctor: typeof A }): A {
                    return new holder["Ctor"]();
                }
            """.trimIndent(),
        )
        val methodDto = frontendDto.classes.single { it.signature.name == DEFAULT_ARK_CLASS_NAME }
            .methods.single { it.signature.name == "make" }
        val assignments = methodDto.body!!.cfg.blocks.flatMap { it.stmts }.filterIsInstance<AssignStmtDto>()

        val property = assignments.single { it.right is org.jacodb.ets.dto.PropertyRefDto }
        val allocation = assignments.single { it.right is NewExprDto }.right as NewExprDto
        assertEquals(property.left as LocalDto, allocation.constructorValue as LocalDto)
        assertTrue(assignments.none { it.right is ArrayRefDto || it.right is RawValueDto })

        val modelMethod = frontendDto.toEtsFile().classes.single { it.name == DEFAULT_ARK_CLASS_NAME }
            .methods.single { it.name == "make" }
        val modelAssignments = modelMethod.cfg.stmts.filterIsInstance<EtsAssignStmt>()

        assertTrue(modelAssignments.any { it.rhv is org.jacodb.ets.model.EtsPropertyRef })
        assertTrue(modelAssignments.any { it.rhv is EtsNewExpr })
        assertTrue(modelAssignments.none { it.rhv is EtsRawEntity })
    }

    @Test
    fun `legacy new JSON keeps its static type without a constructor value`() {
        val legacyJson = """
            {
              "_": "NewExpr",
              "classType": { "_": "UnknownType" }
            }
        """.trimIndent()

        val decoded = Json { serializersModule = dtoModule }
            .decodeFromString(ValueDto.serializer(), legacyJson) as NewExprDto

        assertEquals(UnknownTypeDto, decoded.classType)
        assertEquals(null, decoded.constructorValue)
    }

    @Test
    fun `instanceof constructor call survives frontend JSON and model conversion`() {
        val dto = runFrontend(
            """
                class A {}
                function choose(): typeof A { return A; }
                export function check(value: object): boolean {
                    return value instanceof choose();
                }
            """.trimIndent(),
        )
        val defaultClass = dto.classes.single { it.signature.name == DEFAULT_ARK_CLASS_NAME }
        val checkMethod = defaultClass.methods.single { it.signature.name == "check" }
        val assignments = checkMethod.body!!.cfg.blocks.flatMap { it.stmts }.filterIsInstance<AssignStmtDto>()
        val constructorCall = assignments.single { (it.right as? StaticCallExprDto)?.method?.name == "choose" }
        val instanceCheck = assignments.single { it.right is InstanceOfExprDto }.right as InstanceOfExprDto

        assertEquals(constructorCall.left, instanceCheck.checkValue)
        assertEquals(null, instanceCheck.checkType)

        val model = dto.toEtsFile()
        val modelMethod = model.classes.single { it.name == DEFAULT_ARK_CLASS_NAME }
            .methods.single { it.name == "check" }
        val modelCheck = modelMethod.cfg.stmts.filterIsInstance<EtsAssignStmt>()
            .single { it.rhv is EtsInstanceOfExpr }.rhv as EtsInstanceOfExpr

        assertEquals((constructorCall.left as LocalDto).name, (modelCheck.checkValue as EtsLocal).name)
        assertEquals(null, modelCheck.checkType)
    }

    @Test
    fun `legacy instanceof JSON keeps its static type without a constructor value`() {
        val legacyJson = """
            {
              "_": "InstanceOfExpr",
              "arg": { "_": "Constant", "value": "null", "type": { "_": "NullType" } },
              "checkType": { "_": "UnknownType" }
            }
        """.trimIndent()

        val decoded = Json { serializersModule = dtoModule }
            .decodeFromString(ValueDto.serializer(), legacyJson) as InstanceOfExprDto

        assertEquals(UnknownTypeDto, decoded.checkType)
        assertEquals(null, decoded.checkValue)
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
    fun `yield delegation survives frontend JSON and model conversion`() {
        val frontendDto = runFrontend(
            """
                export function* values() {
                    yield* [1, 2];
                    yield [3];
                }
            """.trimIndent(),
        )
        val roundTripped = EtsFileDto.loadFromJson(
            Json { serializersModule = dtoModule }.encodeToString(frontendDto),
        )
        val methodDto = roundTripped.classes.single { it.signature.name == DEFAULT_ARK_CLASS_NAME }
            .methods.single { it.signature.name == "values" }
        val yieldsDto = methodDto.body!!.cfg.blocks.flatMap { it.stmts }
            .filterIsInstance<AssignStmtDto>()
            .mapNotNull { it.right as? YieldExprDto }

        assertEquals(listOf(true, false), yieldsDto.map { it.isDelegating })

        val modelMethod = roundTripped.toEtsFile().classes.single { it.name == DEFAULT_ARK_CLASS_NAME }
            .methods.single { it.name == "values" }
        val modelYields = modelMethod.cfg.stmts.filterIsInstance<EtsAssignStmt>()
            .mapNotNull { it.rhv as? EtsYieldExpr }

        assertEquals(listOf(true, false), modelYields.map { it.isDelegating })
        assertTrue(modelYields[0].toString().startsWith("yield* "))
        assertTrue(modelYields[1].toString().startsWith("yield "))
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

    @Test
    fun `computed object binding reuses its normalized key in rest through JSON conversion`() {
        val frontendDto = runFrontend(
            """
                export function pick(input: any, key: any): number {
                    const { [key]: value, ...rest } = input;
                    return value + rest.y;
                }
            """.trimIndent(),
        )
        val serialized = Json { serializersModule = dtoModule }.encodeToString(frontendDto)
        val roundTripped = EtsFileDto.loadFromJson(serialized)
        val dtoStmts = roundTripped.classes.single { it.signature.name == DEFAULT_ARK_CLASS_NAME }
            .methods.single { it.signature.name == "pick" }.body!!.cfg.blocks.flatMap { it.stmts }
        val assignments = dtoStmts.filterIsInstance<AssignStmtDto>()
        val read = assignments.mapNotNull { it.right as? PropertyRefDto }.single()
        val copy = dtoStmts.filterIsInstance<CopyDataPropertiesStmtDto>().single()

        assertEquals(expected = 1, actual = assignments.count { it.right is ToPropertyKeyExprDto })
        assertEquals(expected = read.key, actual = copy.excludedKeys.single())
        assertTrue(assignments.none { it.right is RawValueDto })

        val method = roundTripped.toEtsFile().classes.single { it.name == DEFAULT_ARK_CLASS_NAME }
            .methods.single { it.name == "pick" }
        val converted = method.cfg.stmts.filterIsInstance<EtsAssignStmt>()
        val modelRead = converted.mapNotNull { it.rhv as? EtsPropertyRef }.single()
        val modelCopy = method.cfg.stmts.filterIsInstance<EtsCopyDataPropertiesStmt>().single()

        assertEquals(expected = 1, actual = converted.count { it.rhv is EtsToPropertyKeyExpr })
        assertEquals(expected = modelRead.key, actual = modelCopy.excludedKeys.single())
    }

    @Test
    fun `object rest preserves excluded keys through JSON conversion`() {
        val frontendDto = runFrontend(
            """
                export function copy(input: { x: number; y: number }): number {
                    const { x, ...rest } = input;
                    return rest.y + x;
                }
            """.trimIndent(),
        )

        val serialized = Json { serializersModule = dtoModule }.encodeToString(frontendDto)
        val roundTripped = EtsFileDto.loadFromJson(serialized)
        val dtoStmts = roundTripped.classes.single { it.signature.name == DEFAULT_ARK_CLASS_NAME }
            .methods.single { it.signature.name == "copy" }
            .body!!.cfg.blocks.flatMap { it.stmts }
        val copies = dtoStmts.filterIsInstance<CopyDataPropertiesStmtDto>()

        assertTrue(dtoStmts.filterIsInstance<AssignStmtDto>().any { it.right is RequireObjectCoercibleExprDto })
        assertEquals(1, copies.size)
        assertTrue(copies[0].throwOnNullishSource)
        assertEquals("x", (copies[0].excludedKeys.single() as ConstantDto).value)

        val scene = EtsScene(listOf(roundTripped.toEtsFile()))
        val method = scene.projectClasses.single { it.name == DEFAULT_ARK_CLASS_NAME }
            .methods.single { it.name == "copy" }
        val converted = method.cfg.stmts.filterIsInstance<EtsCopyDataPropertiesStmt>()
        assertEquals(1, converted.size)
        assertEquals("x", (converted[0].excludedKeys.single() as EtsStringConstant).value)
    }

    @Test
    fun `object spread preserves ordered copy operations through JSON conversion`() {
        val frontendDto = runFrontend(
            """
                export function copy(input: { x: number; y: number }): number {
                    const object = { ...input, y: 7 };
                    return object.y + object.x;
                }
            """.trimIndent(),
        )

        val serialized = Json { serializersModule = dtoModule }.encodeToString(frontendDto)
        val roundTripped = EtsFileDto.loadFromJson(serialized)
        val dtoStmts = roundTripped.classes.single { it.signature.name == DEFAULT_ARK_CLASS_NAME }
            .methods.single { it.signature.name == "copy" }
            .body!!.cfg.blocks.flatMap { it.stmts }
        val copies = dtoStmts.filterIsInstance<CopyDataPropertiesStmtDto>()

        assertEquals(1, copies.size)
        assertFalse(copies[0].throwOnNullishSource)
        assertTrue(copies[0].excludedKeys.isEmpty())

        val scene = EtsScene(listOf(roundTripped.toEtsFile()))
        val method = scene.projectClasses.single { it.name == DEFAULT_ARK_CLASS_NAME }
            .methods.single { it.name == "copy" }
        val converted = method.cfg.stmts.filterIsInstance<EtsCopyDataPropertiesStmt>()
        assertEquals(1, converted.size)
        assertTrue(converted[0].excludedKeys.isEmpty())
    }
}
