/**
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
package org.utbot.jcdb.impl.types

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.utbot.jcdb.api.isSynthetic
import org.utbot.jcdb.impl.types.Comparables.ComparableTest1
import org.utbot.jcdb.impl.types.Comparables.ComparableTest2
import org.utbot.jcdb.impl.types.Comparables.ComparableTest3
import org.utbot.jcdb.impl.types.Comparables.ComparableTest5

class RecursiveTypesTest : BaseTypesTest() {

    @Test
    fun `recursive type`() {
        runBlocking {
            val comparable1 = findType<ComparableTest1>()
            val compareTo = comparable1.methods.first { it.name == "compareTo" && !it.method.isSynthetic }
            assertEquals("int", compareTo.returnType.typeName)
            compareTo.parameters.first().type.assertClassType<ComparableTest1>()
        }
    }

    @Test
    fun `declaration of recursive type`() {
        runBlocking {
            val comparable2 = findType<ComparableTest2<*>>()
            val compareTo = comparable2.methods.first { it.name == "compareTo" && !it.method.isSynthetic }
            assertEquals("int", compareTo.returnType.typeName)
            with(compareTo.parameters.first().type) {
                assertEquals("T", typeName)
            }
        }
    }

    @Test
    fun `extending type with recursion in declaration`() {
        runBlocking {
            val comparable3 = findType<ComparableTest3>()
            val compareTo = comparable3.superType!!.methods.first { it.name == "compareTo" && !it.method.isSynthetic }
            assertEquals("int", compareTo.returnType.typeName)
            compareTo.parameters.first().type.assertClassType<ComparableTest3>()
        }
    }

    @Test
    fun `extending recursive types in declaration`() {
        runBlocking {
            val comparable5 = findType<ComparableTest5>()
            with(comparable5.superType!!.fields) {
                first { it.name == "stateT" }.fieldType.assertClassType<Int>()
                first { it.name == "stateW" }.fieldType.assertClassType<Int>()
            }
        }
    }

}