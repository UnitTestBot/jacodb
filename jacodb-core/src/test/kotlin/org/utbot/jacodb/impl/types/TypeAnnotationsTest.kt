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

package org.utbot.jacodb.impl.types

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.utbot.jacodb.api.JcAnnotation
import org.utbot.jacodb.api.JcArrayType
import org.utbot.jacodb.api.JcBoundedWildcard
import org.utbot.jacodb.api.JcClassType
import org.utbot.jacodb.impl.usages.NullAnnotationExamples

class TypeAnnotationsTest: BaseTypesTest() {
    @Test
    fun `type annotations on fields`() = runBlocking {
        val clazz = findType<NullAnnotationExamples>()

        val expectedAnnotations = mapOf(
            "refNullable" to listOf(),
            "refNotNull" to listOf(jbNotNull),
            "explicitlyNullable" to listOf(jbNullable),
        )

        val fields = clazz.declaredFields.filter { it.name in expectedAnnotations.keys }
        val actualAnnotations = fields.associate { it.name to it.fieldType.annotations.simplified }

        assertEquals(expectedAnnotations, actualAnnotations)
    }

    @Test
    fun `type annotations on method parameters`() = runBlocking {
        val clazz = findType<NullAnnotationExamples>()
        val nullableMethod = clazz.declaredMethods.single { it.name == "nullableMethod" }

        val expectedAnnotations = listOf(listOf(jbNullable), listOf(jbNotNull), listOf())
        val actualParameterAnnotations = nullableMethod.parameters.map { it.type.annotations.simplified }

        assertEquals(expectedAnnotations, actualParameterAnnotations)
    }

    @Test
    fun `type annotations on method return value`() = runBlocking {
        val clazz = findType<NullAnnotationExamples>()

        val notNullMethod = clazz.declaredMethods.single { it.name == "notNullMethod" }
        assertEquals(listOf(jbNotNull), notNullMethod.method.annotations.simplified)
    }

    @Test
    fun `type annotations on wildcard bounds`() = runBlocking {
        val clazz = findType<NullAnnotationExamples>()

        val notNullMethod = clazz.declaredMethods.single { it.name == "wildcard" }
        val actualAnnotations = ((notNullMethod.returnType as JcClassTypeImpl).typeArguments[0] as JcBoundedWildcard)
            .upperBounds[0]
            .annotations
            .simplified
        assertEquals(listOf(jbNotNull), actualAnnotations)
    }

    @Test
    fun `type annotations on inner types`() = runBlocking {
        val clazz = findType<NullAnnotationExamples>()

        val innerMethod = clazz.declaredMethods.single { it.name == "inner" }
        val actualAnnotationsOnInner = innerMethod.returnType.annotations.simplified
        val actualAnnotationsOnOuter = (innerMethod.returnType as JcClassType)
            .outerType!!
            .typeArguments[0]
            .annotations
            .simplified

        assertEquals(listOf(jbNullable), actualAnnotationsOnInner)
        assertEquals(listOf(jbNotNull), actualAnnotationsOnOuter)
    }

    @Test
    fun `type annotations on array types`() = runBlocking {
        val clazz = findType<NullAnnotationExamples>()

        val arrayMethod = clazz.declaredMethods.single { it.name == "array" }
        val actualAnnotationsOnArray = arrayMethod.returnType.annotations.simplified
        val actualAnnotationsOnArrayElement = (arrayMethod.returnType as JcArrayType)
            .elementType
            .annotations
            .simplified

        assertEquals(listOf(jbNullable), actualAnnotationsOnArray)
        assertEquals(listOf(jbNotNull), actualAnnotationsOnArrayElement)
    }

    private val jbNullable = "org.jetbrains.annotations.Nullable"
    private val jbNotNull  = "org.jetbrains.annotations.NotNull"
    private val Iterable<JcAnnotation>.simplified get() = map { it.name }
}