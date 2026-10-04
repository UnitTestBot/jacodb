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
import java.math.BigInteger
import org.jacodb.ets.dto.AssignStmtDto
import org.jacodb.ets.dto.BigIntTypeDto
import org.jacodb.ets.dto.ConstantDto
import org.jacodb.ets.model.EtsAssignStmt
import org.jacodb.ets.model.EtsBigIntConstant
import org.jacodb.ets.model.EtsBigIntType

class EtsTsBigIntTest {
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
    fun `bigint types and exact constants survive frontend JSON and model conversion`() {
        val frontendDto = runFrontend(
            source =
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

}
