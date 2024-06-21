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

package ark

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jacodb.panda.dynamic.ark.base.ArkAnyType
import org.jacodb.panda.dynamic.ark.base.ArkLocal
import org.jacodb.panda.dynamic.ark.dto.ArkFileDto
import org.jacodb.panda.dynamic.ark.dto.ArkMethodBuilder
import org.jacodb.panda.dynamic.ark.dto.ClassSignatureDto
import org.jacodb.panda.dynamic.ark.dto.ConstantDto
import org.jacodb.panda.dynamic.ark.dto.FieldDto
import org.jacodb.panda.dynamic.ark.dto.FieldSignatureDto
import org.jacodb.panda.dynamic.ark.dto.LocalDto
import org.jacodb.panda.dynamic.ark.dto.MethodDto
import org.jacodb.panda.dynamic.ark.dto.StmtDto
import org.jacodb.panda.dynamic.ark.dto.convertToArkFile
import org.jacodb.panda.dynamic.ark.model.ArkClassSignature
import org.jacodb.panda.dynamic.ark.model.ArkMethodSignature
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class ArkFromJsonTest {
    private val json = Json {
        // classDiscriminator = "_"
        prettyPrint = true
    }

    private val defaultSignature = ArkMethodSignature(
        enclosingClass = ArkClassSignature(name = "_DEFAULT_ARK_CLASS"),
        name = "_DEFAULT_ARK_METHOD",
        parameters = emptyList(),
        returnType = ArkAnyType,
    )

    @Test
    fun testLoadArkFileFromJson() {
        val path = "arkir/basic.ts.json"
        val stream = object {}::class.java.getResourceAsStream("/$path")
            ?: error("Resource not found: $path")
        val arkDto = ArkFileDto.loadFromJson(stream)
        println("arkDto = $arkDto")
        val ark = convertToArkFile(arkDto)
        println("ark = $ark")
    }

    @Test
    fun testLoadValueFromJson() {
        val jsonString = """
            {
              "name": "x",
              "type": "any"
            }
        """.trimIndent()
        val valueDto = Json.decodeFromString<LocalDto>(jsonString)
        println("valueDto = $valueDto")
        Assertions.assertEquals(LocalDto("x", "any"), valueDto)
        val ctx = ArkMethodBuilder(defaultSignature)
        val value = ctx.convertToArkEntity(valueDto)
        println("value = $value")
        Assertions.assertEquals(ArkLocal("x", ArkAnyType), value)
    }

    @Test
    fun testLoadFieldFromJson() {
        val field = FieldDto(
            signature = FieldSignatureDto(
                enclosingClass = ClassSignatureDto(
                    name = "Test"
                ),
                name = "x",
                fieldType = "number",
            ),
            modifiers = emptyList(),
            typeParameters = emptyList(),
            isOptional = true,
            isDefinitelyAssigned = false,
            initializer = ConstantDto("0", "number"),
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
        val ctx = ArkMethodBuilder(defaultSignature)
        val stmt = ctx.convertToArkStmt(stmtDto)
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
                 "returnType": "unknown"
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
}
