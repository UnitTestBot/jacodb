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
import org.utbot.jacodb.impl.cfg.util.loops
import org.utbot.jacodb.impl.features.InMemoryHierarchy

class LoopsTest : BaseTest() {

    companion object : WithDB(InMemoryHierarchy)

    @Test
    fun `loop inside loop should work`() {
        val clazz = cp.findClass<JavaTasks>()
        with(clazz.findMethod("insertionSort").flowGraph().loops) {
            assertEquals(2, size)
        }
    }

    @Test
    fun `simple for loops`() {
        val clazz = cp.findClass<JavaTasks>()
        with(clazz.findMethod("heapSort").flowGraph().loops) {
            assertEquals(2, size)
        }
    }

    @Test
    fun `simple while loops`() {
        val clazz = cp.findClass<JavaTasks>()
        with(clazz.findMethod("sortTemperatures").flowGraph().loops) {
            assertEquals(2, size)
        }
    }

    @Test
    fun `combined loops`() {
        val clazz = cp.findClass<JavaTasks>()
        with(clazz.findMethod("sortTimes").flowGraph().loops) {
            assertEquals(3, size)
        }
    }

    private fun JcClassOrInterface.findMethod(name: String): JcMethod = declaredMethods.first { it.name == name }

}