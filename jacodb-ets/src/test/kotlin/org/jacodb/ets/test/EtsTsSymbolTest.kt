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

import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jacodb.ets.dto.EtsFileDto
import org.jacodb.ets.dto.dtoModule
import org.jacodb.ets.dto.toEtsFile
import org.jacodb.ets.utils.DEFAULT_ARK_CLASS_NAME
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.generateEtsIR
import org.junit.jupiter.api.Test
import org.jacodb.ets.dto.SymbolTypeDto
import org.jacodb.ets.model.EtsSymbolType

class EtsTsSymbolTest {
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
        val frontendDto = runFrontend(source = "export function same(value: symbol): symbol { return value; }")
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

}
