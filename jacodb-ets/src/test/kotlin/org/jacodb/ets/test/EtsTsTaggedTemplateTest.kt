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
import org.jacodb.ets.dto.CallExprDto
import org.jacodb.ets.dto.EtsFileDto
import org.jacodb.ets.dto.PtrCallExprDto
import org.jacodb.ets.dto.RawValueDto
import org.jacodb.ets.dto.TemplateObjectExprDto
import org.jacodb.ets.dto.dtoModule
import org.jacodb.ets.dto.toEtsFile
import org.jacodb.ets.model.EtsAssignStmt
import org.jacodb.ets.model.EtsPtrCallExpr
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


class EtsTsTaggedTemplateTest {
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
