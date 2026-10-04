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
import org.jacodb.ets.dto.EtsFileDto
import org.jacodb.ets.dto.RawValueDto
import org.jacodb.ets.dto.SpreadExpansionExprDto
import org.jacodb.ets.dto.dtoModule
import org.jacodb.ets.dto.toEtsFile
import org.jacodb.ets.model.EtsAssignStmt
import org.jacodb.ets.model.EtsSpreadExpansionExpr
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


class EtsTsConstructorSpreadTest {
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
    fun `constructor spreads preserve iterator expansion and ordered arguments`() {
        val dto = frontendRoundTrip(
            """
                function add(a: number, b: number): number { return a + b; }
                class Point { constructor(public x: number, public y: number) {} }
                export function sum(pair: [number, number]): number { return add(...pair); }
                export function make(pair: [number, number]): number { return new Point(...pair).y; }
            """.trimIndent(),
        )

        for ((name, target) in listOf("make" to "constructor")) {
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

}
