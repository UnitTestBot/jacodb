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
import org.jacodb.ets.dto.AssignStmtDto
import org.jacodb.ets.dto.CallExprDto
import org.jacodb.ets.dto.ClassValueRefDto
import org.jacodb.ets.dto.EtsFileDto
import org.jacodb.ets.dto.LocalDto
import org.jacodb.ets.dto.NewClassExprDto
import org.jacodb.ets.dto.NewExprDto
import org.jacodb.ets.dto.PtrCallExprDto
import org.jacodb.ets.dto.RawValueDto
import org.jacodb.ets.dto.SpreadExpansionExprDto
import org.jacodb.ets.dto.TemplateObjectExprDto
import org.jacodb.ets.dto.dtoModule
import org.jacodb.ets.dto.toEtsFile
import org.jacodb.ets.model.EtsAssignStmt
import org.jacodb.ets.model.EtsNewClassExpr
import org.jacodb.ets.model.EtsNewExpr
import org.jacodb.ets.model.EtsPtrCallExpr
import org.jacodb.ets.model.EtsSpreadExpansionExpr
import org.jacodb.ets.model.EtsTemplateObjectExpr
import org.jacodb.ets.utils.DEFAULT_ARK_CLASS_NAME
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.generateEtsIR
import org.jacodb.ets.utils.getOperands
import org.junit.jupiter.api.Test
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EtsTsCallLoweringTest {
    private fun frontendRoundTrip(source: String): EtsFileDto {
        val input = createTempDirectory("ts-calls-test").resolve("test.ts")
        input.writeText(source)

        val output = generateEtsIR(
            input,
            isProject = false,
            timeout = null,
            provider = EtsIrProvider.TS_FRONTEND,
        )
        val dto = EtsFileDto.loadFromJson(output.readText())
        return EtsFileDto.loadFromJson(Json { serializersModule = dtoModule }.encodeToString(dto))
    }

    private fun assignments(dto: EtsFileDto, method: String): List<AssignStmtDto> =
        dto.classes.single { it.signature.name == DEFAULT_ARK_CLASS_NAME }
            .methods.single { it.signature.name == method }.body!!.cfg.blocks
            .flatMap { it.stmts }.filterIsInstance<AssignStmtDto>()

    @Test
    fun `comma constructor values decode as locals after computed access`() {
        val dto = frontendRoundTrip(
            """
                class A { x = 1; }
                export function computed(holder: any): A { return new (holder['sideEffect'], A)(); }
                export function direct(): A { return new (0, A)(); }
            """.trimIndent(),
        )

        for (name in listOf("computed", "direct")) {
            val stmts = assignments(dto, name)
            val allocation = stmts.single { it.right is NewExprDto }
            val constructor = (allocation.right as NewExprDto).constructorValue as LocalDto
            val capture = stmts.single { it.left == constructor && it.right is ClassValueRefDto }

            assertEquals("A", (capture.right as ClassValueRefDto).signature.name)
            assertTrue(stmts.indexOf(capture) < stmts.indexOf(allocation))
            assertTrue(stmts.none { it.right is RawValueDto })

            val model = dto.toEtsFile().classes.single { it.name == DEFAULT_ARK_CLASS_NAME }
                .methods.single { it.name == name }.cfg.stmts.filterIsInstance<EtsAssignStmt>()
            val newExpr = model.single { it.rhv is EtsNewExpr }.rhv as EtsNewExpr
            assertTrue(newExpr.constructorValue != null)
        }
    }

    @Test
    fun `call and constructor spreads preserve iterator expansion and ordered arguments`() {
        val dto = frontendRoundTrip(
            """
                function add(a: number, b: number): number { return a + b; }
                class Point { constructor(public x: number, public y: number) {} }
                export function sum(pair: [number, number]): number { return add(...pair); }
                export function make(pair: [number, number]): number { return new Point(...pair).y; }
            """.trimIndent(),
        )

        for ((name, target) in listOf("sum" to "add", "make" to "constructor")) {
            val stmts = assignments(dto, name)
            val expansion = stmts.single { it.right is SpreadExpansionExprDto }
            val reads = stmts.filter { it.right is ArrayRefDto }
            val call = stmts.single { (it.right as? CallExprDto)?.method?.name == target }.right as CallExprDto

            assertEquals(2, (expansion.right as SpreadExpansionExprDto).expectedCount)
            assertEquals(2, reads.size)
            assertEquals(reads.map { it.left }, call.args)
            assertTrue(reads.all { (it.right as ArrayRefDto).array == expansion.left })
            assertTrue(stmts.indexOf(expansion) < stmts.indexOf(reads.first()))
            assertTrue(stmts.none { it.right is RawValueDto })

            val model = dto.toEtsFile().classes.single { it.name == DEFAULT_ARK_CLASS_NAME }
                .methods.single { it.name == name }.cfg.stmts.filterIsInstance<EtsAssignStmt>()
            val expanded = model.single { it.rhv is EtsSpreadExpansionExpr }.rhv as EtsSpreadExpansionExpr
            assertEquals(2, expanded.expectedCount)
            assertEquals(listOf(expanded.iterable), expanded.getOperands().toList())
        }
    }

    @Test
    fun `class expressions retain constructor identity field initialization and visitor support`() {
        val dto = frontendRoundTrip(
            """
                export function value(): number {
                    const Local = class { x = 1; };
                    return new Local().x;
                }
            """.trimIndent(),
        )
        val stmts = assignments(dto, "value")
        val creation = stmts.single { it.right is NewClassExprDto }.right as NewClassExprDto
        val clazz = dto.classes.single { it.signature == creation.signature }
        val allocation = stmts.single { it.right is NewExprDto }.right as NewExprDto

        assertEquals(creation.signature, (allocation.classType as org.jacodb.ets.dto.ClassTypeDto).signature)
        assertTrue(clazz.fields.any { it.signature.name == "x" })
        assertTrue(clazz.methods.any { it.signature.name == "%instInit" && it.body != null })
        assertTrue(clazz.methods.any { it.signature.name == "constructor" && it.body != null })
        assertTrue(stmts.none { it.right is RawValueDto })

        val model = dto.toEtsFile().classes.single { it.name == DEFAULT_ARK_CLASS_NAME }
            .methods.single { it.name == "value" }.cfg.stmts.filterIsInstance<EtsAssignStmt>()
        val newClass = model.single { it.rhv is EtsNewClassExpr }.rhv as EtsNewClassExpr
        assertTrue(newClass.getOperands().none())
        assertEquals(creation.signature.name, newClass.signature.name)
    }

    @Test
    fun `tagged template objects preserve raw cooked undefined sites and method receivers`() {
        val dto = frontendRoundTrip(
            """
                class Tagger {
                    tag(parts: TemplateStringsArray, value: number): string { return parts.raw[0] + value; }
                }
                export function value(tagger: Tagger): string { return tagger.tag`\unicode${'$'}{1}`; }
            """.trimIndent(),
        )
        val stmts = assignments(dto, "value")
        val creation = stmts.single { it.right is TemplateObjectExprDto }
        val template = creation.right as TemplateObjectExprDto
        val call = stmts.single { it.right is PtrCallExprDto }.right as PtrCallExprDto

        assertEquals(listOf(null, ""), template.cooked)
        assertEquals(listOf("\\unicode", ""), template.raw)
        assertTrue(template.siteId.isNotEmpty())
        assertEquals(creation.left, call.args.first())
        assertTrue(call.receiver != null)
        assertTrue(stmts.none { it.right is RawValueDto })

        val model = dto.toEtsFile().classes.single { it.name == DEFAULT_ARK_CLASS_NAME }
            .methods.single { it.name == "value" }.cfg.stmts.filterIsInstance<EtsAssignStmt>()
        val modelTemplate = model.single { it.rhv is EtsTemplateObjectExpr }.rhv as EtsTemplateObjectExpr
        val modelCall = model.single { it.rhv is EtsPtrCallExpr }.rhv as EtsPtrCallExpr
        assertEquals(template.siteId, modelTemplate.siteId)
        assertEquals(template.cooked, modelTemplate.cooked)
        assertEquals(template.raw, modelTemplate.raw)
        assertTrue(modelTemplate.getOperands().none())
        assertTrue(modelCall.getOperands().any { it == modelCall.receiver })
    }
}
