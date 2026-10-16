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

package org.jacodb.testing.lookup

import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.MethodNotFoundException
import org.jacodb.api.jvm.ext.findClass
import org.jacodb.api.jvm.ext.toType
import org.jacodb.impl.cfg.TypedSpecialMethodRefImpl
import org.jacodb.impl.types.TypeNameImpl
import org.jacodb.testing.BaseTest
import org.jacodb.testing.WithGlobalDbImmutable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

class SpecialMethodLookupTest : BaseTest() {
    companion object : WithGlobalDbImmutable()

    @Test
    fun `super call resolves default inherited by superclass`() {
        val child = cp.findClass<SpecialMethodFixtures.DefaultChild>()

        assertSpecialOwner(
            owner = child,
            name = "value",
            descriptor = "()I",
            expectedOwner = SpecialMethodFixtures.DefaultValue::class.java,
        )

        val caller = child.declaredMethods.single { it.name == "callSuper" }
        assertFalse(caller.flowGraph().instructions.isEmpty())
    }

    @Test
    fun `class chain wins over interface default`() {
        val child = cp.findClass<SpecialMethodFixtures.ClassValueChild>()

        assertSpecialOwner(
            owner = child,
            name = "value",
            descriptor = "()I",
            expectedOwner = SpecialMethodFixtures.ClassValue::class.java,
        )
    }

    @Test
    fun `most specific interface declaration wins`() {
        val child = cp.findClass<SpecialMethodFixtures.SpecificValue>()

        assertSpecialOwner(
            owner = child,
            name = "value",
            descriptor = "()I",
            expectedOwner = SpecialMethodFixtures.MoreSpecificValue::class.java,
        )
    }

    @Test
    fun `abstract redeclaration suppresses inherited default`() {
        val child = cp.findClass<SpecialMethodFixtures.AbstractUser>()

        assertSpecialOwner(
            owner = child,
            name = "value",
            descriptor = "()I",
            expectedOwner = SpecialMethodFixtures.AbstractValue::class.java,
        )
        assertTrue(child.lookup.specialMethod(name = "value", description = "()I")!!.isAbstract)
    }

    @Test
    fun `inherited default preserves generic substitution`() {
        val child = cp.findClass<SpecialMethodFixtures.GenericChild>()

        assertSpecialOwner(
            owner = child,
            name = "get",
            descriptor = "()Ljava/lang/Object;",
            expectedOwner = SpecialMethodFixtures.GenericValue::class.java,
        )
        val method = child.toType().lookup.specialMethod(name = "get", description = "()Ljava/lang/Object;")

        assertNotNull(method)
        assertEquals("java.lang.String", method!!.returnType.typeName)
        assertFalse(child.declaredMethods.single { it.name == "callSuper" }.flowGraph().instructions.isEmpty())
    }

    @Test
    fun `constructors are not inherited and static methods are not special`() {
        val child = cp.findClass<SpecialMethodFixtures.ConstructorChild>()
        val defaultBase = cp.findClass<SpecialMethodFixtures.DefaultBase>()

        assertNull(child.lookup.specialMethod(name = "<init>", description = "(I)V"))
        assertNull(child.toType().lookup.specialMethod(name = "<init>", description = "(I)V"))
        assertNull(child.lookup.specialMethod(name = "staticValue", description = "()I"))
        assertNull(child.toType().lookup.specialMethod(name = "staticValue", description = "()I"))
        assertNull(defaultBase.lookup.specialMethod(name = "staticValue", description = "()I"))
        assertSpecialOwner(
            owner = child,
            name = "privateValue",
            descriptor = "()I",
            expectedOwner = SpecialMethodFixtures.ConstructorChild::class.java,
        )
    }

    @Test
    fun `missing method diagnostic includes argument types and descriptor`() {
        val child = cp.findClass<SpecialMethodFixtures.DefaultChild>().toType()
        val reference = TypedSpecialMethodRefImpl(
            type = child,
            name = "missing",
            argTypes = listOf(
                TypeNameImpl.fromTypeName(typeName = "int"),
                TypeNameImpl.fromTypeName(typeName = "java.lang.String"),
            ),
            returnType = TypeNameImpl.fromTypeName(typeName = "void"),
        )

        val error = assertThrows(MethodNotFoundException::class.java) { reference.method }

        assertTrue(error.message!!.contains("missing(int, java.lang.String)"))
        assertTrue(error.message!!.contains("(ILjava/lang/String;)V"))
    }

    @Test
    fun `JDK Random super call reaches RandomGenerator default`() {
        val randomGenerator = cp.findClassOrNull("java.util.random.RandomGenerator")
        assumeTrue(randomGenerator != null, "RandomGenerator requires JDK 17 or newer")
        val random = cp.findClass("java.util.Random")
        val threadLocalRandom = cp.findClass("java.util.concurrent.ThreadLocalRandom")
        val nextInt = threadLocalRandom.declaredMethods.single { it.name == "nextInt" && it.description == "(II)I" }

        val graph = nextInt.flowGraph()
        val raw = random.lookup.specialMethod(name = "nextInt", description = "(II)I")
        val typed = random.toType().lookup.specialMethod(name = "nextInt", description = "(II)I")

        assertNotNull(raw)
        assertNotNull(typed)
        assertEquals(randomGenerator, raw!!.enclosingClass)
        assertEquals(raw, typed!!.method)
        assertFalse(graph.instructions.isEmpty())
    }

    private fun assertSpecialOwner(
        owner: JcClassOrInterface,
        name: String,
        descriptor: String,
        expectedOwner: Class<*>,
    ) {
        val raw = owner.lookup.specialMethod(name, descriptor)
        val typed = owner.toType().lookup.specialMethod(name, descriptor)

        assertNotNull(raw)
        assertNotNull(typed)
        assertEquals(expectedOwner.name, raw!!.enclosingClass.name)
        assertEquals(raw, typed!!.method)
        assertEquals(expectedOwner.name, (typed.enclosingType as JcClassType).jcClass.name)
    }
}
