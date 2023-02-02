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

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.utbot.jacodb.api.JcClassType
import org.utbot.jacodb.api.JcTypeVariable
import java.io.Closeable


class InnerTypesTest : BaseTypesTest() {

    @Test
    fun `inner classes linked to method`() {
        val classWithInners = findType<InnerClasses<*>>()
        val inners = classWithInners.innerTypes
        assertEquals(4, inners.size)
        val methodLinked = inners.first { it.typeName == "org.jacodb.testing.types.InnerClasses<W>.InnerClasses\$1" }
        with(methodLinked.fields) {
            with(first { it.name == "stateT" }) {
                assertEquals("T", (fieldType as JcTypeVariable).symbol)
            }
            with(first { it.name == "stateW" }) {
                assertEquals("W", fieldType.typeName)
            }
        }
    }

    @Test
    fun `get not parameterized inner types`() {
        val innerClasses = findType<InnerClasses<*>>().innerTypes
        assertEquals(4, innerClasses.size)
        with(innerClasses.first { it.typeName.contains("InnerState") }) {
            val fields = fields
            assertEquals(2, fields.size)

            with(fields.first { it.name == "stateW" }) {
                with(fieldType.assertIs<JcTypeVariable>()) {
                    assertEquals("W", symbol)
                }
            }
        }
    }

    @Test
    fun `get inner type linked to parameterized method`() {
        val innerClasses = findType<InnerClasses<*>>().innerTypes
        assertEquals(4, innerClasses.size)
        with(innerClasses.first { it.typeName.contains("1") }) {
            val fields = fields
            assertEquals(4, fields.size)
            interfaces.first().assertClassType<Runnable>()

            with(fields.first { it.name == "stateT" }) {
                assertEquals("stateT", name)
                with(fieldType.assertIs<JcTypeVariable>()) {
                    assertEquals("T", symbol)
                }
            }
            with(fields.first { it.name == "stateW" }) {
                assertEquals("stateW", name)
                with(fieldType.assertIs<JcTypeVariable>()) {
                    assertEquals("W", symbol)
                }
            }

        }
    }

    @Test
    fun `parameterized inner type with parent type parameterization`() {
        with(field("stateString")) {
            fields.first { it.name == "stateW" }.fieldType.assertClassType<String>()
        }

    }

    @Test
    fun `custom parameterization of method overrides outer class parameterization`() {
        with(field("stateString")) {
            with(methods.first { it.name == "method" }) {
                with(returnType.assertIs<JcTypeVariable>()) {
                    assertEquals("W", symbol)
                    assertEquals("java.util.List<java.io.Closeable>", bounds.first().typeName)
                }
            }
        }

    }

    @Test
    fun `custom parameterization of inner type overrides outer class parameterization`() {

        with(field("stateClosable")) {
            with(fields.first { it.name == "stateW" }) {
                fieldType.assertClassType<Closeable>()
            }
            with(methods.first { it.name == "method" }) {
                with(returnType.assertIs<JcTypeVariable>()) {
                    assertEquals("W", symbol)
                    assertEquals("java.util.List<java.lang.Integer>", bounds.first().typeName)
                }
            }
        }

    }

    private fun field(fieldName: String): JcClassType {
        return findType<InnerClasses<*>>().fields.first {
            it.name == fieldName
        }.fieldType.assertIsClass()
    }

}