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
import org.jacodb.ets.dto.ClassValueRefDto
import org.jacodb.ets.dto.EtsFileDto
import org.jacodb.ets.dto.LocalDto
import org.jacodb.ets.dto.NewExprDto
import org.jacodb.ets.dto.RawValueDto
import org.jacodb.ets.dto.dtoModule
import org.jacodb.ets.dto.toEtsFile
import org.jacodb.ets.model.EtsAssignStmt
import org.jacodb.ets.model.EtsNewExpr
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


class EtsTsComputedConstructorTest {
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

}
