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
import org.jacodb.ets.dto.AssignStmtDto
import org.jacodb.ets.dto.PtrCallExprDto
import org.jacodb.ets.dto.AwaitExprDto
import org.jacodb.ets.dto.RawValueDto
import org.jacodb.ets.model.EtsAssignStmt
import org.jacodb.ets.model.EtsAwaitExpr

class EtsTsForAwaitTest {
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
    fun `for await preserves async acquisition and sync value unwrapping`() {
        val frontendDto = runFrontend(
            source = """
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
            val awaits = assignments.mapNotNull { it.right as? AwaitExprDto }
            val next = assignments.single { (it.right as? PtrCallExprDto)?.method?.name == "next" }

            assertTrue(calls.any { it.method.name == "Symbol.asyncIterator" && it.receiver != null })
            assertTrue(calls.any { it.method.name == "Symbol.iterator" && it.receiver != null })
            assertEquals(2, awaits.size)
            assertTrue(awaits.any { it.arg == next.left })
            assertTrue(assignments.none { it.right is RawValueDto })

            val modelMethod = roundTripped.toEtsFile().classes.single { it.name == DEFAULT_ARK_CLASS_NAME }
                .methods.single { it.name == name }
            assertEquals(2, modelMethod.cfg.stmts.filterIsInstance<EtsAssignStmt>()
                .count { it.rhv is EtsAwaitExpr })
        }
    }

}
