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

import io.mockk.mockk
import org.jacodb.ets.model.EtsAddExpr
import org.jacodb.ets.model.EtsAssignStmt
import org.jacodb.ets.model.EtsEntity
import org.jacodb.ets.model.EtsLocal
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsNumberConstant
import org.jacodb.ets.model.EtsStmt
import org.jacodb.ets.model.EtsStmtLocation
import org.jacodb.ets.model.EtsUnknownType
import org.jacodb.ets.utils.AbstractHandler
import org.jacodb.ets.utils.EntityCollector
import kotlin.test.Test
import kotlin.test.assertEquals

class CollectorTest {

    private fun createStmt(): EtsStmt {
        val method = mockk<EtsMethod>()
        val a = EtsLocal("a")
        val b = EtsLocal("b")
        val n = EtsNumberConstant(42.0)
        val rhv = EtsAddExpr(b, n, type = EtsUnknownType)
        val loc = EtsStmtLocation.stub(method)
        val stmt = EtsAssignStmt(loc, a, rhv)
        return stmt
    }

    @Test
    fun `test AbstractHandler`() {
        val stmt = createStmt()
        val result = mutableSetOf<String>()
        val c = object : AbstractHandler() {
            override fun handle(value: EtsEntity) {
                result += value.toString()
            }

            override fun handle(stmt: EtsStmt) {
                result += stmt.toString()
            }
        }
        stmt.accept(c)
        println(result)
        assertEquals("[a := b + 42.0, a, b + 42.0, b, 42.0]", result.toString())
    }

    @Test
    fun `test EntityCollector`() {
        val stmt = createStmt()
        val c = EntityCollector(mutableSetOf<String>()) {
            it.toString()
        }
        stmt.accept(c)
        println(c.result)
        assertEquals("[a, b + 42.0, b, 42.0]", c.result.toString())
    }
}
