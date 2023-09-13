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

import org.junit.jupiter.api.Assertions.assertEquals
import java.lang.reflect.GenericArrayType
import java.lang.reflect.ParameterizedType
import java.lang.reflect.TypeVariable
import kotlin.reflect.jvm.*

class Arrays {

    fun foo(strings: Array<String>, integers: Array<Int>, objectArrays: Array<Array<Any>>) {}

    fun bar(): Array<List<String>> = null!!
    class A<T> {
        fun baz(): Array<T> = null!!
    }

    fun box(): String {
        assertEquals(Array<String>::class.java, ::foo.parameters[0].type.javaType)
        assertEquals(Array<Int>::class.java, ::foo.parameters[1].type.javaType)
        assertEquals(Array<Array<Any>>::class.java, ::foo.parameters[2].type.javaType)

        val g = ::bar.returnType.javaType
        println(g)
        if (g !is GenericArrayType || g.genericComponentType !is ParameterizedType)
            return "Fail: should be array of parameterized type, but was $g (${g.javaClass}). g !is GenericArrayType is ${g !is GenericArrayType}."

        val h = A<String>::baz.returnType.javaType
        if (h !is GenericArrayType || h.genericComponentType !is TypeVariable<*>)
            return "Fail: should be array of type variable, but was $h (${h.javaClass})"

        return "OK"
    }
}