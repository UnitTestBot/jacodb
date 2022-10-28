package org.utbot.jcdb.impl.types

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.utbot.jcdb.api.JcClassType
import org.utbot.jcdb.api.JcTypeVariable
import org.utbot.jcdb.api.JcTypedField
import org.utbot.jcdb.api.JcTypedMethod
import org.utbot.jcdb.impl.types.MultipleParametrization.SuperTest1
import org.utbot.jcdb.impl.types.MultipleParametrization.SuperTest2
import org.utbot.jcdb.impl.types.MultipleParametrization.SuperTest3
import kotlin.reflect.KFunction2
import kotlin.reflect.KMutableProperty1

class MultiParametersTest : BaseTypesTest() {

    private val finalW = "java.util.ArrayList<java.lang.String>"
    private val finalZ = "java.util.ArrayList<java.util.ArrayList<java.lang.String>>"

    @Test
    fun `first level of parameterization fields`() {
        runBlocking {
            val test1 = findClassType<SuperTest1<*, *, *>>()
            with(test1.field(SuperTest1<*, *, *>::stateT)) {
                assertEquals("T", (fieldType as JcTypeVariable).symbol)
            }
            with(test1.field(SuperTest1<*, *, *>::stateW)) {
                assertEquals("W", (fieldType as JcTypeVariable).symbol)
            }
            with(test1.field(SuperTest1<*, *, *>::stateZ)) {
                assertEquals("Z", (fieldType as JcTypeVariable).symbol)
            }
        }
    }

    @Test
    fun `second level of parameterization fields`() {
        runBlocking {
            val test2 = findClassType<SuperTest2<*, *>>()
            with(test2.field(SuperTest1<*, *, *>::stateT)) {
                fieldType.assertClassType<String>()
            }
            with(test2.field(SuperTest1<*, *, *>::stateW)) {
                val variable = fieldType as JcTypeVariable
                assertEquals("W", variable.symbol)
            }
            with(test2.field(SuperTest1<*, *, *>::stateZ)) {
                assertEquals("Z", (fieldType as JcTypeVariable).symbol)
            }
        }
    }

    @Test
    fun `third level of parameterization fields`() {
        runBlocking {
            val test2 = findClassType<SuperTest3>()
            with(test2.field(SuperTest1<*, *, *>::stateT)) {
                fieldType.assertClassType<String>()
            }
            with(test2.field(SuperTest1<*, *, *>::stateW)) {
                val variable = fieldType
                assertEquals(finalW, variable.typeName)
            }
            with(test2.field(SuperTest1<*, *, *>::stateZ)) {
                val variable = fieldType
                assertEquals(finalZ, variable.typeName)
            }
        }
    }

    @Test
    fun `first level of parameterization methods`() {
        runBlocking {
            val test1 = findClassType<SuperTest1<*, *, *>>()
            with(test1.method(SuperTest1<*, *, *>::runT)) {
                assertEquals("T", (returnType as JcTypeVariable).symbol)
                assertEquals("T", (parameters.first().type as JcTypeVariable).symbol)
            }
            with(test1.method(SuperTest1<*, *, *>::runW)) {
                assertEquals("W", (returnType as JcTypeVariable).symbol)
                assertEquals("W", (parameters.first().type as JcTypeVariable).symbol)
            }
            with(test1.method(SuperTest1<*, *, *>::runZ)) {
                assertEquals("Z", (returnType as JcTypeVariable).symbol)
                assertEquals("Z", (parameters.first().type as JcTypeVariable).symbol)
            }
        }
    }

    @Test
    fun `second level of parameterization methods`() {
        runBlocking {
            val test2 = findClassType<SuperTest2<*, *>>()
            with(test2.method(SuperTest1<*, *, *>::runT)) {
                parameters.first().type.assertClassType<String>()
                returnType.assertClassType<String>()
            }
            with(test2.method(SuperTest1<*, *, *>::runW)) {
                assertEquals("W", (returnType as JcTypeVariable).symbol)
                assertEquals("W", (parameters.first().type as JcTypeVariable).symbol)
            }
            with(test2.method(SuperTest1<*, *, *>::runZ)) {
                assertEquals("Z", (returnType as JcTypeVariable).symbol)
                assertEquals("Z", (parameters.first().type as JcTypeVariable).symbol)
            }
        }
    }

    @Test
    fun `third level of parameterization methods`() {
        runBlocking {
            val test2 = findClassType<SuperTest3>()
            with(test2.method(SuperTest1<*, *, *>::runT)) {
                parameters.first().type.assertClassType<String>()
                returnType.assertClassType<String>()
            }
            with(test2.method(SuperTest1<*, *, *>::runW)) {
                assertEquals(finalW, parameters.first().type.typeName)
                assertEquals(finalW, returnType.typeName)
            }
            with(test2.method(SuperTest1<*, *, *>::runZ)) {
                assertEquals(finalZ, parameters.first().type.typeName)
                assertEquals(finalZ, returnType.typeName)
            }
        }
    }

    private suspend fun JcClassType.field(prop: KMutableProperty1<SuperTest1<*, *, *>, *>): JcTypedField {
        return fields.first { it.name == prop.name }
    }

    private suspend fun JcClassType.method(prop: KFunction2<SuperTest1<*, *, *>, Nothing, *>): JcTypedMethod {
        return methods.first { it.name == prop.name }
    }

}