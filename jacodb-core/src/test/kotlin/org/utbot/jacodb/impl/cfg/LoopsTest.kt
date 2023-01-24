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

package org.utbot.jacodb.impl.cfg

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.utbot.jacodb.api.JcClassOrInterface
import org.utbot.jacodb.api.JcMethod
import org.utbot.jacodb.api.ext.findClass
import org.utbot.jacodb.impl.BaseTest
import org.utbot.jacodb.impl.WithDB
import org.utbot.jacodb.impl.cfg.util.JcLoop
import org.utbot.jacodb.impl.cfg.util.loops
import org.utbot.jacodb.impl.features.InMemoryHierarchy

class LoopsTest : BaseTest() {

    companion object : WithDB(InMemoryHierarchy)

    @Test
    fun `loop inside loop should work`() {
        val clazz = cp.findClass<JavaTasks>()
        with(clazz.findMethod("insertionSort").loops) {
            assertEquals(2, size)
            with(get(1)) {
                assertEquals(36, head.lineNumber)
                assertEquals(2, exits.size)
                assertSources(36, 37)
            }

            with(first()) {
                assertEquals(31, head.lineNumber)
                assertEquals(1, exits.size)
                assertSources(31, 41)
            }
        }
    }

    @Test
    fun `simple for loops`() {
        val clazz = cp.findClass<JavaTasks>()
        with(clazz.findMethod("heapSort").loops) {
            assertEquals(2, size)
            with(first()) {
                assertEquals(98, head.lineNumber)
                assertEquals(1, exits.size)
                assertSources(98, 99)
            }

            with(get(1)) {
                assertEquals(102, head.lineNumber)
                assertEquals(1, exits.size)
                assertSources(102, 107)
            }
        }
    }

    @Test
    fun `simple while loops`() {
        val clazz = cp.findClass<JavaTasks>()
        with(clazz.findMethod("sortTemperatures").loops) {
            assertEquals(2, size)
            with(first()) {
                assertEquals(135, head.lineNumber)
                assertEquals(1, exits.size)
                assertSources(135, 138)
            }
            with(get(1)) {
                assertEquals(148, head.lineNumber)
                assertEquals(1, exits.size)
                assertSources(148, 150)
            }
        }
    }

    @Test
    fun `combined loops`() {
        val clazz = cp.findClass<JavaTasks>()
        with(clazz.findMethod("sortTimes").loops) {
            assertEquals(3, size)
            with(first()) {
                assertEquals(53, head.lineNumber)
                assertEquals(listOf(53,61, 73) , exits.map { it.lineNumber }.toSet().sorted())
                assertSources(53, 75)
            }

            with(get(1)) {
                assertEquals(82, head.lineNumber)
                assertEquals(1, exits.size)
                assertSources(82, 84)
            }
            with(get(2)) {
                assertEquals(85, head.lineNumber)
                assertEquals(1, exits.size)
                assertSources(85, 87)
            }
        }
    }

    private fun JcClassOrInterface.findMethod(name: String): JcMethod = declaredMethods.first { it.name == name }

    private val JcMethod.loops: List<JcLoop>
        get() {
            return this.flowGraph().loops.toList().sortedBy { it.head.lineNumber }
        }


    private fun JcLoop.assertSources(start: Int, end: Int) {
        val sourceLineNumbers = instructions.map { it.lineNumber }
        assertEquals(end, sourceLineNumbers.max())
        assertEquals(start, sourceLineNumbers.min())
    }
}