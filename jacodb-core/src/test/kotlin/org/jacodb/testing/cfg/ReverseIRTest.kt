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

package org.jacodb.testing.cfg

import org.jacodb.api.ext.findClass
import org.jacodb.testing.ir.DoubleComparison
import org.jacodb.testing.ir.InvokeMethodWithException
import org.jacodb.testing.ir.WhenExpr
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ReverseIRTest : BaseInstructionsTest() {

    @Test
    fun comparison() {
        val clazz = testAndLoadClass(cp.findClass<DoubleComparison>())
        val m = clazz.declaredMethods.first { it.name == "box" }
        assertEquals("OK", m.invoke(null))
    }

    @Test
    fun `when`() {
        val clazz = testAndLoadClass(cp.findClass<WhenExpr>())
        val m = clazz.declaredMethods.first { it.name == "box" }
        assertEquals("OK", m.invoke(null))
    }

    @Test
    fun `local vars`() {
        val clazz = testAndLoadClass(cp.findClass<InvokeMethodWithException>())
        val m = clazz.declaredMethods.first { it.name == "box" }
        assertEquals("OK", m.invoke(null))
    }

}