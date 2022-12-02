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

package org.utbot.jcdb.impl

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.utbot.jcdb.api.JcArrayType
import org.utbot.jcdb.api.JcBoundedWildcard
import org.utbot.jcdb.api.JcClassType
import org.utbot.jcdb.api.JcType
import org.utbot.jcdb.api.JcTypeVariable
import org.utbot.jcdb.api.JcTypeVariableDeclaration
import org.utbot.jcdb.api.JcUnboundWildcard
import org.utbot.jcdb.api.ext.findTypeOrNull

class KotlinNullabilityTest : BaseTest() {
    companion object : WithDB()

    @Test
    fun `Test nullability for simple generics`() = runBlocking {
        val clazz = typeOf<KotlinNullabilityExamples>() as JcClassType
        val params = clazz.declaredMethods.single { it.name == "simpleGenerics" }.parameters
        val actualNullability = params.map { it.type.nullabilityTree }
        val expectedNullability = listOf(
            // SomeContainer<SomeContainer<Int>>
            buildTree(false) {
                +buildTree(false) {
                    +buildTree(false)
                }
            },

            // SomeContainer<SomeContainer<Int?>>
            buildTree(false) {
                +buildTree(false) {
                    +buildTree(true)
                }
            },

            // SomeContainer<SomeContainer<Int>?>
            buildTree(false) {
                +buildTree(true) {
                    +buildTree(false)
                }
            }
        )

        assertEquals(expectedNullability, actualNullability)
    }

    @Test
    fun `Test nullability for extension function`() = runBlocking {
        val clazz = typeOf<KotlinNullabilityExamples>() as JcClassType
        val actualNullability = clazz.declaredMethods.single { it.name == "extensionFunction" }
            .parameters.single()
            .type
            .nullabilityTree

        // SomeContainer<SomeContainer<Int?>?>
        val expectedNullability = buildTree(false) {
            +buildTree(true) {
                +buildTree(true)
            }
        }

        assertEquals(expectedNullability, actualNullability)
    }

    @Test
    fun `Test nullability for generics with projections`() = runBlocking {
        val clazz = typeOf<KotlinNullabilityExamples>() as JcClassType
        val params = clazz.declaredMethods.single { it.name == "genericsWithProjection" }.parameters
        val actualNullability = params.map { it.type.nullabilityTree }
        val expectedNullability = listOf(
            // SomeContainer<out String?>
            buildTree(false) {
                +buildTree(true)
            },

            // SomeContainer<in String>
            buildTree(false) {
                +buildTree(false)
            },
        )

        assertEquals(expectedNullability, actualNullability)
    }

    @Test
    fun `Test nullability on type parameters`() = runBlocking {
        val clazz = typeOf<KotlinNullabilityExamples>() as JcClassType
        val params = clazz.declaredMethods.single { it.name == "typeVariableParameters" }.parameters
        val actualNullability = params.map { it.type.nullable }
        val expectedNullability = listOf(false, true) // T, T?

        assertEquals(expectedNullability, actualNullability)
    }

    @Test
    fun `Test nullability on type variable declarations`() = runBlocking {
        val clazz = typeOf<KotlinNullabilityExamples>() as JcClassType
        val params = clazz.declaredMethods.single { it.name == "typeVariableDeclarations" }.typeParameters
        val actualNullability = params.map { it.bounds.single().nullabilityTree }

        val expectedNullability = listOf(
            // List<Int?>
            buildTree(false) {
                +buildTree(true)
            },

            // List<Int>?
            buildTree(true) {
                +buildTree(false)
            },
        )

        assertEquals(expectedNullability, actualNullability)
    }

    @Test
    fun `Test nullability after substitution with notNull type`() = runBlocking {
        val clazz = typeOf<KotlinNullabilityExamples>() as JcClassType
        val param = clazz.declaredMethods.single { it.name == "instantiatedContainer" }.parameters[0]

        val fieldsNullability = (param.type as JcClassType)
            .fields
            .sortedBy { it.name }
            .map { it.fieldType.nullabilityTree }

        // E -> String
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

        assertEquals(expectedNullability, fieldsNullability)
    }

    @Test
    fun `Test nullability after substitution with nullable type`() = runBlocking {
        val clazz = typeOf<KotlinNullabilityExamples>() as JcClassType
        val param = clazz.declaredMethods.single { it.name == "instantiatedContainer" }.parameters[1]

        val fieldsNullability = (param.type as JcClassType)
            .fields
            .sortedBy { it.name }
            .map { it.fieldType.nullabilityTree }

        // E -> String?
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

        assertEquals(expectedNullability, fieldsNullability)
    }

    @Test
    fun `Test nullability after passing nullable type through chain of notnull type variables`() = runBlocking {
        val clazz = typeOf<KotlinNullabilityExamples>() as JcClassType
        val fieldType = clazz.declaredFields.single { it.name == "someContainerProducer" }.fieldType
        val innerMethodType = (fieldType as JcClassType).declaredMethods.single { it.name == "produceContainer" }.returnType

        val fieldsNullability = (innerMethodType as JcClassType)
            .fields
            .sortedBy { it.name }
            .map { it.fieldType.nullabilityTree }

        // P -> Int?, E -> P
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

        assertEquals(expectedNullability, fieldsNullability)
    }



    private data class TypeNullabilityTree(val isNullable: Boolean, val innerTypes: List<TypeNullabilityTree>)

    private class TreeBuilder(private val isNullable: Boolean) {
        private val innerTypes: MutableList<TypeNullabilityTree> = mutableListOf()

        operator fun TypeNullabilityTree.unaryPlus() {
            this@TreeBuilder.innerTypes.add(this)
        }

        fun build(): TypeNullabilityTree = TypeNullabilityTree(isNullable, innerTypes)
    }

    private fun buildTree(isNullable: Boolean, actions: TreeBuilder.() -> Unit = {}) =
        TreeBuilder(isNullable).apply(actions).build()

    private val JcType.nullabilityTree: TypeNullabilityTree get() {
        return when (this) {
            is JcClassType -> TypeNullabilityTree(nullable, typeArguments.map { it.nullabilityTree })
            is JcArrayType -> TypeNullabilityTree(nullable, listOf(elementType.nullabilityTree))
            is JcBoundedWildcard -> (upperBounds + lowerBounds).map { it.nullabilityTree }.single()  // For bounded wildcard we are interested only in nullability of bound, not of the wildcard itself
            is JcUnboundWildcard -> TypeNullabilityTree(nullable, listOf())
            is JcTypeVariable -> TypeNullabilityTree(nullable, bounds.map { it.nullabilityTree })
            is JcTypeVariableDeclaration -> TypeNullabilityTree(nullable, bounds.map { it.nullabilityTree })
            else -> TypeNullabilityTree(nullable, listOf())
        }
    }

    private inline fun <reified T> typeOf(): JcType {
        return cp.findTypeOrNull<T>() ?: throw IllegalStateException("Type ${T::class.java.name} not found")
    }
}