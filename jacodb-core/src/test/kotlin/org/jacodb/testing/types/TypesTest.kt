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

import org.jacodb.api.JcArrayType
import org.jacodb.api.JcClassType
import org.jacodb.api.JcPrimitiveType
import org.jacodb.api.JcTypeVariable
import org.jacodb.api.ext.findClass
import org.jacodb.api.ext.findMethodOrNull
import org.jacodb.api.ext.humanReadableSignature
import org.jacodb.api.ext.toType
import org.jacodb.impl.types.JcClassTypeImpl
import org.jacodb.impl.types.signature.JvmClassRefType
import org.jacodb.impl.types.substition.JcSubstitutorImpl
import org.jacodb.testing.Example
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.InputStream

class TypesTest : BaseTypesTest() {

    @Test
    fun `primitive and array types`() {
        val primitiveAndArrays = findType<PrimitiveAndArrays>()
        val fields = primitiveAndArrays.declaredFields
        assertEquals(2, fields.size)

        with(fields.first()) {
            assertTrue(fieldType is JcPrimitiveType)
            assertEquals("value", name)
            assertEquals("int", fieldType.typeName)
        }
        with(fields.get(1)) {
            assertTrue(fieldType is JcArrayType)
            assertEquals("intArray", name)
            assertEquals("int[]", fieldType.typeName)
        }


        val methods = primitiveAndArrays.declaredMethods.filterNot { it.method.isConstructor }
        with(methods.first()) {
            assertTrue(returnType is JcArrayType)
            assertEquals("int[]", returnType.typeName)

            assertEquals(1, parameters.size)
            with(parameters.get(0)) {
                assertTrue(type is JcArrayType)
                assertEquals("java.lang.String[]", type.typeName)
            }
        }
    }

    @Test
    fun `parameters test`() {
        val type = findType<Example>()
        val actualParameters = type.declaredMethods.single { it.name == "f" }.parameters
        assertEquals(listOf("notNullable", "nullable"), actualParameters.map { it.name })
        assertEquals(false, actualParameters.first().type.nullable)
        assertEquals(true, actualParameters.get(1).type.nullable)
    }

    @Test
    fun `inner-outer classes recursion`() {
        cp.findClass("com.zaxxer.hikari.pool.HikariPool").toType().interfaces
        cp.findClass("com.zaxxer.hikari.util.ConcurrentBag").toType()
    }

    @Test
    fun `kotlin private inline fun`() {
        val type = cp.findClass("kotlin.text.RegexKt\$fromInt\$1\$1").toType().interfaces.single().typeArguments.first()
        type as JcTypeVariable
        assertTrue(type.bounds.isNotEmpty())
    }

    @Test
    fun `interfaces types test`() {
        val sessionCacheVisitorType =
            cp.findClass("sun.security.ssl.SSLSessionContextImpl\$SessionCacheVisitor").toType()
        val cacheVisitorType = sessionCacheVisitorType.interfaces.first()
        val firstParam = cacheVisitorType.typeArguments.first()

        assertEquals(firstParam.jcClass, cp.findClass("sun.security.ssl.SessionId"))

        val secondParam = cacheVisitorType.typeArguments[1]
        assertEquals(secondParam.jcClass, cp.findClass("sun.security.ssl.SSLSessionImpl"))
    }

    private val listClass = List::class.java.name

    @Test
    fun `raw types equality`() {
        val rawType1 = rawList()
        val rawType2 = rawList()
        assertEquals(rawType1, rawType2)
    }

    @Test
    fun `parametrized types equality`() {
        val rawType = rawList()
        val type1 = listType<String>()
        val type2 = listType<String>()
        assertNotEquals(rawType, type1)
        assertNotEquals(rawType, type2)

        assertEquals(type1, type2)
    }

    @Test
    fun `parametrized typed method equality`() {

        val objectList = listType<Any>()
        val stringList1 = listType<String>()
        val stringList2 = listType<String>()
        val isList = listType<InputStream>()

        assertEquals(stringList1.iterator, stringList2.iterator)
        assertNotEquals(isList.iterator, stringList1.iterator)
        assertNotEquals(objectList.iterator, stringList1.iterator)
    }

    @Test
    fun `humanReadableSignature should work`() {
        val type = listType<String>()
        assertEquals(
            "java.util.List<java.lang.String>#isEmpty():boolean",
            type.declaredMethods.first { it.name == "isEmpty" }.humanReadableSignature
        )
    }

    private inline fun <reified T> listType(raw: Boolean = false): JcClassType {
        val elementName = T::class.java.name
        return JcClassTypeImpl(
            cp, listClass, null,
            when {
                raw -> emptyList()
                else -> listOf(JvmClassRefType(elementName, false, emptyList()))
            }, false, emptyList()
        )
    }

    private fun rawList(): JcClassType {
        return JcClassTypeImpl(cp, listClass, null, JcSubstitutorImpl.empty, false, emptyList())
    }


    private val JcClassType.iterator get() = findMethodOrNull { it.name == "iterator" && it.parameters.isEmpty() }

}