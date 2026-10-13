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

package org.jacodb.go.api

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GoTypeIdentityTest {
    @Test
    fun distinguishesPrimitiveTuplePositionsAndPointerFields() {
        val integer = BasicType(typeName = "int")
        val pointer = PointerType(baseType = integer)
        val floatingTuple = TupleType(
            types = listOf(BasicType(typeName = "bool"), BasicType(typeName = "float32"), integer),
        )
        val stringTuple = TupleType(
            types = listOf(BasicType(typeName = "bool"), integer, BasicType(typeName = "rune")),
        )

        assertNotEquals(integer as GoType, pointer as GoType)
        assertNotEquals(pointer as GoType, integer as GoType)
        assertEquals("*int", pointer.typeName)
        assertNotEquals(floatingTuple, stringTuple)
        assertNotEquals(
            StructType(fields = listOf(integer), tags = null),
            StructType(fields = listOf(pointer), tags = null),
        )
    }

    @Test
    fun equalCompositeKeysHaveEqualHashesAndWorkInMaps() {
        val first = MapType(
            keyType = PointerType(baseType = BasicType(typeName = "int")),
            valueType = TupleType(types = listOf(BasicType(typeName = "bool"), BasicType(typeName = "int"))),
        )
        val second = MapType(
            keyType = PointerType(baseType = BasicType(typeName = "int")),
            valueType = TupleType(types = listOf(BasicType(typeName = "bool"), BasicType(typeName = "int"))),
        )

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
        assertEquals("value", mapOf<GoType, String>(first to "value")[second])
    }

    @Test
    fun namedRecursionKeepsNominalIdentityStable() {
        val node = NamedType(underlyingType = NullType(), name = "pkg.Node", methods = emptyList())
        val originalHash = node.hashCode()
        val pointer = PointerType(baseType = node)

        node.underlyingType = StructType(fields = listOf(pointer), tags = null)

        assertEquals(originalHash, node.hashCode())
        assertEquals("*pkg.Node", pointer.typeName)
        assertTrue(node.underlyingType.typeName.contains("*pkg.Node"))
        assertNotEquals(node as GoType, BasicType(typeName = "pkg.Node") as GoType)
    }

    @Test
    fun mutablePointerPlaceholderCyclesAreSafeToInspectAndCompare() {
        val first = PointerType(baseType = NullType())
        val second = PointerType(baseType = NullType())

        first.baseType = first
        second.baseType = second

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
        assertTrue(first.toString().contains("recursive"))
    }
}
