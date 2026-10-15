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

import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jacodb.ets.dto.ClassDto
import org.jacodb.ets.dto.ClassSignatureDto
import org.jacodb.ets.dto.EtsFileDto
import org.jacodb.ets.dto.FieldDto
import org.jacodb.ets.dto.FieldSignatureDto
import org.jacodb.ets.dto.FileSignatureDto
import org.jacodb.ets.dto.FunctionTypeDto
import org.jacodb.ets.dto.LexicalEnvTypeDto
import org.jacodb.ets.dto.LexicalEnvTypeRefDto
import org.jacodb.ets.dto.LocalDto
import org.jacodb.ets.dto.MethodParameterDto
import org.jacodb.ets.dto.MethodSignatureDto
import org.jacodb.ets.dto.NumberTypeDto
import org.jacodb.ets.dto.StringTypeDto
import org.jacodb.ets.dto.TypeDto
import org.jacodb.ets.dto.VoidTypeDto
import org.jacodb.ets.dto.dtoModule
import org.jacodb.ets.dto.toEtsFile
import org.jacodb.ets.dto.toEtsType
import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.model.EtsFunctionType
import org.jacodb.ets.model.EtsLexicalEnvType
import org.jacodb.ets.model.EtsNumberType
import org.jacodb.ets.model.EtsStringType
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class EtsLexicalEnvTypeReferencesTest {
    private val json = Json { serializersModule = dtoModule }
    private val fileSignature = FileSignatureDto(projectName = "test", fileName = "closures.ts")
    private val classSignature = ClassSignatureDto(name = "Entry", declaringFile = fileSignature)

    @Test
    fun `legacy inline lexical environments need no table`() {
        val environment = environment(name = "legacy", captures = listOf(local(name = "value", type = NumberTypeDto)))
        val serialized = json.encodeToString(file(types = listOf(environment)))

        val decoded = EtsFileDto.loadFromJson(serialized)
        val converted = decoded.toEtsFile()
        val type = assertIs<EtsLexicalEnvType>(converted.classes.single().fields.single().type)

        assertTrue(decoded.lexicalEnvTypes.isEmpty())
        assertTrue("lexicalEnvTypes" !in serialized)
        assertEquals(listOf("value"), type.closures.map { it.name })
        assertSame(EtsNumberType, type.closures.single().type)
    }

    @Test
    fun `references preserve captures and nested function signatures with shared model identity`() {
        val parent = environment(name = "parent", captures = listOf(local(name = "seed", type = NumberTypeDto)))
        val callback = FunctionTypeDto(
            signature = method(
                name = "callback",
                parameters = listOf(MethodParameterDto(name = "environment", type = LexicalEnvTypeRefDto(id = 0))),
                returnType = StringTypeDto,
            ),
            isArrow = true,
        )
        val child = environment(
            name = "child",
            captures = listOf(
                local(name = "parent", type = LexicalEnvTypeRefDto(id = 0)),
                local(name = "callback", type = callback),
                local(name = "message", type = StringTypeDto),
            ),
        )
        val consumer = FunctionTypeDto(
            signature = method(
                name = "consumer",
                parameters = listOf(MethodParameterDto(name = "environment", type = LexicalEnvTypeRefDto(id = 1))),
                returnType = LexicalEnvTypeRefDto(id = 1),
            ),
        )
        val dto = file(
            environments = listOf(parent, child),
            types = listOf(LexicalEnvTypeRefDto(id = 1), LexicalEnvTypeRefDto(id = 1), consumer),
        )

        val converted = readModel(dto = dto)
        val fields = converted.classes.single().fields
        val first = assertIs<EtsLexicalEnvType>(fields[0].type)
        val function = assertIs<EtsFunctionType>(fields[2].type)
        val capturedParent = assertIs<EtsLexicalEnvType>(first.closures[0].type)
        val capturedCallback = assertIs<EtsFunctionType>(first.closures[1].type)

        assertSame(first, fields[1].type)
        assertSame(first, function.signature.parameters.single().type)
        assertSame(first, function.signature.returnType)
        assertEquals(listOf("parent", "callback", "message"), first.closures.map { it.name })
        assertSame(capturedParent, capturedCallback.signature.parameters.single().type)
        assertSame(EtsNumberType, capturedParent.closures.single().type)
        assertSame(EtsStringType, capturedCallback.signature.returnType)
        assertSame(EtsStringType, first.closures[2].type)
        assertTrue(capturedCallback.isArrow)
    }

    @Test
    fun `a branching environment DAG remains shared throughout model conversion`() {
        val environments = mutableListOf(environment(name = "base", captures = emptyList()))
        repeat(times = 16) { index ->
            environments += environment(
                name = "level${index + 1}",
                captures = listOf(
                    local(name = "left", type = LexicalEnvTypeRefDto(id = index)),
                    local(name = "right", type = LexicalEnvTypeRefDto(id = index)),
                ),
            )
        }

        val converted = readModel(dto = file(environments = environments, types = listOf(LexicalEnvTypeRefDto(id = 16))))
        var current = assertIs<EtsLexicalEnvType>(converted.classes.single().fields.single().type)

        repeat(times = 16) {
            assertEquals(listOf("left", "right"), current.closures.map { it.name })
            assertSame(current.closures[0].type, current.closures[1].type)
            current = assertIs<EtsLexicalEnvType>(current.closures[0].type)
        }
        assertTrue(current.closures.isEmpty())
    }

    @Test
    fun `shared legacy inline environment DTOs stay shared without a table`() {
        var inlineEnvironment = environment(name = "base", captures = emptyList())
        repeat(times = 16) { index ->
            inlineEnvironment = environment(
                name = "level${index + 1}",
                captures = listOf(
                    local(name = "left", type = inlineEnvironment),
                    local(name = "right", type = inlineEnvironment),
                ),
            )
        }

        val converted = file(types = listOf(inlineEnvironment, inlineEnvironment)).toEtsFile()
        val fields = converted.classes.single().fields
        var current = assertIs<EtsLexicalEnvType>(fields[0].type)

        assertSame(current, fields[1].type)
        repeat(times = 16) {
            assertSame(current.closures[0].type, current.closures[1].type)
            current = assertIs<EtsLexicalEnvType>(current.closures[0].type)
        }
        assertTrue(current.closures.isEmpty())
    }

    @Test
    fun `out of range and missing table references fail explicitly`() {
        val environments = listOf(environment(name = "valid", captures = emptyList()))
        for (id in listOf(-1, 1, Int.MAX_VALUE)) {
            val dto = file(environments = environments, types = listOf(LexicalEnvTypeRefDto(id = id)))

            val failure = assertFailsWith<SerializationException> { readModel(dto = dto) }

            assertTrue(failure.message.orEmpty().contains("outside table"))
        }

        val missingTable = file(types = listOf(LexicalEnvTypeRefDto(id = 0)))
        val decoded = EtsFileDto.loadFromJson(json.encodeToString(missingTable))

        assertIs<LexicalEnvTypeRefDto>(decoded.classes.single().fields.single().signature.type)
        assertFailsWith<SerializationException> { decoded.toEtsFile() }
        assertFailsWith<SerializationException> { LexicalEnvTypeRefDto(id = 0).toEtsType() }
    }

    @Test
    fun `unused definitions with invalid references are rejected`() {
        val invalid = environment(name = "invalid", captures = listOf(local(name = "value", type = LexicalEnvTypeRefDto(id = 1))))

        val failure = assertFailsWith<SerializationException> {
            readModel(dto = file(environments = listOf(invalid), types = emptyList()))
        }

        assertTrue(failure.message.orEmpty().contains("outside table"))
    }

    @Test
    fun `direct and nested function reference cycles are rejected`() {
        val direct = environment(name = "direct", captures = listOf(local(name = "self", type = LexicalEnvTypeRefDto(id = 0))))
        val nestedFunction = FunctionTypeDto(
            signature = method(name = "nested", parameters = emptyList(), returnType = LexicalEnvTypeRefDto(id = 0)),
        )
        val first = environment(name = "first", captures = listOf(local(name = "next", type = LexicalEnvTypeRefDto(id = 1))))
        val second = environment(name = "second", captures = listOf(local(name = "function", type = nestedFunction)))

        for (environments in listOf(listOf(direct), listOf(first, second))) {
            val failure = assertFailsWith<SerializationException> {
                readModel(dto = file(environments = environments, types = emptyList()))
            }

            assertTrue(failure.message.orEmpty().contains("Cyclic LexicalEnvTypeRef"))
        }
    }

    @Test
    fun `file scopes stay independent and failed conversions leave no reference context`() {
        val invalid = file(types = listOf(LexicalEnvTypeRefDto(id = 0)))
        val first = file(
            environments = listOf(environment(name = "first", captures = listOf(local(name = "number", type = NumberTypeDto)))),
            types = listOf(LexicalEnvTypeRefDto(id = 0)),
        )
        val second = file(
            environments = listOf(environment(name = "second", captures = listOf(local(name = "string", type = StringTypeDto)))),
            types = listOf(LexicalEnvTypeRefDto(id = 0)),
        )

        assertFailsWith<SerializationException> { readModel(dto = invalid) }
        val firstModel = readModel(dto = first)
        val secondModel = EtsFileDto.loadFromJson(json.encodeToString(second).byteInputStream()).toEtsFile()
        val firstType = assertIs<EtsLexicalEnvType>(firstModel.classes.single().fields.single().type)
        val secondType = assertIs<EtsLexicalEnvType>(secondModel.classes.single().fields.single().type)

        assertNotSame(firstType, secondType)
        assertSame(EtsNumberType, firstType.closures.single().type)
        assertSame(EtsStringType, secondType.closures.single().type)
        assertFailsWith<SerializationException> { LexicalEnvTypeRefDto(id = 0).toEtsType() }
    }

    private fun readModel(dto: EtsFileDto): EtsFile = EtsFileDto.loadFromJson(json.encodeToString(dto)).toEtsFile()

    private fun environment(name: String, captures: List<LocalDto>): LexicalEnvTypeDto = LexicalEnvTypeDto(
        method = method(name = name, parameters = emptyList(), returnType = VoidTypeDto),
        closures = captures,
    )

    private fun local(name: String, type: TypeDto): LocalDto = LocalDto(name = name, type = type)

    private fun method(name: String, parameters: List<MethodParameterDto>, returnType: TypeDto): MethodSignatureDto = MethodSignatureDto(
        declaringClass = classSignature,
        name = name,
        parameters = parameters,
        returnType = returnType,
    )

    private fun file(environments: List<LexicalEnvTypeDto> = emptyList(), types: List<TypeDto>): EtsFileDto = EtsFileDto(
        signature = fileSignature,
        namespaces = emptyList(),
        classes = listOf(
            ClassDto(
                signature = classSignature,
                modifiers = 0,
                decorators = emptyList(),
                superClassName = null,
                implementedInterfaceNames = emptyList(),
                fields = types.mapIndexed { index, type ->
                    FieldDto(
                        signature = FieldSignatureDto(declaringClass = classSignature, name = "field$index", type = type),
                        modifiers = 0,
                        decorators = emptyList(),
                        isOptional = false,
                        isDefinitelyAssigned = false,
                    )
                },
                methods = emptyList(),
            ),
        ),
        importInfos = emptyList(),
        exportInfos = emptyList(),
        lexicalEnvTypes = environments,
    )
}
