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
import org.jacodb.api.JcBoundedWildcard
import org.jacodb.api.JcClassType
import org.jacodb.api.JcTypeVariable
import org.jacodb.testing.types.WildcardBounds.DirectBound
import org.jacodb.testing.types.WildcardBounds.DirectBoundString
import org.jacodb.testing.types.WildcardBounds.WildcardLowerBound
import org.jacodb.testing.types.WildcardBounds.WildcardLowerBoundString
import org.jacodb.testing.types.WildcardBounds.WildcardUpperBound
import org.jacodb.testing.types.WildcardBounds.WildcardUpperBoundString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WildcardTypesTest : BaseTypesTest() {

    @Test
    fun `direct types`() {
        runBlocking {
            val bounded = findType<DirectBound<*>>()
            with(bounded.fields.first()) {
                assertEquals("field", name)
                with(fieldType.assertIs<JcClassType>()) {
                    assertEquals("java.util.List<T>", typeName)
                }
            }
        }
    }

    @Test
    fun `resolved direct types`() {
        runBlocking {
            val bounded = findType<DirectBoundString>()
            with(bounded.superType!!.fields.first()) {
                assertEquals("field", name)
                with(fieldType.assertIs<JcClassType>()) {
                    assertEquals("java.util.List<java.lang.String>", typeName)
                }
            }
        }
    }

    @Test
    fun `upper bound types`() {
        runBlocking {
            val bounded = findType<WildcardUpperBound<*>>()
            with(bounded.fields.first()) {
                assertEquals("field", name)
                with(fieldType.assertIs<JcClassType>()) {
                    assertEquals("java.util.List<? extends T>", typeName)
                    with(typeArguments.first().assertIs<JcBoundedWildcard>()) {
                        upperBounds.first().assertIs<JcTypeVariable>()
                    }
                }
            }
        }
    }

    @Test
    fun `resolved upper bound types`() {
        runBlocking {
            val bounded = findType<WildcardUpperBoundString>()
            with(bounded.superType!!.fields.first()) {
                assertEquals("field", name)
                with(fieldType.assertIs<JcClassType>()) {
                    assertEquals("java.util.List<? extends java.lang.String>", typeName)
                    with(typeArguments.first().assertIs<JcBoundedWildcard>()) {
                        upperBounds.first().assertClassType<String>()
                    }
                }
            }
        }
    }

    @Test
    fun `lower bound types`() {
        runBlocking {
            val bounded = findType<WildcardLowerBound<*>>()
            with(bounded.fields.first()) {
                assertEquals("field", name)
                with(fieldType.assertIs<JcClassType>()) {
                    assertEquals("java.util.List<? super T>", typeName)
                    with(typeArguments.first().assertIs<JcBoundedWildcard>()) {
                        lowerBounds.first().assertIs<JcTypeVariable>()
                    }
                }
            }
        }
    }

    @Test
    fun `resolved lower bound types`() {
        runBlocking {
            val bounded = findType<WildcardLowerBoundString>()
            with(bounded.superType!!.fields.first()) {
                assertEquals("field", name)
                with(fieldType.assertIs<JcClassType>()) {
                    assertEquals("java.util.List<? super java.lang.String>", typeName)
                    with(typeArguments.first().assertIs<JcBoundedWildcard>()) {
                        lowerBounds.first().assertClassType<String>()
                    }
                }
            }
        }
    }
}