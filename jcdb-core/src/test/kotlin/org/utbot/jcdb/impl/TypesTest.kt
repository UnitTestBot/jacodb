package org.utbot.jcdb.impl

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.utbot.jcdb.api.JCDB
import org.utbot.jcdb.api.JcArrayType
import org.utbot.jcdb.api.JcClassType
import org.utbot.jcdb.api.JcClasspath
import org.utbot.jcdb.api.JcPrimitiveType
import org.utbot.jcdb.api.JcType
import org.utbot.jcdb.api.JcTypeVariable
import org.utbot.jcdb.api.ext.findTypeOrNull
import org.utbot.jcdb.api.isConstructor
import org.utbot.jcdb.impl.types.PrimitiveAndArrays
import org.utbot.jcdb.impl.types.SuperFoo
import org.utbot.jcdb.jcdb

class TypesTest {

    companion object : LibrariesMixin {
        var db: JCDB? = runBlocking {
            jcdb {
                loadByteCode(allClasspath)
                useProcessJavaRuntime()
            }.also {
                it.awaitBackgroundJobs()
            }
        }

        @AfterAll
        @JvmStatic
        fun cleanup() {
            db?.close()
            db = null
        }
    }

    private val cp: JcClasspath = runBlocking { db!!.classpath(allClasspath) }

    @AfterEach
    fun close() {
        cp.close()
    }

    @Test
    fun `generics for super types`() = runBlocking {
        val superFooType = findClassType<SuperFoo>()
        with(superFooType.superType().assertClassType()) {
            val fields = fields()
            assertEquals(2, fields.size)

            with(fields.first()) {
                assertEquals("state", name)
                fieldType().assertType<String>()
            }
        }
    }

    @Test
    fun `generics for methods 1`() = runBlocking {
        val superFooType = findClassType<SuperFoo>()
        val superType = superFooType.superType().assertClassType()
        val methods = superType.methods().filterNot { it.method.isConstructor }
        assertEquals(2, methods.size)

        with(methods.first { it.method.name == "run1" }) {
            returnType().assertType<String>()
            parameters().first().type().assertType<String>()
        }
    }

//    @Test
    fun `generics for methods 2`() = runBlocking {
        val superFooType = findClassType<SuperFoo>()
        val superType = superFooType.superType().assertClassType()
        val methods = superType.methods().filterNot { it.method.isConstructor }
        assertEquals(2, methods.size)

        with(methods.first { it.method.name == "run2" }) {
            val returnType = returnType()
            val params = parameters().first()
            val w = originalParameterization().first()
            assertEquals("W", (params.type() as? JcTypeVariable)?.typeSymbol)
            assertEquals("W", w.symbol)
            assertEquals(cp.findTypeOrNull<String>(), w.bounds.first())
        }
    }

    @Test
    fun `primitive and array types`() = runBlocking {
        val primitiveAndArrays = findClassType<PrimitiveAndArrays>()
        val fields = primitiveAndArrays.fields()
        assertEquals(2, fields.size)

        with(fields.first()) {
            assertTrue(fieldType() is JcPrimitiveType)
            assertEquals("int", name)
            assertEquals("int", fieldType().typeName)
        }
        with(fields.get(1)) {
            assertTrue(fieldType() is JcArrayType)
            assertEquals("intArray", name)
            assertEquals("int[]", fieldType().typeName)
        }


        val methods = primitiveAndArrays.methods().filterNot { it.method.isConstructor }
        with(methods.first()) {
            assertTrue(returnType() is JcArrayType)
            assertEquals("int[]", returnType().typeName)

            assertEquals(1, parameters().size)
            with(parameters().get(0)) {
                assertTrue(type() is JcArrayType)
                assertEquals("java.lang.String[]", type().typeName)
            }
        }
    }

    private suspend inline fun <reified T> findClassType(): JcClassType {
        val found = cp.findTypeOrNull(T::class.java.name)
        assertNotNull(found)
        assertTrue(found is JcClassType)
        return found as JcClassType
    }

    private fun JcType?.assertClassType(): JcClassType {
        assertNotNull(this)
        assertTrue(this is JcClassType)
        return this as JcClassType
    }

    private suspend inline fun <reified T> JcType?.assertType() {
        val expected = findClassType<T>()
        assertNotNull(this)
        assertTrue(this is JcClassType)
        assertEquals(expected.typeName, this?.typeName)
    }

    private val stringType get() = runBlocking { findClassType<String>() }
}