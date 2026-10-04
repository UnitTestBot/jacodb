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
import org.jacodb.ets.dto.EtsFileDto
import org.jacodb.ets.dto.NewClassExprDto
import org.jacodb.ets.dto.NewExprDto
import org.jacodb.ets.dto.RawValueDto
import org.jacodb.ets.dto.dtoModule
import org.jacodb.ets.dto.toEtsFile
import org.jacodb.ets.model.EtsAssignStmt
import org.jacodb.ets.model.EtsNewClassExpr
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


class EtsTsClassExpressionTest {
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

}
