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
package org.utbot.jcdb.impl

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.utbot.jcdb.api.JcClassType
import org.utbot.jcdb.api.JcType
import org.utbot.jcdb.api.ext.findTypeOrNull
import org.utbot.jcdb.api.isNullable

class SimpleNullabilityTest:  BaseTest() {

    companion object : WithDB()

    @Test
    fun `Test field nullability`() = runBlocking {
        val clazz = typeOf<NullableExamples>() as JcClassType

        val expectedNullability = mapOf(
            "refNullable" to true,
            "refNotNull" to false,
            "primitiveNullable" to true,
            "primitiveNotNull" to false,
        )

        val actualFieldNullability = clazz.declaredFields.associate { it.name to it.field.isNullable }
        val actualFieldTypeNullability = clazz.declaredFields.associate { it.name to it.fieldType.nullable }

        assertEquals(expectedNullability, actualFieldNullability)
        assertEquals(expectedNullability, actualFieldTypeNullability)
    }

    @Test
    fun `Test method parameter isNullable`() = runBlocking {
        val clazz = typeOf<NullableExamples>() as JcClassType
        val nullableMethod = clazz.declaredMethods.single { it.name == "nullableMethod" }

        val expectedNullability = listOf(true, false)
        val actualParameterNullability = nullableMethod.parameters.map { it.nullable }
        val actualParameterTypeNullability = nullableMethod.parameters.map { it.type.nullable }

        assertEquals(expectedNullability, actualParameterNullability)
        assertEquals(expectedNullability, actualParameterTypeNullability)
    }

    @Test
    fun `Test method isNullable`() = runBlocking {
        val clazz = typeOf<NullableExamples>() as JcClassType

        val nullableMethod = clazz.declaredMethods.single { it.name == "nullableMethod" }
        assertTrue(nullableMethod.method.isNullable)
        assertTrue(nullableMethod.returnType.nullable)

        val notNullMethod = clazz.declaredMethods.single { it.name == "notNullMethod" }
        assertFalse(notNullMethod.method.isNullable)
        assertFalse(notNullMethod.returnType.nullable)
    }

    private inline fun <reified T> typeOf(): JcType {
        return cp.findTypeOrNull<T>() ?: throw IllegalStateException("Type ${T::class.java.name} not found")
    }
}