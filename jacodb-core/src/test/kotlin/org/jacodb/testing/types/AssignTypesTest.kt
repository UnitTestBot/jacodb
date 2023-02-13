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

package org.jacodb.testing.types

import org.jacodb.api.ext.isAssignable
import org.jacodb.api.ext.objectType
import org.jacodb.api.throwClassNotFound
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test


class AssignTypesTest : BaseTypesTest() {

    @Test
    fun `unboxing is working`() {
        assertTrue("java.lang.Byte".type.isAssignable("byte".type))
        assertTrue("byte".type.isAssignable("java.lang.Byte".type))

        assertTrue("int".type.isAssignable("java.lang.Integer".type))
        assertTrue("java.lang.Integer".type.isAssignable("int".type))
    }

    @Test
    fun `arrays is working`() {
        assertTrue("byte[]".type.isAssignable(cp.objectType))
        assertFalse("java.lang.Byte[]".type.isAssignable("byte[]".type))
        assertFalse("byte[]".type.isAssignable("java.lang.Byte[]".type))
        assertFalse("int[]".type.isAssignable("byte[]".type))

        assertTrue("boolean[][]".type.isAssignable("java.lang.Object[]".type))
        assertTrue("boolean[][][]".type.isAssignable("java.lang.Cloneable[][]".type))
    }

    @Test
    fun `class type is working`() {
        assertTrue("java.util.List".type.isAssignable("java.util.Collection".type))
        assertTrue("java.util.AbstractList".type.isAssignable("java.util.Collection".type))

        assertFalse(cp.objectType.isAssignable("java.util.Collection".type))
    }

    @Test
    fun `primitive type is working`() {
        assertTrue("byte".type.isAssignable("int".type))
        assertTrue("byte".type.isAssignable("byte".type))
        assertTrue("java.lang.Byte".type.isAssignable("byte".type))
        assertTrue("java.lang.Integer".type.isAssignable("int".type))

        assertTrue("long".type.isAssignable("double".type))
        assertFalse("boolean".type.isAssignable("short".type))
    }

    private val String.type get() = cp.findTypeOrNull(this) ?: throwClassNotFound()
}