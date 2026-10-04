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
import org.jacodb.ets.dto.EtsFileDto
import org.jacodb.ets.dto.dtoModule
import org.jacodb.ets.dto.toEtsFile
import org.jacodb.ets.model.EtsAssignStmt
import org.jacodb.ets.model.EtsIfStmt
import org.jacodb.ets.model.EtsNewArrayExpr
import org.jacodb.ets.model.EtsPtrCallExpr
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.generateEtsIR
import org.junit.jupiter.api.Test
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.assertTrue

class EtsTsArrayParameterTest {
    private fun roundTrip(source: String): EtsFileDto {
        val input = createTempDirectory("ts-array-parameters").resolve("test.ts")
        input.writeText(source)
        val output = generateEtsIR(input, isProject = false, timeout = null, provider = EtsIrProvider.TS_FRONTEND)
        val dto = EtsFileDto.loadFromJson(output.readText())

        return EtsFileDto.loadFromJson(Json { serializersModule = dtoModule }.encodeToString(dto))
    }

    @Test
    fun `dynamic array spread survives frontend JSON and model conversion`() {
        val dto = roundTrip("export function copy(input: number[]): number[] { return [0, ...input, 9]; }")

        val method = dto.toEtsFile().classes.flatMap { it.methods }.single { it.name == "copy" }
        val values = method.cfg.stmts.filterIsInstance<EtsAssignStmt>().map { it.rhv }

        assertTrue(values.any { it is EtsNewArrayExpr })
        assertTrue(values.filterIsInstance<EtsPtrCallExpr>().any { it.callee.name == "next" && it.receiver != null })
    }

    @Test
    fun `array rest survives frontend JSON and model conversion`() {
        val dto = roundTrip("export function tail(input: number[]): number[] { const [first, ...rest] = input; return rest; }")

        val method = dto.toEtsFile().classes.flatMap { it.methods }.single { it.name == "tail" }
        val values = method.cfg.stmts.filterIsInstance<EtsAssignStmt>().map { it.rhv }

        assertTrue(values.any { it is EtsNewArrayExpr })
        assertTrue(values.filterIsInstance<EtsPtrCallExpr>().any { it.callee.name == "next" && it.receiver != null })
    }

    @Test
    fun `parameter defaults survive frontend JSON and model conversion`() {
        val dto = roundTrip("export function value(a = 3, b = a + 1): number { return b; }")

        val method = dto.toEtsFile().classes.flatMap { it.methods }.single { it.name == "value" }

        assertTrue(method.cfg.stmts.any { it is EtsIfStmt })
    }

    @Test
    fun `rest parameter metadata survives frontend JSON and model conversion`() {
        val dto = roundTrip("export function count(...values: number[]): number { return values.length; }")

        val dtoMethod = dto.classes.flatMap { it.methods }.single { it.signature.name == "count" }
        val method = dto.toEtsFile().classes.flatMap { it.methods }.single { it.name == "count" }

        assertTrue(dtoMethod.signature.parameters.single().isRest)
        assertTrue(method.signature.parameters.single().isRest)
    }
}
