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
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.jacodb.ets.dto.AssignStmtDto
import org.jacodb.ets.dto.EtsFileDto
import org.jacodb.ets.dto.RawStmtDto
import org.jacodb.ets.dto.RawValueDto
import org.jacodb.ets.dto.toEtsFile
import org.jacodb.ets.model.EtsRawStmt
import org.jacodb.ets.utils.DEFAULT_ARK_CLASS_NAME
import org.jacodb.ets.utils.DEFAULT_ARK_METHOD_NAME
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.generateEtsIR
import org.junit.jupiter.api.Test

class EtsTsPartialClassTest {
    private fun frontend(source: String): EtsFileDto {
        val input = createTempDirectory("ts-partial-class").resolve("test.ts")
        input.writeText(source)

        val output = generateEtsIR(
            input,
            isProject = false,
            timeout = null,
            provider = EtsIrProvider.TS_FRONTEND,
        )
        return EtsFileDto.loadFromJson(output.readText())
    }

    @Test
    fun `ordinary bodies survive native frontend and model conversion with unsupported lifecycle`() {
        val dto = frontend(
            """
                class Base { [Symbol.iterator]() { return [][Symbol.iterator](); } }
                export class Counter extends Base {
                    own = 7;
                    increment(value: number) { return value + this.own; }
                }
                export class Registry extends Map<string, number> {
                    positive(value: number) { return value > 0; }
                }
            """.trimIndent(),
        )

        val model = dto.toEtsFile()
        for (name in listOf("Counter", "Registry")) {
            val clazz = model.classes.single { it.name == name }
            for (methodName in listOf("constructor", "%instInit", "%statInit")) {
                val lifecycle = clazz.methods.single { it.name == methodName }
                assertTrue(lifecycle.cfg.stmts.any { it is EtsRawStmt })
            }
        }
        val counter = dto.classes.single { it.signature.name == "Counter" }
        val body = counter.methods.single { it.signature.name == "increment" }
            .body!!.cfg.blocks.flatMap { it.stmts }
        assertTrue(body.isNotEmpty())
        assertFalse(body.filterIsInstance<AssignStmtDto>().any { it.right is RawValueDto })
        assertEquals("Base", counter.superClassName)
        assertTrue(dto.exportInfos.none { it.exportName == "Counter" || it.exportName == "Registry" })

        val module = dto.classes.single { it.signature.name == DEFAULT_ARK_CLASS_NAME }
        val statements = module.methods.single { it.signature.name == DEFAULT_ARK_METHOD_NAME }
            .body!!.cfg.blocks.flatMap { it.stmts }
        assertEquals(3, statements.filterIsInstance<RawStmtDto>().count { it.kind == "UnsupportedStmt" })
    }

    @Test
    fun `native frontend retains explicit barriers for unresolved base operations`() {
        val dto = frontend(
            """
                export class Element extends HTMLElement {
                    own = 1;
                    independent(value: number) { return value + this.own; }
                    dependent() { return this.remove(); }
                }
            """.trimIndent(),
        )
        val clazz = dto.classes.single { it.signature.name == "Element" }

        val independent = clazz.methods.single { it.signature.name == "independent" }
            .body!!.cfg.blocks.flatMap { it.stmts }.filterIsInstance<AssignStmtDto>()
        val dependent = clazz.methods.single { it.signature.name == "dependent" }
            .body!!.cfg.blocks.flatMap { it.stmts }.filterIsInstance<AssignStmtDto>()
        assertFalse(independent.any { it.right is RawValueDto })
        assertTrue(dependent.any { it.right is RawValueDto })
        assertEquals("HTMLElement", dto.toEtsFile().classes.single { it.name == "Element" }.superClass?.name)
    }
}
