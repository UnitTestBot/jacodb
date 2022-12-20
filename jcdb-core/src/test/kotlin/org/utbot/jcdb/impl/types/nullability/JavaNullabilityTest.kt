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

package org.utbot.jcdb.impl.types.nullability

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.utbot.jcdb.api.JcClassType
import org.utbot.jcdb.impl.types.BaseTypesTest
import org.utbot.jcdb.impl.usages.NullAnnotationExamples

@Disabled("Type annotations are not supported")
class JavaNullabilityTest : BaseTypesTest() {

    @Test
    fun `Test nullability for simple types Java`() = runBlocking {
        val clazz = findType<NullAnnotationExamples>()
        val params = clazz.declaredMethods.single { it.name == "nullableMethod" }.parameters
        val actualNullability = params.map { it.type.nullabilityTree }
        val expectedNullability = listOf(
            // @Nullable String
            buildTree(true),

            // @NotNull String
            buildTree(false),

            // SomeContainer<@NotNull String>
            buildTree(null) {
                +buildTree(false)
            }
        )

        assertEquals(expectedNullability, actualNullability)
    }

    @Test
    fun `Test nullability on wildcards Java`() = runBlocking {
        val clazz = findType<NullAnnotationExamples>()
        val returnType = clazz.declaredMethods.single { it.name == "wildcard" }.returnType
        val actualNullability = returnType.nullabilityTree
        val expectedNullability =
            buildTree(true) {
                +buildTree(false)
            }

        assertEquals(expectedNullability, actualNullability)
    }

    @Test
    fun `Test nullability after substitution with NotNull type or type of undefined nullability Java`() = runBlocking {
        val clazz = findType<NullAnnotationExamples>()
        val containerOfUndefined = clazz.declaredFields.single { it.name == "containerOfUndefined" }
        val containerOfNotNull = clazz.declaredFields.single { it.name == "containerOfNotNull" }

        val containerOfUndefinedFieldsNullability = (containerOfUndefined.fieldType as JcClassType)
            .fields
            .sortedBy { it.name }
            .map { it.fieldType.nullabilityTree }

        val containerOfNotNullFieldsNullability = (containerOfNotNull.fieldType as JcClassType)
            .fields
            .sortedBy { it.name }
            .map { it.fieldType.nullabilityTree }

        // E -> String or E -> @NotNull String
        val expectedNullability = listOf(
            // List<@NotNull E>
            buildTree(null) {
                +buildTree(false)
            },

            // List<@Nullable E>
            buildTree(null) {
                +buildTree(true)
            },

            // List<E>
            buildTree(null) {
                +buildTree(null)
            },

            // @NotNull E, @Nullable E, E
            buildTree(false), buildTree(true), buildTree(null)
        )

        assertEquals(expectedNullability, containerOfNotNullFieldsNullability)
        assertEquals(expectedNullability, containerOfUndefinedFieldsNullability)
    }

    @Test
    fun `Test nullability after substitution with nullable type Java`() = runBlocking {
        val clazz = findType<NullAnnotationExamples>()
        val containerOfNullable = clazz.declaredFields.single { it.name == "containerOfNullable" }

        val containerOfNullableFieldsNullability = (containerOfNullable.fieldType as JcClassType)
            .fields
            .sortedBy { it.name }
            .map { it.fieldType.nullabilityTree }

        // E -> @Nullable String
        val expectedNullability = listOf(
            // List<@NotNull E>
            buildTree(null) {
                +buildTree(false)
            },

            // List<@Nullable E>
            buildTree(null) {
                +buildTree(true)
            },

            // List<E>
            buildTree(null) {
                +buildTree(true)
            },

            // @NotNull E, @Nullable E, E
            buildTree(false), buildTree(true), buildTree(true)
        )

        assertEquals(expectedNullability, containerOfNullableFieldsNullability)
    }
}