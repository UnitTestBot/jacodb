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

import org.jacodb.ets.dto.BasicBlockDto
import org.jacodb.ets.dto.BodyDto
import org.jacodb.ets.dto.CfgDto
import org.jacodb.ets.dto.ClassSignatureDto
import org.jacodb.ets.dto.ClosureFieldRefDto
import org.jacodb.ets.dto.ConstantDto
import org.jacodb.ets.dto.FileSignatureDto
import org.jacodb.ets.dto.AssignStmtDto
import org.jacodb.ets.dto.LocalDto
import org.jacodb.ets.dto.MethodDto
import org.jacodb.ets.dto.MethodSignatureDto
import org.jacodb.ets.dto.NumberTypeDto
import org.jacodb.ets.dto.ReturnStmtDto
import org.jacodb.ets.dto.ReturnVoidStmtDto
import org.jacodb.ets.dto.SourceSpanDto
import org.jacodb.ets.dto.StmtOriginDto
import org.jacodb.ets.dto.StmtOriginKey
import org.jacodb.ets.dto.toEtsMethod
import org.jacodb.ets.model.EtsAssignStmt
import org.jacodb.ets.model.EtsClosureFieldRef
import org.jacodb.ets.model.EtsNopStmt
import org.jacodb.ets.model.EtsReturnStmt
import org.jacodb.ets.model.EtsSourceSpan
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame

class EtsMethodBuilderTest {
    @Test
    fun `generated local skips names supplied by DTO`() {
        val methodDto = MethodDto(
            signature = MethodSignatureDto(
                declaringClass = ClassSignatureDto(
                    name = "TestClass",
                    declaringFile = FileSignatureDto(projectName = "TestProject", fileName = "test.ts"),
                ),
                name = "testMethod",
                parameters = emptyList(),
                returnType = NumberTypeDto,
            ),
            modifiers = 0,
            decorators = emptyList(),
            body = BodyDto(
                locals = listOf(
                    LocalDto(name = "_tmp0", type = NumberTypeDto),
                    LocalDto(name = "_tmp1", type = NumberTypeDto),
                ),
                cfg = CfgDto(
                    blocks = listOf(
                        BasicBlockDto(
                            id = 0,
                            successors = emptyList(),
                            stmts = listOf(ReturnStmtDto(arg = ConstantDto(value = "1", type = NumberTypeDto))),
                        ),
                    ),
                ),
            ),
        )

        val method = methodDto.toEtsMethod()
        val statements = method.cfg.blocks.single().statements
        val assignment = assertIs<EtsAssignStmt>(statements[0])
        val returnStmt = assertIs<EtsReturnStmt>(statements[1])

        assertEquals(listOf("_tmp0", "_tmp1", "_tmp2"), method.locals.map { it.name })
        assertSame(method.locals[2], assignment.lhv)
        assertSame(assignment.lhv, returnStmt.returnValue)
    }

    @Test
    fun `assignment accepts closure field reference as LHV`() {
        val methodDto = MethodDto(
            signature = MethodSignatureDto(
                declaringClass = ClassSignatureDto(
                    name = "TestClass",
                    declaringFile = FileSignatureDto("TestProject", "test.ts"),
                ),
                name = "testMethod",
                parameters = emptyList(),
                returnType = NumberTypeDto,
            ),
            modifiers = 0,
            decorators = emptyList(),
            body = BodyDto(
                locals = emptyList(),
                cfg = CfgDto(
                    blocks = listOf(
                        BasicBlockDto(
                            0,
                            successors = emptyList(),
                            stmts = listOf(
                                AssignStmtDto(
                                    left = ClosureFieldRefDto(
                                        base = LocalDto("%closures0", NumberTypeDto),
                                        fieldName = "captured",
                                        type = NumberTypeDto,
                                    ),
                                    right = LocalDto("value", NumberTypeDto),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val statement = assertIs<EtsAssignStmt>(methodDto.toEtsMethod().cfg.blocks.single().statements.single())

        assertIs<EtsClosureFieldRef>(statement.lhv)
    }

    @Test
    fun `source statement origin does not leak to synthetic empty block nop`() {
        val originKey = StmtOriginKey(blockId = 0, stmtIndex = 0)
        val sourceSpan = SourceSpanDto(
            startOffset = 4,
            endOffset = 11,
            startLine = 1,
            startColumn = 2,
            endLine = 1,
            endColumn = 9,
            nodeKind = "ReturnStatement",
        )
        val methodDto = MethodDto(
            signature = MethodSignatureDto(
                declaringClass = ClassSignatureDto(
                    name = "TestClass",
                    declaringFile = FileSignatureDto("TestProject", "test.ts"),
                ),
                name = "testMethod",
                parameters = emptyList(),
                returnType = NumberTypeDto,
            ),
            modifiers = 0,
            decorators = emptyList(),
            body = BodyDto(
                locals = emptyList(),
                cfg = CfgDto(
                    blocks = listOf(
                        BasicBlockDto(0, successors = emptyList(), stmts = listOf(ReturnVoidStmtDto)),
                        BasicBlockDto(1, successors = emptyList(), stmts = emptyList()),
                    ),
                ),
                stmtOrigins = listOf(StmtOriginDto(originKey.blockId, originKey.stmtIndex, sourceSpan)),
            ),
        )

        val method = methodDto.toEtsMethod()

        assertEquals(
            EtsSourceSpan("test.ts", 4, 11, 1, 2, 1, 9, "ReturnStatement"),
            method.cfg.blocks[0].statements.single().location.origin,
        )
        val syntheticNop = assertIs<EtsNopStmt>(method.cfg.blocks[1].statements.single())
        assertNull(syntheticNop.location.origin)
    }
}
