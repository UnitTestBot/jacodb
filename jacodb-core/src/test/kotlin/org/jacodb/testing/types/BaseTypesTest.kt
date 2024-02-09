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

import org.jacodb.api.JcClassType
import org.jacodb.api.JcType
import org.jacodb.testing.BaseTest
import org.jacodb.testing.WithGlobalDB
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull

abstract class BaseTypesTest : BaseTest() {

    companion object : WithGlobalDB()

    protected inline fun <reified T> findType(): JcClassType {
        val found = cp.findTypeOrNull(T::class.java.name)
        assertNotNull(found)
        return found!!.assertIs()
    }

    protected fun JcType?.assertIsClass(): JcClassType {
        assertNotNull(this)
        return this!!.assertIs()
    }

    protected inline fun <reified T> JcType?.assertClassType(): JcClassType {
        val expected = findType<T>()
        assertEquals(
            expected.jcClass.name,
            (this as? JcClassType)?.jcClass?.name,
            "Expected ${expected.jcClass.name} but got ${this?.typeName}"
        )
        return this as JcClassType
    }

    protected inline fun <reified T> Any.assertIs(): T {
        return assertInstanceOf(T::class.java, this)
    }
}
