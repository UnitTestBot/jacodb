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

import org.jacodb.ets.dsl.Assign
import org.jacodb.ets.dsl.ConstantBoolean
import org.jacodb.ets.dsl.ConstantInt
import org.jacodb.ets.dsl.If
import org.jacodb.ets.dsl.IfBuilder
import org.jacodb.ets.dsl.Local
import org.jacodb.ets.dsl.Nop
import org.jacodb.ets.dsl.ProgramBuilderImpl
import org.jacodb.ets.dsl.const
import org.jacodb.ets.dsl.local
import org.jacodb.ets.dsl.program
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class EtsProgramBuilderTest {
    @Test
    fun `else-if chain attaches each alternative to the preceding false branch`() {
        val result = Local("result")
        val firstCondition = ConstantBoolean(false)
        val secondCondition = ConstantBoolean(true)
        val thirdCondition = ConstantBoolean(true)
        val firstAssignment = Assign(result, ConstantInt(1))
        val secondAssignment = Assign(result, ConstantInt(2))
        val thirdAssignment = Assign(result, ConstantInt(3))
        val fallbackAssignment = Assign(result, ConstantInt(4))

        val built = program {
            ifStmt(firstCondition) {
                assign(result, const(1))
            }.elseIf(secondCondition) {
                assign(result, const(2))
            }.elseIf(thirdCondition) {
                assign(result, const(3))
            }.`else` {
                assign(result, const(4))
            }
        }

        val firstIf = built.nodes.single() as If
        val secondIf = firstIf.elseBranch.single() as If
        val thirdIf = secondIf.elseBranch.single() as If

        assertEquals(firstCondition, firstIf.condition)
        assertEquals(listOf(firstAssignment), firstIf.thenBranch)
        assertEquals(secondCondition, secondIf.condition)
        assertEquals(listOf(secondAssignment), secondIf.thenBranch)
        assertEquals(thirdCondition, thirdIf.condition)
        assertEquals(listOf(thirdAssignment), thirdIf.thenBranch)
        assertEquals(listOf(fallbackAssignment), thirdIf.elseBranch)
    }

    @Test
    fun `build snapshots top-level nodes before later builder calls`() {
        val builder = ProgramBuilderImpl()
        builder.nop()

        val built = builder.build()
        builder.ret(ConstantInt(1))

        assertEquals(listOf(Nop), built.nodes)
    }

    @Test
    fun `program snapshots nested branches before retained builder calls`() {
        lateinit var retainedBranch: IfBuilder
        val built = program {
            retainedBranch = ifStmt(const(true)) {
                assign(local("value"), const(1))
            }
        }

        retainedBranch.assign(Local("value"), ConstantInt(2))
        retainedBranch.`else` {
            assign(local("value"), const(3))
        }

        val builtIf = built.nodes.single() as If
        assertEquals(listOf(Assign(Local("value"), ConstantInt(1))), builtIf.thenBranch)
        assertEquals(emptyList(), builtIf.elseBranch)
    }
}
