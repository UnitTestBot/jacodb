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

package org.utbot.jcdb.impl.types

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.utbot.jcdb.api.JcMethod
import org.utbot.jcdb.api.JcTypedMethod
import org.utbot.jcdb.api.constructors
import org.utbot.jcdb.api.ext.findClass
import org.utbot.jcdb.api.isConstructor
import org.utbot.jcdb.api.isSynthetic
import org.utbot.jcdb.api.methods
import org.utbot.jcdb.api.toType
import org.utbot.jcdb.impl.hierarchies.Overrides
import java.io.Closeable

class OverridesTest : BaseTypesTest() {

    @Test
    fun `types methods should respect overrides `() {
        val impl1 = cp.findClass<Overrides.Impl1>().toType()
        assertEquals(1, impl1.constructors.size)
        assertEquals(2, impl1.declaredMethods.typedNotSynthetic().size)
        with(impl1.methods.typedNotSynthetic().filter { it.name == "runMain" }) {
            assertEquals(2, size)
            assertTrue(any { it.parameters.first().type.typeName == String::class.java.name })
            assertTrue(any { it.parameters.first().type.typeName == "java.util.List<java.lang.String>" })
        }

        val impl2 = cp.findClass<Overrides.Impl2>().toType()
        assertEquals(1, impl2.constructors.size)
        assertEquals(2, impl2.declaredMethods.typedNotSynthetic().size)
        with(impl2.methods.typedNotSynthetic().filter { it.name == "runMain" }) {
            assertEquals(3, size)
            assertTrue(any { it.parameters.first().type.typeName == Closeable::class.java.name })
            assertTrue(any { it.parameters.first().type.typeName == String::class.java.name })
            assertTrue(any { it.parameters.first().type.typeName == "java.util.List<java.lang.String>" })
        }
    }

    @Test
    fun `types fields should respect overrides and visibility`() {
        val impl1 = cp.findClass<Overrides.Impl1>().toType()
        assertEquals(0, impl1.declaredFields.size)
        with(impl1.fields) {
            assertEquals(2, size)
            first { it.name == "protectedMain" }.fieldType.assertClassType<String>()
            first { it.name == "publicMain" }.fieldType.assertClassType<String>()
        }

        val impl2 = cp.findClass<Overrides.Impl2>().toType()
        assertEquals(3, impl2.declaredFields.size)
        with(impl2.fields) {
            assertEquals(5, size)
            assertEquals("java.util.List<java.io.Closeable>", first { it.name == "publicMain1" }.fieldType.typeName)
            assertEquals("java.util.List<java.io.Closeable>", first { it.name == "protectedMain1" }.fieldType.typeName)

            with(first { it.name == "main" }) {
                fieldType.assertClassType<String>()
                enclosingType.assertClassType<Overrides.Impl2>()
            }
            first { it.name == "publicMain" }.fieldType.assertClassType<String>()
            first { it.name == "protectedMain" }.fieldType.assertClassType<String>()
        }
    }

    @Test
    fun `class methods should respect overrides`() {
        val impl1 = cp.findClass<Overrides.Impl1>()
        assertEquals(1, impl1.constructors.size)
        assertEquals(2, impl1.declaredMethods.notSynthetic().size)
        assertEquals(2, impl1.methods.notSynthetic().filter { it.name == "runMain" }.size)

        val impl2 = cp.findClass<Overrides.Impl2>()
        assertEquals(1, impl2.constructors.size)
        assertEquals(2, impl2.declaredMethods.notSynthetic().size)
        assertEquals(3, impl2.methods.notSynthetic().filter { it.name == "runMain" }.size)
    }

    private fun List<JcMethod>.notSynthetic() = filterNot { it.isSynthetic || it.isConstructor }

    private fun List<JcTypedMethod>.typedNotSynthetic() = filterNot { it.method.isSynthetic || it.method.isConstructor }

}