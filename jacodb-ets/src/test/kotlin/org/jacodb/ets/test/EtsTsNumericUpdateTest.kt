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
import org.jacodb.ets.dto.AssignStmtDto
import org.jacodb.ets.dto.BigIntTypeDto
import org.jacodb.ets.dto.EtsFileDto
import org.jacodb.ets.dto.LocalDto
import org.jacodb.ets.dto.NumberTypeDto
import org.jacodb.ets.dto.ReturnStmtDto
import org.jacodb.ets.dto.ToNumericExprDto
import org.jacodb.ets.dto.UnionTypeDto
import org.jacodb.ets.dto.dtoModule
import org.jacodb.ets.dto.toEtsFile
import org.jacodb.ets.model.EtsAssignStmt
import org.jacodb.ets.model.EtsBigIntType
import org.jacodb.ets.model.EtsEntity
import org.jacodb.ets.model.EtsExpr
import org.jacodb.ets.model.EtsNumberType
import org.jacodb.ets.model.EtsStmt
import org.jacodb.ets.model.EtsToNumericExpr
import org.jacodb.ets.model.EtsUnionType
import org.jacodb.ets.utils.AbstractHandler
import org.jacodb.ets.utils.DEFAULT_ARK_CLASS_NAME
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.generateEtsIR
import org.jacodb.ets.utils.getOperands
import org.junit.jupiter.api.Test
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EtsTsNumericUpdateTest {
    @Test
    fun `numeric old values survive production JSON decoding and model traversal`() {
        val source = """
            export function numberLocal(x: number): number { return x++; }
            export function bigintLocal(x: bigint): bigint { return x--; }
            export function computed(object: any, key: any): unknown { return object[key()]++; }
            export function array(values: (number | bigint)[]): unknown { return values[0]--; }
        """.trimIndent()
        val directory = createTempDirectory("ts-numeric-update-test")
        val input = directory.resolve("test.ts")
        input.writeText(source)

        val output = generateEtsIR(
            input,
            isProject = false,
            timeout = null,
            provider = EtsIrProvider.TS_FRONTEND,
        )
        assertTrue(output.exists(), message = "production frontend did not produce output")
        val frontendDto = EtsFileDto.loadFromJson(output.readText())
        val roundTripped = EtsFileDto.loadFromJson(
            Json { serializersModule = dtoModule }.encodeToString(frontendDto),
        )
        val dtoClass = roundTripped.classes.single { it.signature.name == DEFAULT_ARK_CLASS_NAME }
        val modelClass = roundTripped.toEtsFile().classes.single { it.name == DEFAULT_ARK_CLASS_NAME }
        val numericDto = UnionTypeDto(types = listOf(NumberTypeDto, BigIntTypeDto))
        val numericModel = EtsUnionType(types = listOf(EtsNumberType, EtsBigIntType))
        val expectedDtoTypes = mapOf(
            "numberLocal" to NumberTypeDto,
            "bigintLocal" to BigIntTypeDto,
            "computed" to numericDto,
            "array" to numericDto,
        )
        val expectedModelTypes = mapOf(
            "numberLocal" to EtsNumberType,
            "bigintLocal" to EtsBigIntType,
            "computed" to numericModel,
            "array" to numericModel,
        )

        for ((name, expectedType) in expectedDtoTypes) {
            val dtoStmts = dtoClass.methods.single { it.signature.name == name }
                .body!!.cfg.blocks.flatMap { it.stmts }
            val conversion = dtoStmts.filterIsInstance<AssignStmtDto>().single { it.right is ToNumericExprDto }
            val numeric = conversion.right as ToNumericExprDto
            val result = dtoStmts.filterIsInstance<ReturnStmtDto>().single().arg as LocalDto

            assertEquals(expected = expectedType, actual = numeric.type)
            assertEquals(expected = (conversion.left as LocalDto).name, actual = result.name)

            val modelStmts = modelClass.methods.single { it.name == name }.cfg.stmts
            val modelNumeric = modelStmts.filterIsInstance<EtsAssignStmt>()
                .mapNotNull { it.rhv as? EtsToNumericExpr }.single()
            assertEquals(expected = expectedModelTypes.getValue(name), actual = modelNumeric.type)
            assertEquals(expected = listOf(modelNumeric.arg), actual = modelNumeric.getOperands().toList())

            val visited = mutableListOf<EtsEntity>()
            val handler = object : AbstractHandler() {
                override fun handle(value: EtsEntity) {
                    visited += value
                }

                override fun handle(stmt: EtsStmt) = Unit
            }
            modelNumeric.accept(handler)
            assertEquals(expected = listOf(modelNumeric, modelNumeric.arg), actual = visited)

            val defaultVisitor = object : EtsExpr.Visitor.Default<String> {
                override fun defaultVisit(expr: EtsExpr): String = expr.javaClass.simpleName
            }
            assertEquals(expected = "EtsToNumericExpr", actual = modelNumeric.accept(defaultVisitor))
        }
    }
}
