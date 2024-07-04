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

package ets

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jacodb.panda.dynamic.ets.base.EtsAnyType
import org.jacodb.panda.dynamic.ets.base.EtsLocal
import org.jacodb.panda.dynamic.ets.dto.AnyTypeDto
import org.jacodb.panda.dynamic.ets.dto.ClassSignatureDto
import org.jacodb.panda.dynamic.ets.dto.ConstantDto
import org.jacodb.panda.dynamic.ets.dto.EtsFileDto
import org.jacodb.panda.dynamic.ets.dto.EtsMethodBuilder
import org.jacodb.panda.dynamic.ets.dto.FieldDto
import org.jacodb.panda.dynamic.ets.dto.FieldSignatureDto
import org.jacodb.panda.dynamic.ets.dto.LocalDto
import org.jacodb.panda.dynamic.ets.dto.MethodDto
import org.jacodb.panda.dynamic.ets.dto.ModifierDto
import org.jacodb.panda.dynamic.ets.dto.NumberTypeDto
import org.jacodb.panda.dynamic.ets.dto.StmtDto
import org.jacodb.panda.dynamic.ets.dto.convertToEtsFile
import org.jacodb.panda.dynamic.ets.model.EtsClassSignature
import org.jacodb.panda.dynamic.ets.model.EtsMethodSignature
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class EtsFromJsonTest {
    private val json = Json {
        // classDiscriminator = "_"
        prettyPrint = true
    }

    private val defaultSignature = EtsMethodSignature(
        enclosingClass = EtsClassSignature(name = "_DEFAULT_ARK_CLASS"),
        name = "_DEFAULT_ARK_METHOD",
        parameters = emptyList(),
        returnType = EtsAnyType,
    )

    @Test
    fun testLoadEtsFileFromJson() {
        val path = "ir/basic.json"
        val stream = object {}::class.java.getResourceAsStream("/$path")
            ?: error("Resource not found: $path")
        val etsDto = EtsFileDto.loadFromJson(stream)
        println("etsDto = $etsDto")
        val ets = convertToEtsFile(etsDto)
        println("ets = $ets")
    }

    @Test
    fun testLoadValueFromJson() {
        val jsonString = """
            {
              "name": "x",
              "type": {
                "_": "AnyType"
              }
            }
        """.trimIndent()
        val valueDto = Json.decodeFromString<LocalDto>(jsonString)
        println("valueDto = $valueDto")
        Assertions.assertEquals(LocalDto("x", AnyTypeDto), valueDto)
        val ctx = EtsMethodBuilder(defaultSignature)
        val value = ctx.convertToEtsEntity(valueDto)
        println("value = $value")
        Assertions.assertEquals(EtsLocal("x", EtsAnyType), value)
    }

    @Test
    fun testLoadFieldFromJson() {
        val field = FieldDto(
            signature = FieldSignatureDto(
                enclosingClass = ClassSignatureDto(
                    name = "Test"
                ),
                name = "x",
                fieldType = NumberTypeDto,
            ),
            modifiers = emptyList(),
            typeParameters = emptyList(),
            isOptional = true,
            isDefinitelyAssigned = false,
            initializer = ConstantDto("0", NumberTypeDto),
        )
        println("field = $field")

        val jsonString = json.encodeToString(field)
        println("json: $jsonString")

        val fieldDto = json.decodeFromString<FieldDto>(jsonString)
        println("fieldDto = $fieldDto")
        Assertions.assertEquals(field, fieldDto)
    }

    @Test
    fun testLoadReturnVoidStmtFromJson() {
        val jsonString = """
            {
              "_": "ReturnVoidStmt"
            }
        """.trimIndent()
        val stmtDto = Json.decodeFromString<StmtDto>(jsonString)
        println("stmtDto = $stmtDto")
        val ctx = EtsMethodBuilder(defaultSignature)
        val stmt = ctx.convertToEtsStmt(stmtDto)
        println("stmt = $stmt")
    }

    @Test
    fun testLoadMethodFromJson() {
        val jsonString = """
             {
               "signature": {
                 "enclosingClass": {
                   "name": "_DEFAULT_ARK_CLASS"
                 },
                 "name": "_DEFAULT_ARK_METHOD",
                 "parameters": [],
                 "returnType": {
                    "_": "UnknownType"
                  }
               },
               "modifiers": [],
               "typeParameters": [],
               "body": {
                 "locals": [],
                 "cfg": {
                   "blocks": [
                     {
                       "id": 0,
                       "successors": [],
                       "predecessors": [],
                       "stmts": [
                         {
                           "_": "ReturnVoidStmt"
                         }
                       ]
                     }
                   ]
                 }
               }
             }
        """.trimIndent()
        val methodDto = Json.decodeFromString<MethodDto>(jsonString)
        println("methodDto = $methodDto")
    }

    @Test
    fun testLoadModifierFromJson() {
        val jsonString = """
            {
              "kind": "cat",
              "content": "Brian"
            }
        """.trimIndent()
        val modifierDto = Json.decodeFromString<ModifierDto>(jsonString)
        println("modifierDto = $modifierDto")
        Assertions.assertEquals(ModifierDto.DecoratorItem("cat", "Brian"), modifierDto)
        val jsonString2 = json.encodeToString(modifierDto)
        println("json: $jsonString2")
    }

    @Test
    fun testLoadListOfModifiersFromJson() {
        val jsonString = """
            [
              {
                "kind": "cat",
                "content": "Bruce"
              },
              "public",
              "static"
            ]
        """.trimIndent()
        val modifiers = Json.decodeFromString<List<ModifierDto>>(jsonString)
        println("modifiers = $modifiers")
        Assertions.assertEquals(
            listOf(
                ModifierDto.DecoratorItem("cat", "Bruce"),
                ModifierDto.StringItem("public"),
                ModifierDto.StringItem("static"),
            ),
            modifiers
        )
    }
}
