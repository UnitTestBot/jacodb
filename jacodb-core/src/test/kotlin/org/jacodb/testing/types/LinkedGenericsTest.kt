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

package org.jacodb.testing.types

import kotlinx.coroutines.runBlocking
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcTypeVariable
import org.jacodb.api.jvm.ext.findClass
import org.jacodb.testing.types.Generics.LinkedImpl
import org.jacodb.testing.types.Generics.SingleImpl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LinkedGenericsTest : BaseTypesTest() {

    @Test
    fun `linked generics original parametrization`() = runBlocking {
        val partial = findType<LinkedImpl<*>>()
        with(partial.superType!!) {
            with(typeParameters.first()) {
                assertEquals("T", symbol)
                bounds.first().assertClassType<Any>()
            }

            with(typeParameters[1]) {
                assertEquals("W", symbol)
                assertEquals(1, bounds.size)
                assertEquals("java.util.List<T>", bounds[0].typeName)
            }
        }
    }

    @Test
    fun `linked generics current parametrization`() = runBlocking {
        val partial = findType<LinkedImpl<*>>()
        with(partial.superType!!) {
            with(typeArguments[0]) {
                assertClassType<String>()
            }

            with(typeArguments[1]) {
                this as JcTypeVariable
                assertEquals("W", symbol)
                assertEquals(1, bounds.size)
                assertEquals("java.util.List<java.lang.String>", bounds[0].typeName)
            }
        }
    }

    @Test
    fun `linked generics fields parametrization`() = runBlocking {
        val partial = findType<LinkedImpl<*>>()
        with(partial.superType!!) {
            val fields = fields
            assertEquals(3, fields.size)

            with(fields.first { it.name == "state" }) {
                assertEquals("state", name)
                fieldType.assertClassType<String>()
            }
            with(fields.first { it.name == "stateW" }) {
                assertEquals(
                    "java.util.List<java.lang.String>",
                    (fieldType as JcTypeVariable).bounds.first().typeName
                )
            }
            with(fields.first { it.name == "stateListW" }) {
                val resolvedType = fieldType.assertIsClass()
                assertEquals(cp.findClass<List<*>>(), resolvedType.jcClass)
                val shouldBeW = (resolvedType.typeArguments.first() as JcTypeVariable)
                assertEquals("java.util.List<java.lang.String>", shouldBeW.bounds.first().typeName)
            }
        }
    }


    @Test
    fun `generics applied for fields of super types`() {
        runBlocking {
            val superFooType = findType<SingleImpl>()
            with(superFooType.superType.assertIsClass()) {
                val fields = fields
                assertEquals(2, fields.size)

                with(fields.first()) {
                    assertEquals("state", name)
                    fieldType.assertClassType<String>()
                }
                with(fields.get(1)) {
                    assertEquals("stateList", name)
                    with(fieldType.assertIsClass()) {
                        assertEquals("java.util.ArrayList<java.lang.String>", typeName)
                    }
                }
            }
        }
    }

    @Test
    fun `direct generics from child types applied to methods`() {
        runBlocking {
            val superFooType = findType<SingleImpl>()
            val superType = superFooType.superType.assertIsClass()
            val methods = superType.declaredMethods.filterNot { it.method.isConstructor }
            assertEquals(2, methods.size)

            with(methods.first { it.method.name == "run1" }) {
                returnType.assertClassType<String>()
                parameters.first().type.assertClassType<String>()
            }
        }
    }

    @Test
    fun `custom generics from child types applied to methods`() {
        runBlocking {
            val superFooType = findType<SingleImpl>()
            val superType = superFooType.superType.assertIsClass()
            val methods = superType.declaredMethods.filterNot { it.method.isConstructor }
            assertEquals(2, methods.size)

            with(methods.first { it.method.name == "run2" }) {
                val params = parameters.first()
                val w = typeParameters.first()

                val bound = (params.type as JcClassType).typeArguments.first()
                assertEquals("W", (bound as? JcTypeVariable)?.symbol)
                assertEquals("W", w.symbol)
                bound as JcTypeVariable
                bound.bounds.first().assertClassType<String>()
            }
        }
    }

}