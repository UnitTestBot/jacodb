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

import model.BlockCfg
import model.Scene
import org.jacodb.ets.dto.toDto
import org.jacodb.ets.dto.toEtsConstant
import org.jacodb.ets.model.BasicBlock
import org.jacodb.ets.model.EtsAnyType
import org.jacodb.ets.model.EtsAssignStmt
import org.jacodb.ets.model.EtsAwaitExpr
import org.jacodb.ets.model.EtsBlockCfg
import org.jacodb.ets.model.EtsBooleanType
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsExpr
import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.model.EtsFileSignature
import org.jacodb.ets.model.EtsIfStmt
import org.jacodb.ets.model.EtsLocal
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsMethodImpl
import org.jacodb.ets.model.EtsMethodSignature
import org.jacodb.ets.model.EtsNumberConstant
import org.jacodb.ets.model.EtsNumberType
import org.jacodb.ets.model.EtsReturnStmt
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsStmtLocation
import org.jacodb.ets.model.EtsStringConstant
import org.jacodb.ets.model.EtsStringType
import org.jacodb.ets.model.EtsYieldExpr
import org.jacodb.ets.proto.toEts
import org.jacodb.ets.proto.toProto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ProtoRoundtripTest {
    @Test
    fun `scene binary roundtrip keeps project and SDK files separate`() {
        val project = file(name = "project.ts")
        val sdk = file(name = "sdk.d.ts")
        val scene = EtsScene(projectFiles = listOf(project), sdkFiles = listOf(sdk))

        val decoded = Scene.ADAPTER.decode(scene.toProto().encode()).toEts()

        assertEquals(listOf(project.signature), decoded.projectFiles.map { it.signature })
        assertEquals(listOf(sdk.signature), decoded.sdkFiles.map { it.signature })
    }

    @Test
    fun `constant payload is raw text rather than quoted display text`() {
        val method = method()
        val values = listOf("", "hello", "a\"b\\c\n", "привет😀")

        for (value in values) {
            val constant = EtsStringConstant(value = value)
            val proto = constant.toProto()
            val dto = constant.toDto()
            val cfg = EtsBlockCfg(
                blocks = listOf(BasicBlock(id = 0, statements = listOf(
                    EtsReturnStmt(location = EtsStmtLocation.stub(method), returnValue = constant),
                ))),
                successors = mapOf(0 to emptyList()),
            )

            val decoded = BlockCfg.ADAPTER.decode(cfg.toProto().encode()).toEts(method)
            val statements = decoded.blocks.single().statements
            val returned = assertIs<EtsReturnStmt>(statements.last())
            val materialized = statements.filterIsInstance<EtsAssignStmt>().single()

            assertEquals(expected = value, actual = proto.value_)
            assertEquals(expected = value, actual = dto.value)
            assertEquals(constant, dto.toEtsConstant())
            assertEquals(constant, materialized.rhv)
            assertEquals(materialized.lhv, returned.returnValue)
            assertEquals(expected = "\"$value\"", actual = constant.toString())
        }
    }

    @Test
    fun `conditional binary roundtrip keeps unequal true and false branch targets`() {
        val method = method()
        val location = EtsStmtLocation.stub(method)
        val cfg = EtsBlockCfg(
            blocks = listOf(
                BasicBlock(id = 0, statements = listOf(EtsIfStmt(
                    location = location,
                    condition = EtsLocal(name = "condition", type = EtsBooleanType),
                ))),
                BasicBlock(id = 1, statements = listOf(EtsReturnStmt(
                    location = location, returnValue = EtsNumberConstant(value = 11.0),
                ))),
                BasicBlock(id = 2, statements = listOf(EtsReturnStmt(
                    location = location, returnValue = EtsNumberConstant(value = 22.0),
                ))),
            ),
            successors = mapOf(0 to listOf(1, 2), 1 to emptyList(), 2 to emptyList()),
        )

        val proto = cfg.toProto()
        val decoded = BlockCfg.ADAPTER.decode(proto.encode()).toEts(method)

        assertEquals(expected = listOf(2, 1), actual = proto.blocks.first().successors)
        assertEquals(cfg.successors, decoded.successors)
        val trueTarget = decoded.successors.getValue(0).first()
        val falseTarget = decoded.successors.getValue(0).last()
        assertEquals(EtsNumberConstant(value = 11.0), decoded.blocks[trueTarget].statements
            .filterIsInstance<EtsAssignStmt>().single().rhv)
        assertEquals(EtsNumberConstant(value = 22.0), decoded.blocks[falseTarget].statements
            .filterIsInstance<EtsAssignStmt>().single().rhv)
    }

    @Test
    fun `await and yield binary roundtrip retain their explicit result types`() {
        val method = method()
        val expressions: List<EtsExpr> = listOf(
            EtsAwaitExpr(arg = EtsLocal(name = "promise", type = EtsAnyType), type = EtsStringType),
            EtsYieldExpr(arg = EtsLocal(name = "value", type = EtsAnyType), type = EtsNumberType),
        )

        for (expression in expressions) {
            val cfg = EtsBlockCfg(
                blocks = listOf(BasicBlock(id = 0, statements = listOf(EtsAssignStmt(
                    location = EtsStmtLocation.stub(method),
                    lhv = EtsLocal(name = "result", type = expression.type),
                    rhv = expression,
                )))),
                successors = mapOf(0 to emptyList()),
            )

            val decoded = BlockCfg.ADAPTER.decode(cfg.toProto().encode()).toEts(method)
            val assignment = assertIs<EtsAssignStmt>(decoded.blocks.single().statements.single())

            assertEquals(expression, assignment.rhv)
        }
    }

    private fun file(name: String): EtsFile = EtsFile(
        signature = EtsFileSignature(projectName = "roundtrip", fileName = name),
        classes = emptyList(),
    )

    private fun method(): EtsMethod = EtsMethodImpl(
        signature = EtsMethodSignature(
            name = "test",
            enclosingClass = EtsClassSignature.UNKNOWN,
            parameters = emptyList(),
            returnType = EtsAnyType,
        ),
    )
}
