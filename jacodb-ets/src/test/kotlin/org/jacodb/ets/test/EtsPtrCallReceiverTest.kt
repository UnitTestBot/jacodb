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
import org.jacodb.ets.model.EtsEntity
import org.jacodb.ets.model.EtsLocal
import org.jacodb.ets.model.EtsPtrCallExpr
import org.jacodb.ets.model.EtsStmt
import org.jacodb.ets.utils.AbstractHandler
import org.jacodb.ets.utils.getOperands
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EtsPtrCallReceiverTest {
    private fun call(includeReceiver: Boolean): EtsPtrCallExpr {
        val fileSignature = """{"projectName":"test","fileName":"receiver.ts"}"""
        val classSignature = """{"name":"Container","declaringFile":$fileSignature}"""
        val receiver = if (includeReceiver) """, "receiver":{"_":"Local","name":"object","type":{"_":"UnknownType"}}""" else ""
        val source = """
            {
              "signature":$fileSignature,"namespaces":[],"importInfos":[],"exportInfos":[],
              "classes":[{
                "signature":$classSignature,"modifiers":0,"decorators":[],
                "superClassName":"","implementedInterfaceNames":[],"fields":[],
                "methods":[{
                  "signature":{"name":"run","declaringClass":$classSignature,"parameters":[],"returnType":{"_":"UnknownType"}},
                  "modifiers":0,"decorators":[],
                  "body":{"locals":[],"cfg":{"blocks":[{"id":0,"successors":[],"stmts":[{
                    "_":"AssignStmt","left":{"_":"Local","name":"result","type":{"_":"UnknownType"}},
                    "right":{"_":"PtrCallExpr","ptr":{"_":"Local","name":"fn","type":{"_":"UnknownType"}},
                      "method":{"name":"invoke","declaringClass":$classSignature,"parameters":[],"returnType":{"_":"UnknownType"}},
                      "args":[]$receiver}
                  },{"_":"ReturnVoidStmt"}]}]}}
                }]
              }]
            }
        """.trimIndent()
        val dto = EtsFileDto.loadFromJson(source)
        val roundTripped = EtsFileDto.loadFromJson(Json { serializersModule = dtoModule }.encodeToString(dto))

        return roundTripped.toEtsFile().classes.single().methods.single().cfg.stmts
            .filterIsInstance<EtsAssignStmt>().single().rhv as EtsPtrCallExpr
    }

    @Test
    fun `receiver survives JSON conversion and is traversed once`() {
        val call = call(includeReceiver = true)
        val visited = mutableListOf<String>()
        val handler = object : AbstractHandler() {
            override fun handle(value: EtsEntity) {
                if (value is EtsLocal) visited += value.name
            }

            override fun handle(stmt: EtsStmt) = Unit
        }

        call.accept(handler)

        assertEquals("object", call.receiver?.name)
        assertEquals(listOf("fn", "object"), call.getOperands().filterIsInstance<EtsLocal>().map { it.name }.toList())
        assertEquals(listOf("fn", "object"), visited)
    }

    @Test
    fun `old payload without receiver remains readable`() {
        val call = call(includeReceiver = false)

        assertNull(call.receiver)
        assertEquals(listOf("fn"), call.getOperands().filterIsInstance<EtsLocal>().map { it.name }.toList())
    }
}
