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
import org.jacodb.testing.WithDB
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class IincTest : BaseInstructionsTest() {

    companion object : WithDB()

    @Test
    fun `iinc should work`() {
        val clazz = cp.findClass<Incrementation>()

        val javaClazz = testAndLoadClass(clazz)
        val method = javaClazz.methods.first { it.name == "iinc" }
        val res = method.invoke(null, 0)
        assertEquals(0, res)
    }

    @Test
    fun `iinc arrayIntIdx should work`() {
        val clazz = cp.findClass<Incrementation>()

        val javaClazz = testAndLoadClass(clazz)
        val method = javaClazz.methods.first { it.name == "iincArrayIntIdx" }
        val res = method.invoke(null)
        assertArrayEquals(intArrayOf(1, 0, 2), res as IntArray)
    }

    @Test
    fun `iinc arrayByteIdx should work`() {
        val clazz = cp.findClass<Incrementation>()

        val javaClazz = testAndLoadClass(clazz)
        val method = javaClazz.methods.first { it.name == "iincArrayByteIdx" }
        val res = method.invoke(null)
        assertArrayEquals(intArrayOf(1, 0, 2), res as IntArray)
    }

    @Test
    fun `iinc for`() {
        val clazz = cp.findClass<Incrementation>()

        val javaClazz = testAndLoadClass(clazz)
        val method = javaClazz.methods.first { it.name == "iincFor" }
        val res = method.invoke(null)
        assertArrayEquals(intArrayOf(0, 1, 2, 3, 4), res as IntArray)
    }

    @Test
    fun `iinc if`() {
        val clazz = cp.findClass<Incrementation>()

        val javaClazz = testAndLoadClass(clazz)
        val method = javaClazz.methods.first { it.name == "iincIf" }
        assertArrayEquals(intArrayOf(), method.invoke(null, true, true) as IntArray)
        assertArrayEquals(intArrayOf(0), method.invoke(null, true, false) as IntArray)
    }

    @Test
    fun `iinc if 2`() {
        val clazz = cp.findClass<Incrementation>()

        val javaClazz = testAndLoadClass(clazz)
        val method = javaClazz.methods.first { it.name == "iincIf2" }
        assertEquals(2, method.invoke(null, 1))
        assertEquals(4, method.invoke(null, 2))
    }

//    @Test
    fun `iinc while`() {
        val clazz = cp.findClass<Incrementation>()

        val javaClazz = testAndLoadClass(clazz)
        val method = javaClazz.methods.first { it.name == "iincWhile" }
        assertEquals(2, method.invoke(null) as IntArray)
    }

}