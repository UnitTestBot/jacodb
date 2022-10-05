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
    fun `generics for parent types`() = runBlocking {
        val superFooType = cp.findTypeOrNull(SuperFoo::class.java.name)
        assertNotNull(superFooType!!)
        assertTrue(superFooType is JcClassType)
        superFooType as JcClassType
        with(superFooType.superType()) {
            assertNotNull(this)
            this!!
            assertTrue(this is JcClassType)
            this as JcClassType
            val fields = fields()
            assertEquals(2, fields.size)

            with(fields.first()) {
                assertEquals("state", name)
                assertTrue(fieldType() is JcClassType)
            }
        }
    }

    @Test
    fun `primitive and array types`() = runBlocking {
        val primitiveAndArrays = cp.findTypeOrNull(PrimitiveAndArrays::class.java.name)
        assertTrue(primitiveAndArrays is JcClassType)
        primitiveAndArrays as JcClassType
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
}