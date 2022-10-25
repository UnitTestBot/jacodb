package org.utbot.jcdb.impl.types

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.utbot.jcdb.api.JcArrayType
import org.utbot.jcdb.api.JcPrimitiveType
import org.utbot.jcdb.api.isConstructor

class TypesTest : BaseTypesTest() {

    @Test
    fun `primitive and array types`() = runBlocking {
        val primitiveAndArrays = findClassType<PrimitiveAndArrays>()
        val fields = primitiveAndArrays.fields
        assertEquals(2, fields.size)

        with(fields.first()) {
            assertTrue(fieldType() is JcPrimitiveType)
            assertEquals("value", name)
            assertEquals("int", fieldType().typeName)
        }
        with(fields.get(1)) {
            assertTrue(fieldType() is JcArrayType)
            assertEquals("intArray", name)
            assertEquals("int[]", fieldType().typeName)
        }


        val methods = primitiveAndArrays.methods.filterNot { it.method.isConstructor }
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
}