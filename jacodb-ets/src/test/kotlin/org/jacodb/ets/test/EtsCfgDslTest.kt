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

import org.jacodb.ets.dsl.add
import org.jacodb.ets.dsl.const
import org.jacodb.ets.dsl.local
import org.jacodb.ets.dsl.lt
import org.jacodb.ets.dsl.param
import org.jacodb.ets.dsl.program
import org.jacodb.ets.dsl.toBlockCfg
import org.jacodb.ets.dsl.toDot
import org.jacodb.ets.model.BasicBlock
import org.jacodb.ets.model.EtsAssignStmt
import org.jacodb.ets.model.EtsBlockCfg
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsFileSignature
import org.jacodb.ets.model.EtsLocal
import org.jacodb.ets.model.EtsMethodImpl
import org.jacodb.ets.model.EtsMethodParameter
import org.jacodb.ets.model.EtsMethodSignature
import org.jacodb.ets.model.EtsNopStmt
import org.jacodb.ets.model.EtsNumberType
import org.jacodb.ets.model.EtsStmtLocation
import org.jacodb.ets.model.EtsUnknownType
import org.jacodb.ets.utils.linearize
import org.jacodb.ets.utils.toDot
import org.jacodb.ets.utils.toEtsBlockCfg
import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class EtsCfgDslTest {
    @Test
    fun `program DOT gives repeated nop positions distinct nodes and stops at return`() {
        val prog = program {
            nop()
            nop()
            ret(const(0))
            nop()
        }

        val dot = prog.toDot()

        assertTrue(dot.contains("  0 -> 1"))
        assertTrue(dot.contains("  1 -> 2"))
        assertFalse(dot.contains("  0 -> 0"))
        assertFalse(dot.contains("  2 -> 3"))
    }

    @Test
    fun `program DOT joins both conditional branches without bypassing them`() {
        val prog = program {
            val i = local("i")

            ifStmt(const(true)) {
                assign(i, const(1))
            }.`else` {
                assign(i, const(2))
            }
            ret(i)
        }

        val dot = prog.toDot()

        assertTrue(dot.contains("  0 -> 1 [label=\"true\"]"))
        assertTrue(dot.contains("  0 -> 2 [label=\"false\"]"))
        assertTrue(dot.contains("  1 -> 3"))
        assertTrue(dot.contains("  2 -> 3"))
        assertFalse(dot.contains("  0 -> 3"))
    }

    @Test
    fun `program DOT labels empty branch continuation and goto has no fallthrough`() {
        val prog = program {
            val i = local("i")

            ifStmt(const(true)) {
                assign(i, const(1))
            }
            goto("done")
            nop()
            label("done")
            ret(i)
        }

        val dot = prog.toDot()

        assertTrue(dot.contains("  0 -> 1 [label=\"true\"]"))
        assertTrue(dot.contains("  0 -> 2 [label=\"false\"]"))
        assertTrue(dot.contains("  1 -> 2"))
        assertTrue(dot.contains("  2 -> 4"))
        assertFalse(dot.contains("  2 -> 3"))
    }

    @Test
    fun `block CFG rejects successor IDs equal to block count`() {
        val method = EtsMethodImpl(
            signature = EtsMethodSignature(
                enclosingClass = EtsClassSignature.UNKNOWN,
                name = "testMethod",
                parameters = emptyList(),
                returnType = EtsUnknownType,
            ),
        )
        val block = BasicBlock(
            id = 0,
            statements = listOf(EtsNopStmt(location = EtsStmtLocation.stub(method))),
        )

        assertFailsWith<IllegalArgumentException> {
            EtsBlockCfg(blocks = listOf(block), successors = mapOf(0 to listOf(1)))
        }
        assertFailsWith<IllegalArgumentException> {
            EtsBlockCfg(blocks = listOf(block), successors = mapOf(1 to emptyList()))
        }
    }

    @Test
    fun `block CFG retains valid self-loop terminal and empty graphs`() {
        val method = EtsMethodImpl(
            signature = EtsMethodSignature(
                enclosingClass = EtsClassSignature.UNKNOWN,
                name = "testMethod",
                parameters = emptyList(),
                returnType = EtsUnknownType,
            ),
        )
        val stmt = EtsNopStmt(location = EtsStmtLocation.stub(method))
        val block = BasicBlock(id = 0, statements = listOf(stmt))

        val selfLoop = EtsBlockCfg(blocks = listOf(block), successors = mapOf(0 to listOf(0)))
        val terminal = EtsBlockCfg(blocks = listOf(block), successors = mapOf(0 to emptyList()))

        assertEquals(setOf(stmt), selfLoop.successors(stmt))
        assertEquals(emptySet(), terminal.successors(stmt))
        assertEquals(emptyList(), EtsBlockCfg.EMPTY.stmts)
    }

    @Test
    fun `test simple program`() {
        val prog = program {
            val i = local("i")

            // i := arg(0)
            assign(i, param(0))

            // if (i < 10) i += 50
            ifStmt(lt(i, const(10.0))) {
                assign(i, add(i, const(50.0)))
            }

            // return i
            ret(i)
        }
        println("program:\n${prog.toText()}")
        val blockCfg = prog.toBlockCfg()
        println("blockCfg:\n${blockCfg.toDot()}")

        val method = EtsMethodImpl(
            signature = EtsMethodSignature(
                enclosingClass = EtsClassSignature(
                    name = "Test",
                    file = EtsFileSignature(
                        projectName = "TestProject",
                        fileName = "Test.ts",
                    ),
                ),
                name = "testMethod",
                parameters = listOf(
                    EtsMethodParameter(0, "a", EtsUnknownType),
                ),
                returnType = EtsNumberType,
            ),
        )

        val etsBlockCfg = blockCfg.toEtsBlockCfg(method)
        println("etsBlockCfg:\n${etsBlockCfg.toDot()}")
        val etsCfg = etsBlockCfg.linearize()
        println("etsCfg:\n${etsCfg.toDot()}")

        method.body.cfg = etsBlockCfg
        method.body.locals = etsBlockCfg.stmts
            .filterIsInstance<EtsAssignStmt>()
            .mapNotNull { it.lhv as? EtsLocal }
            .distinct()

        assertEquals(method.locals, listOf(
            EtsLocal("i", EtsUnknownType),
            EtsLocal("_tmp0", EtsUnknownType),
        ))
    }
}
