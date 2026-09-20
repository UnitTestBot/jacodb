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
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.jacodb.ets.dto.BasicBlockDto
import org.jacodb.ets.dto.BodyDto
import org.jacodb.ets.dto.BooleanTypeDto
import org.jacodb.ets.dto.BuiltinCallProofDto
import org.jacodb.ets.dto.BuiltinEntryRequirementDto
import org.jacodb.ets.dto.CallStmtDto
import org.jacodb.ets.dto.CfgDto
import org.jacodb.ets.dto.ClassSignatureDto
import org.jacodb.ets.dto.FileSignatureDto
import org.jacodb.ets.dto.LocalDto
import org.jacodb.ets.dto.MethodDto
import org.jacodb.ets.dto.MethodParameterDto
import org.jacodb.ets.dto.MethodSignatureDto
import org.jacodb.ets.dto.NumberTypeDto
import org.jacodb.ets.dto.ProvenBuiltinDto
import org.jacodb.ets.dto.StaticCallExprDto
import org.jacodb.ets.dto.ValueDto
import org.jacodb.ets.dto.dtoModule
import org.jacodb.ets.dto.toEtsMethod
import org.jacodb.ets.model.EtsBuiltin
import org.jacodb.ets.model.EtsBuiltinEntryRequirement
import org.jacodb.ets.model.EtsCallStmt
import org.jacodb.ets.model.EtsStaticCallExpr
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

class EtsBuiltinCallProofTest {
    private val json = Json { serializersModule = dtoModule }

    @Test
    fun `preserves builtin proof from DTO to model`() {
        val file = FileSignatureDto(projectName = "project", fileName = "entry.ts")
        val owner = ClassSignatureDto(name = "%dflt", declaringFile = file)
        val entry = MethodSignatureDto(
            declaringClass = owner,
            name = "%AM0\$%dflt",
            parameters = listOf(MethodParameterDto(name = "value", type = NumberTypeDto)),
            returnType = BooleanTypeDto,
        )
        data class BuiltinCase(
            val dtoBuiltin: ProvenBuiltinDto,
            val expectedBuiltin: EtsBuiltin,
            val ownerName: String,
            val methodName: String,
        )

        val argument = LocalDto(name = "value", type = NumberTypeDto)
        val builtins = listOf(
            BuiltinCase(
                dtoBuiltin = ProvenBuiltinDto.NUMBER_IS_INTEGER,
                expectedBuiltin = EtsBuiltin.NUMBER_IS_INTEGER,
                ownerName = "Number",
                methodName = "isInteger",
            ),
            BuiltinCase(
                dtoBuiltin = ProvenBuiltinDto.MATH_ABS,
                expectedBuiltin = EtsBuiltin.MATH_ABS,
                ownerName = "Math",
                methodName = "abs",
            ),
            BuiltinCase(
                dtoBuiltin = ProvenBuiltinDto.MATH_MIN,
                expectedBuiltin = EtsBuiltin.MATH_MIN,
                ownerName = "Math",
                methodName = "min",
            ),
            BuiltinCase(
                dtoBuiltin = ProvenBuiltinDto.MATH_MAX,
                expectedBuiltin = EtsBuiltin.MATH_MAX,
                ownerName = "Math",
                methodName = "max",
            ),
        )

        for (builtinCase in builtins) {
            val builtin = MethodSignatureDto(
                declaringClass = ClassSignatureDto(
                    name = builtinCase.ownerName,
                    declaringFile = FileSignatureDto(projectName = "%unk", fileName = "%unk"),
                ),
                name = builtinCase.methodName,
                parameters = emptyList(),
                returnType = BooleanTypeDto,
            )
            val call = StaticCallExprDto(
                method = builtin,
                args = listOf(argument),
                builtinProof = BuiltinCallProofDto(
                    builtin = builtinCase.dtoBuiltin,
                    entryRequirement = BuiltinEntryRequirementDto.DIRECT_ISOLATED_ENTRY,
                    entryMethod = entry,
                ),
            )
            val method = MethodDto(
                signature = entry,
                modifiers = 0,
                decorators = emptyList(),
                body = BodyDto(
                    locals = listOf(argument),
                    cfg = CfgDto(
                        blocks = listOf(
                            BasicBlockDto(
                                id = 0,
                                successors = emptyList(),
                                stmts = listOf(CallStmtDto(expr = call)),
                            ),
                        ),
                    ),
                ),
            ).toEtsMethod()

            val modelCall = assertIs<EtsStaticCallExpr>(assertIs<EtsCallStmt>(method.cfg.stmts.single()).expr)
            assertEquals(builtinCase.ownerName, modelCall.callee.enclosingClass.name)
            assertEquals(builtinCase.expectedBuiltin, modelCall.builtinProof?.builtin)
            assertEquals(EtsBuiltinEntryRequirement.DIRECT_ISOLATED_ENTRY, modelCall.builtinProof?.entryRequirement)
            assertEquals(method.signature, modelCall.builtinProof?.entryMethod)
        }
    }

    @Test
    fun `defaults missing proof to absent`() {
        val dto = json.decodeFromString<ValueDto>(
            """
                {
                  "_": "StaticCallExpr",
                  "method": {
                    "declaringClass": {
                      "name": "Number",
                      "declaringFile": { "projectName": "%unk", "fileName": "%unk" }
                    },
                    "name": "isInteger",
                    "parameters": [],
                    "returnType": { "_": "NumberType" }
                  },
                  "args": []
                }
            """.trimIndent(),
        )

        assertNull(assertIs<StaticCallExprDto>(dto).builtinProof)
    }

    @Test
    fun `rejects malformed builtin proof`() {
        val malformed = """
            {
              "_": "StaticCallExpr",
              "method": {
                "declaringClass": {
                  "name": "Number",
                  "declaringFile": { "projectName": "%unk", "fileName": "%unk" }
                },
                "name": "isInteger",
                "parameters": [],
                "returnType": { "_": "NumberType" }
              },
              "args": [],
              "builtinProof": {
                "builtin": "UNKNOWN_BUILTIN",
                "entryRequirement": "DIRECT_ISOLATED_ENTRY",
                "entryMethod": {
                  "declaringClass": {
                    "name": "%dflt",
                    "declaringFile": { "projectName": "project", "fileName": "entry.ts" }
                  },
                  "name": "%AM0${'$'}%dflt",
                  "parameters": [],
                  "returnType": { "_": "NumberType" }
                }
              }
            }
        """.trimIndent()

        assertFailsWith<SerializationException> {
            json.decodeFromString<ValueDto>(malformed)
        }
    }
}
