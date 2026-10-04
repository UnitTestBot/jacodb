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
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsAssignStmt
import org.jacodb.ets.model.EtsLocal
import org.jacodb.ets.model.EtsFunctionType
import org.jacodb.ets.model.EtsClosureFieldRef
import org.jacodb.ets.model.EtsThis

class EtsTsLexicalThisTest {
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
    fun `stored arrow lexical receiver survives JSON and model conversion`() {
        val frontendDto = runFrontend(
            source = """
                class Box {
                    offset = 11;
                    installOn(target: Box) {
                        target.callback = (value: number) => this.offset + value;
                    }
                    installOrdinaryOn(target: Box) {
                        target.callback = function(value: number) { return this.offset + value; };
                    }
                }
            """.trimIndent(),
        )
        val roundTripped = EtsFileDto.loadFromJson(
            Json { serializersModule = dtoModule }.encodeToString(frontendDto),
        )
        val box = roundTripped.toEtsFile().classes.single { it.name == "Box" }
        val install = box.methods.single { it.name == "installOn" }
        val arrowValue = install.cfg.stmts.filterIsInstance<EtsAssignStmt>()
            .map { it.rhv }
            .filterIsInstance<EtsLocal>()
            .single { it.name.startsWith("%AM") }
        val arrow = box.methods.single { it.name == arrowValue.name }
        val ordinary = box.methods.single { it.name.endsWith("\$installOrdinaryOn") }

        assertTrue((arrowValue.type as EtsFunctionType).isArrow)
        val receiver = arrow.cfg.stmts.filterIsInstance<EtsAssignStmt>()
            .single { (it.lhv as? EtsLocal)?.name == "this" }.rhv
        assertTrue(receiver is EtsClosureFieldRef)
        assertEquals("this", receiver.fieldName)
        assertTrue(ordinary.cfg.stmts.filterIsInstance<EtsAssignStmt>()
            .single { (it.lhv as? EtsLocal)?.name == "this" }.rhv is org.jacodb.ets.model.EtsThis)
    }

}
