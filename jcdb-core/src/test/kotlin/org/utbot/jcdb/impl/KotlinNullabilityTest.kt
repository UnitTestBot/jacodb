package org.utbot.jcdb.impl

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
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
            // receiver -- SomeContainer<SomeContainer<Int>>
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
    fun `Test nullability after instantiation`() = runBlocking {
        val clazz = typeOf<KotlinNullabilityExamples>() as JcClassType
        val paramWithNotNullArg = clazz.declaredMethods.single { it.name == "instantiatedContainer" }.parameters[0]
        val paramWithNullableArg = clazz.declaredMethods.single { it.name == "instantiatedContainer" }.parameters[1]

        val nullableFieldWithNotNullArg = (paramWithNotNullArg.type as JcClassType).fields.single { it.name == "nullableProperty" }
        val notNullFieldWithNotNullArg = (paramWithNotNullArg.type as JcClassType).fields.single { it.name == "notNullProperty" }
        val nullableFieldWithNullableArg = (paramWithNullableArg.type as JcClassType).fields.single { it.name == "nullableProperty" }
        val notNullFieldWithNullableArg = (paramWithNullableArg.type as JcClassType).fields.single { it.name == "notNullProperty" }

        assertTrue(nullableFieldWithNotNullArg.fieldType.nullable)
        assertFalse(notNullFieldWithNotNullArg.fieldType.nullable)
        assertTrue(nullableFieldWithNullableArg.fieldType.nullable)
        assertTrue(notNullFieldWithNullableArg.fieldType.nullable)
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