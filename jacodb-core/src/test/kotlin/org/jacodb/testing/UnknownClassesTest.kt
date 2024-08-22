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

package org.jacodb.testing

import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.ext.cfg.callExpr
import org.jacodb.api.jvm.ext.cfg.fieldRef
import org.jacodb.api.jvm.ext.findClass
import org.jacodb.api.jvm.ext.findDeclaredMethodOrNull
import org.jacodb.api.jvm.ext.findFieldOrNull
import org.jacodb.api.jvm.ext.findMethodOrNull
import org.jacodb.api.jvm.ext.objectClass
import org.jacodb.impl.features.classpaths.JcUnknownClass
import org.jacodb.impl.features.classpaths.UnknownClassMethodsAndFields
import org.jacodb.impl.features.classpaths.UnknownClasses
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UnknownClassesTest : BaseTest() {

    companion object : WithGlobalDB(UnknownClasses)

    @Test
    fun `unknown class is resolved`() {
        val clazz = cp.findClass("xxx")
        assertTrue(clazz is JcUnknownClass)
        assertTrue(clazz.declaredMethods.isEmpty())
        assertTrue(clazz.declaredFields.isEmpty())

        assertNotNull(clazz.declaration.location)
    }

    @Test
    fun `fields and methods of unknown class is empty`() {
        val clazz = cp.findClass("PhantomClassSubclass").superClass
        assertTrue(clazz is JcUnknownClass)
        assertNotNull(clazz!!)
        assertTrue(clazz.declaredMethods.isEmpty())
        assertTrue(clazz.declaredFields.isEmpty())
    }

    @Test
    fun `parent of class is resolved`() {
        val clazz = cp.findClass("PhantomClassSubclass")
        assertTrue(clazz.superClass is JcUnknownClass)
    }

    @Test
    fun `instructions with references to unknown classes are resolved`() {
        val clazz = listOf(
            cp.findClass("PhantomClassSubclass"),
            cp.findClass("PhantomCodeConsumer")
        )
        clazz.forEach {
            it.declaredMethods.forEach { it.assertCfg() }
        }
    }

    @Test
    fun `instructions with references to unknown fields and methods are resolved`() {
        val clazz = listOf(
            cp.findClass("PhantomDeclarationConsumer")
        )
        clazz.forEach {
            it.declaredMethods.forEach { it.assertCfg() }
        }
    }

    @Test
    fun `object doesn't have unknown methods and fields`() {
        cp.objectClass.let { clazz ->
            assertTrue(clazz !is JcUnknownClass)
            assertTrue(clazz.declaredFields.isEmpty())
            val xxxField = clazz.findFieldOrNull("xxx")
            assertNull(xxxField)
            val xxxMethod = clazz.findMethodOrNull("xxx", "(JILjava/lang/Exception;)V")
            assertNull(xxxMethod)
        }
    }

    private fun JcMethod.assertCfg(){
        val cfg = flowGraph()
        cfg.instructions.forEach {
            it.callExpr?.let {
                assertNotNull(it.method)
            }
            it.fieldRef?.let {
                assertNotNull(it.field)
            }
        }
    }
}
