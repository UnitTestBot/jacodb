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
import org.utbot.jcdb.api.JcType
import org.utbot.jcdb.api.ext.findTypeOrNull
import org.utbot.jcdb.impl.BaseTest
import org.utbot.jcdb.impl.KotlinNullabilityExamples
import org.utbot.jcdb.impl.WithDB
import org.utbot.jcdb.impl.usages.NullAnnotationExamples

class KotlinJavaInteropNullabilityTest : BaseTest() {
    companion object : WithDB()

    @Test
    //@Disabled("as we can")
    fun `Test nullability after substitution of Kotlin T with type of undefined nullability Java`() = runBlocking {
        val clazz = typeOf<NullAnnotationExamples>() as JcClassType
        val containerOfUndefined = clazz.declaredFields.single { it.name == "ktContainerOfUndefined" }

        val containerOfUndefinedFieldsNullability = (containerOfUndefined.fieldType as JcClassType)
            .fields
            .sortedBy { it.name }
            .map { it.fieldType.nullabilityTree }

        // E -> String
        val expectedNullability = listOf(
            // List<E>
            buildTree(false) {
                +buildTree(null)
            },

            // List<E?>
            buildTree(false) {
                +buildTree(true)
            },

            // E
            buildTree(null),

            // E?
            buildTree(true)
        )

        assertEquals(expectedNullability, containerOfUndefinedFieldsNullability)
    }

    @Test
    @Disabled("Type annotations are not supported")
    fun `Test nullability after substitution of Kotlin T with notNull type Java`() = runBlocking {
        val clazz = typeOf<NullAnnotationExamples>() as JcClassType
        val containerOfNotNull = clazz.declaredFields.single { it.name == "ktContainerOfNotNull" }

        val containerOfNotNullFields = (containerOfNotNull.fieldType as JcClassType)
            .fields
            .sortedBy { it.name }
            .map { it.fieldType.nullabilityTree }

        // E -> @NotNull String
        val expectedNullability = listOf(
            // List<E>
            buildTree(false) {
                +buildTree(false)
            },

            // List<E?>
            buildTree(false) {
                +buildTree(true)
            },

            // E
            buildTree(false),

            // E?
            buildTree(true)
        )

        assertEquals(expectedNullability, containerOfNotNullFields)
    }

    @Test
    @Disabled("Type annotations are not supported")
    fun `Test nullability after substitution of Kotlin T with nullable type Java`() = runBlocking {
        val clazz = typeOf<NullAnnotationExamples>() as JcClassType
        val containerOfNotNull = clazz.declaredFields.single { it.name == "ktContainerOfNullable" }

        val containerOfNotNullFields = (containerOfNotNull.fieldType as JcClassType)
            .fields
            .sortedBy { it.name }
            .map { it.fieldType.nullabilityTree }

        // E -> @Nullable String
        val expectedNullability = listOf(
            // List<E>
            buildTree(false) {
                +buildTree(true)
            },

            // List<E?>
            buildTree(false) {
                +buildTree(true)
            },

            // E
            buildTree(true),

            // E?
            buildTree(true)
        )

        assertEquals(expectedNullability, containerOfNotNullFields)
    }

    @Test
    @Disabled("Type annotations are not supported")
    fun `Test nullability after substitution of Java T with nullable type Kotlin`() = runBlocking {
        val clazz = typeOf<KotlinNullabilityExamples>() as JcClassType
        val containerOfNullable = clazz.declaredFields.single { it.name == "javaContainerOfNullable" }

        val containerOfNullableFields = (containerOfNullable.fieldType as JcClassType)
            .fields
            .sortedBy { it.name }
            .map { it.fieldType.nullabilityTree }

        // E -> String?
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

        assertEquals(expectedNullability, containerOfNullableFields)
    }

    @Test
    @Disabled("Type annotations are not supported")
    fun `Test nullability after substitution of Java T with notNull type Kotlin`() = runBlocking {
        val clazz = typeOf<KotlinNullabilityExamples>() as JcClassType
        val containerOfNotNull = clazz.declaredFields.single { it.name == "javaContainerOfNotNull" }

        val containerOfNotNullFields = (containerOfNotNull.fieldType as JcClassType)
            .fields
            .sortedBy { it.name }
            .map { it.fieldType.nullabilityTree }

        // E -> String
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

        assertEquals(expectedNullability, containerOfNotNullFields)
    }

    private inline fun <reified T> typeOf(): JcType {
        return cp.findTypeOrNull<T>() ?: throw IllegalStateException("Type ${T::class.java.name} not found")
    }
}