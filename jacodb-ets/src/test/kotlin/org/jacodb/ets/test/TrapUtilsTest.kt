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

import org.jacodb.ets.model.BasicBlock
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsMethodImpl
import org.jacodb.ets.model.EtsMethodSignature
import org.jacodb.ets.model.EtsNopStmt
import org.jacodb.ets.model.EtsStmtLocation
import org.jacodb.ets.model.EtsTrap
import org.jacodb.ets.model.EtsUnknownType
import org.jacodb.ets.utils.TrapUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

class TrapUtilsTest {
    private fun mkMethod(): EtsMethod {
        val cls = EtsClassSignature.UNKNOWN
        val sig = EtsMethodSignature(cls, "foo", emptyList(), EtsUnknownType)
        return EtsMethodImpl(sig)
    }

    private fun nop(method: EtsMethod): EtsNopStmt =
        EtsNopStmt(EtsStmtLocation.stub(method))

    @Test
    fun `find innermost trap among nested traps`() {
        val method = mkMethod()

        // create statements bound to method
        val s0 = nop(method)
        val s1 = nop(method)
        val s2 = nop(method)
        val s3 = nop(method)
        val s4 = nop(method)

        // create basic blocks
        val b0 = BasicBlock(0, listOf(s0))
        val b1 = BasicBlock(1, listOf(s1))
        val b2 = BasicBlock(2, listOf(s2))
        val b3 = BasicBlock(3, listOf(s3))
        val b4 = BasicBlock(4, listOf(s4))

        // outer trap covers b0..b2, handler at b3
        val outer = EtsTrap(listOf(b0, b1, b2), listOf(b3))
        // inner trap covers only b1, handler at b4
        val inner = EtsTrap(listOf(b1), listOf(b4))

        val traps = listOf(outer, inner)

        val inn = TrapUtils.findInnermostTrap(traps, b1)
        assertNotNull(inn)
        assertSame(inner, inn)

        val forB0 = TrapUtils.findInnermostTrap(traps, b0)
        assertSame(outer, forB0)

        val none = TrapUtils.findInnermostTrap(traps, b3)
        assertNull(none)
    }

    @Test
    fun `trapsForBlock returns nested order inner first`() {
        val method = mkMethod()
        val s0 = nop(method)
        val s1 = nop(method)
        val b0 = BasicBlock(0, listOf(s0))
        val b1 = BasicBlock(1, listOf(s1))

        val t1 = EtsTrap(listOf(b0, b1), listOf())
        val t2 = EtsTrap(listOf(b1), listOf())
        val list = TrapUtils.trapsForBlock(listOf(t1, t2), b1)
        assertEquals(2, list.size)
        assertSame(t2, list[0])
        assertSame(t1, list[1])
    }

    @Test
    fun `handlerEntry and nestingDepth`() {
        val method = mkMethod()
        val s0 = nop(method)
        val s1 = nop(method)
        val h0 = nop(method)
        val h1 = nop(method)
        val b0 = BasicBlock(0, listOf(s0))
        val b1 = BasicBlock(1, listOf(s1))
        val bh0 = BasicBlock(2, listOf(h0))
        val bh1 = BasicBlock(3, listOf(h1))

        val top = EtsTrap(listOf(b0, b1), listOf(bh0))
        val inner = EtsTrap(listOf(b1), listOf(bh1))
        val depths = TrapUtils.nestingDepth(listOf(top, inner))

        assertEquals(bh0, TrapUtils.handlerEntry(top))
        assertEquals(bh1, TrapUtils.handlerEntry(inner))

        assertEquals(0, depths[top])
        assertEquals(1, depths[inner])
    }

    @Test
    fun `mapBlockToTraps builds mapping inner-first`() {
        val method = mkMethod()
        val s0 = nop(method)
        val s1 = nop(method)
        val b0 = BasicBlock(0, listOf(s0))
        val b1 = BasicBlock(1, listOf(s1))

        val t1 = EtsTrap(listOf(b0, b1), listOf())
        val t2 = EtsTrap(listOf(b1), listOf())

        val mapping = TrapUtils.mapBlockToTraps(listOf(t1, t2))

        val forB1 = mapping[b1.id]
        assertNotNull(forB1)
        assertEquals(2, forB1.size)
        assertSame(t2, forB1[0])
        assertSame(t1, forB1[1])

        val forB0 = mapping[b0.id]
        assertNotNull(forB0)
        assertEquals(1, forB0.size)
        assertSame(t1, forB0[0])
    }
}
