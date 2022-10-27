package org.utbot.jcdb.impl.types

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.utbot.jcdb.api.JcBoundedWildcard
import org.utbot.jcdb.api.JcClassType
import org.utbot.jcdb.api.JcTypeVariable
import org.utbot.jcdb.impl.types.WildcardBounds.DirectBound
import org.utbot.jcdb.impl.types.WildcardBounds.DirectBoundString
import org.utbot.jcdb.impl.types.WildcardBounds.WildcardLowerBound
import org.utbot.jcdb.impl.types.WildcardBounds.WildcardLowerBoundString
import org.utbot.jcdb.impl.types.WildcardBounds.WildcardUpperBound
import org.utbot.jcdb.impl.types.WildcardBounds.WildcardUpperBoundString

class WildcardTypesTest : BaseTypesTest() {

    @Test
    fun `direct types`() {
        runBlocking {
            val bounded = findClassType<DirectBound<*>>()
            with(bounded.fields.first()) {
                assertEquals("field", name)
                with(fieldType().assertIs<JcClassType>()) {
                    assertEquals("java.util.List<T>", typeName)
                }
            }
        }
    }

    @Test
    fun `resolved direct types`() {
        runBlocking {
            val bounded = findClassType<DirectBoundString>()
            with(bounded.superType!!.fields.first()) {
                assertEquals("field", name)
                with(fieldType().assertIs<JcClassType>()) {
                    assertEquals("java.util.List<java.lang.String>", typeName)
                }
            }
        }
    }

    @Test
    fun `upper bound types`() {
        runBlocking {
            val bounded = findClassType<WildcardUpperBound<*>>()
            with(bounded.fields.first()) {
                assertEquals("field", name)
                with(fieldType().assertIs<JcClassType>()) {
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
            val bounded = findClassType<WildcardUpperBoundString>()
            with(bounded.superType!!.fields.first()) {
                assertEquals("field", name)
                with(fieldType().assertIs<JcClassType>()) {
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
            val bounded = findClassType<WildcardLowerBound<*>>()
            with(bounded.fields.first()) {
                assertEquals("field", name)
                with(fieldType().assertIs<JcClassType>()) {
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
            val bounded = findClassType<WildcardLowerBoundString>()
            with(bounded.superType!!.fields.first()) {
                assertEquals("field", name)
                with(fieldType().assertIs<JcClassType>()) {
                    assertEquals("java.util.List<? super java.lang.String>", typeName)
                    with(typeArguments.first().assertIs<JcBoundedWildcard>()) {
                        lowerBounds.first().assertClassType<String>()
                    }
                }
            }
        }
    }
}