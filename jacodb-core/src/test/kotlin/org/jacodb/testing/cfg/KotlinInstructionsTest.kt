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

import org.jacodb.testing.WithDB
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class KotlinInstructionsTest: BaseInstructionsTest() {

    companion object : WithDB()

    private fun runTest(className: String) {
        val clazz = cp.findClassOrNull(className)
        Assertions.assertNotNull(clazz)

        val javaClazz = testAndLoadClass(clazz!!)
        val clazzInstance = javaClazz.constructors.first().newInstance()
        val method = javaClazz.methods.first { it.name == "box" }
        val res = method.invoke(clazzInstance)
        Assertions.assertEquals("OK", res)
    }

    @Test
    fun `simple test`() = runTest(SimpleTest::class.java.name)

    @Test
    fun `kotlin vararg test`() = runTest(Varargs::class.java.name)

    @Test
    fun `kotlin equals test`() = runTest(Equals::class.java.name)

    @Test
    fun `kotlin different receivers test`() = runTest(DifferentReceivers::class.java.name)

    @Test
    fun `kotlin sequence test`() = runTest(KotlinSequence::class.java.name)

    @Test
    fun `kotlin range test`() = runTest(Ranges::class.java.name)

    @Test
    fun `kotlin overloading test`() = runTest(Overloading::class.java.name)
}