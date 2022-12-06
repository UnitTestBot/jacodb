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

package org.utbot.jcdb.impl.tests

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.JRE
import org.utbot.jcdb.api.JcClassOrInterface
import org.utbot.jcdb.api.JcClasspath
import org.utbot.jcdb.api.constructors
import org.utbot.jcdb.api.enumValues
import org.utbot.jcdb.api.ext.HierarchyExtension
import org.utbot.jcdb.api.ext.findClass
import org.utbot.jcdb.api.ext.findClassOrNull
import org.utbot.jcdb.api.findMethodOrNull
import org.utbot.jcdb.api.isEnum
import org.utbot.jcdb.api.isFinal
import org.utbot.jcdb.api.isInterface
import org.utbot.jcdb.api.isLocal
import org.utbot.jcdb.api.isMemberClass
import org.utbot.jcdb.api.isNullable
import org.utbot.jcdb.api.isPrivate
import org.utbot.jcdb.api.isPublic
import org.utbot.jcdb.api.jcdbSignature
import org.utbot.jcdb.api.jvmSignature
import org.utbot.jcdb.api.methods
import org.utbot.jcdb.impl.A
import org.utbot.jcdb.impl.B
import org.utbot.jcdb.impl.Bar
import org.utbot.jcdb.impl.C
import org.utbot.jcdb.impl.D
import org.utbot.jcdb.impl.Enums
import org.utbot.jcdb.impl.Foo
import org.utbot.jcdb.impl.SuperDuper
import org.utbot.jcdb.impl.hierarchies.Creature
import org.utbot.jcdb.impl.skipAssertionsOn
import org.utbot.jcdb.impl.usages.Generics
import org.utbot.jcdb.impl.usages.HelloWorldAnonymousClasses
import org.utbot.jcdb.impl.usages.WithInner
import org.w3c.dom.Document
import org.w3c.dom.DocumentType
import org.w3c.dom.Element
import java.util.*
import java.util.concurrent.ConcurrentHashMap

abstract class DatabaseEnvTest {

    abstract val cp: JcClasspath
    abstract val hierarchyExt: HierarchyExtension

    @AfterEach
    open fun close() {
        cp.close()
    }

    @Test
    fun `find class from String`() {
        val clazz = cp.findClass<String>()

        fun fieldType(name: String): String {
            return clazz.declaredFields.first { it.name == name }.type.typeName
        }
        skipAssertionsOn(JRE.JAVA_8) {
            assertEquals("byte", fieldType("coder"))
        }
        assertEquals("long", fieldType("serialVersionUID"))
        assertEquals("java.util.Comparator", fieldType("CASE_INSENSITIVE_ORDER"))
    }

    @Test
    fun `find class from build dir folder`() {
        val clazz = cp.findClass<Foo>()
        assertEquals(Foo::class.java.name, clazz.name)
        assertTrue(clazz.isFinal)
        assertTrue(clazz.isPublic)
        assertFalse(clazz.isInterface)

        val annotations = clazz.annotations
        assertTrue(annotations.size > 1)
        assertNotNull(annotations.firstOrNull { it.matches(Nested::class.java.name) })

        val fields = clazz.declaredFields
        assertEquals(2, fields.size)

        with(fields.first()) {
            assertEquals("foo", name)
            assertEquals("int", type.typeName)
            assertFalse(isNullable)
        }
        with(fields[1]) {
            assertEquals("bar", name)
            assertEquals(String::class.java.name, type.typeName)
            assertFalse(isNullable)
        }

        val methods = clazz.declaredMethods
        assertEquals(5, methods.size)
        with(methods.first { it.name == "smthPublic" }) {
            assertEquals(1, parameters.size)
            assertEquals("int", parameters.first().type.typeName)
            assertTrue(isPublic)
        }

        with(methods.first { it.name == "smthPrivate" }) {
            assertTrue(parameters.isEmpty())
            assertTrue(isPrivate)
        }
    }

    @Test
    fun `array type names`() {
        val clazz = cp.findClass<Bar>()
        assertEquals(Bar::class.java.name, clazz.name)

        val fields = clazz.declaredFields
        assertEquals(3, fields.size)

        with(fields.first()) {
            assertEquals("byteArray", name)
            assertEquals("byte[]", type.typeName)
        }

        with(fields[1]) {
            assertEquals("objectArray", name)
            assertEquals("java.lang.Object[]", type.typeName)
        }

        with(fields[2]) {
            assertEquals("objectObjectArray", name)
            assertEquals("java.lang.Object[][]", type.typeName)
        }

        val methods = clazz.declaredMethods
        assertEquals(2, methods.size)

        with(methods.first { it.name == "smth" }) {
            val parameters = parameters
            assertEquals(1, parameters.size)
            assertEquals("byte[]", parameters.first().type.typeName)
            assertEquals("byte[]", returnType.typeName)
        }
    }

    @Test
    fun `inner and static`() {
        val withInner = cp.findClass<WithInner>()
        val inner = cp.findClass<WithInner.Inner>()
        val staticInner = cp.findClass<WithInner.StaticInner>()

        val anon = cp.findClass("org.utbot.jcdb.impl.usages.WithInner$1")

        assertEquals(withInner, anon.outerClass)
        assertEquals(withInner, inner.outerClass)
        assertEquals(withInner, staticInner.outerClass)
        assertEquals(withInner.findMethodOrNull("sayHello", "()V"), anon.outerMethod)
        assertNull(staticInner.outerMethod)
    }

    @Test
    fun `local and anonymous classes`() {
        val withAnonymous = cp.findClass<HelloWorldAnonymousClasses>()

        val helloWorld = cp.findClass<HelloWorldAnonymousClasses.HelloWorld>()
        assertTrue(helloWorld.isMemberClass)

        val innerClasses = withAnonymous.innerClasses
        assertEquals(4, innerClasses.size)
        val notHelloWorld = innerClasses.filterNot { it.name.contains("\$HelloWorld") }
        val englishGreetings = notHelloWorld.first { it.name.contains("EnglishGreeting") }
        assertTrue(englishGreetings.isLocal)
        assertFalse(englishGreetings.isAnonymous)

        (notHelloWorld - englishGreetings).forEach {
            assertFalse(it.isLocal)
            assertTrue(it.isAnonymous)
            assertFalse(it.isMemberClass)
        }
    }

    @Test
    fun `find interface`() {
        val domClass = cp.findClass<Document>()

        assertTrue(domClass.isPublic)
        assertTrue(domClass.isInterface)

        val methods = domClass.declaredMethods
        assertTrue(methods.isNotEmpty())
        with(methods.first { it.name == "getDoctype" }) {
            assertTrue(parameters.isEmpty())
            assertEquals(DocumentType::class.java.name, returnType.typeName)
            assertEquals("getDoctype()org.w3c.dom.DocumentType;", jcdbSignature)
            assertEquals("getDoctype()Lorg/w3c/dom/DocumentType;", jvmSignature)
            assertTrue(isPublic)
        }

        with(methods.first { it.name == "createElement" }) {
            assertEquals(listOf("java.lang.String"), parameters.map { it.type.typeName })
            assertEquals(Element::class.java.name, returnType.typeName)
            assertEquals("createElement(java.lang.String;)org.w3c.dom.Element;", jcdbSignature)
            assertEquals("createElement(Ljava/lang/String;)Lorg/w3c/dom/Element;", jvmSignature)
        }
    }

    @Test
    fun `find subclasses for class`() {
        with(findSubClasses<AbstractMap<*, *>>(allHierarchy = true).toList()) {
            assertTrue(size > 10) {
                "expected more then 10 but got only: ${joinToString { it.name }}"
            }

            assertNotNull(firstOrNull { it.name == EnumMap::class.java.name })
            assertNotNull(firstOrNull { it.name == HashMap::class.java.name })
            assertNotNull(firstOrNull { it.name == WeakHashMap::class.java.name })
            assertNotNull(firstOrNull { it.name == TreeMap::class.java.name })
            assertNotNull(firstOrNull { it.name == ConcurrentHashMap::class.java.name })
        }
    }

    @Test
    fun `find subclasses for interface`() {
        with(findSubClasses<Document>()) {
            assertTrue(toList().isNotEmpty())
        }
    }

    @Test
    fun `find huge number of subclasses`() {
        with(findSubClasses<Runnable>()) {
            assertTrue(take(10).toList().size == 10)
        }
    }

    @Test
    fun `enum values`() {
        val enum = cp.findClass<Enums>()
        assertTrue(enum.isEnum)
        assertEquals(
            listOf("SIMPLE", "COMPLEX", "SUPER_COMPLEX").sorted(),
            enum.enumValues?.map { it.name }?.sorted()
        )

        val notEnum = cp.findClass<String>()
        assertFalse(notEnum.isEnum)
        assertNull(notEnum.enumValues)
    }

    @Test
    fun `find subclasses with all hierarchy`() {
        val clazz = cp.findClassOrNull<SuperDuper>()
        assertNotNull(clazz!!)

        with(hierarchyExt.findSubClasses(clazz, allHierarchy = true).toList()) {
            assertEquals(4, size) {
                "expected 4 but got only: ${joinToString { it.name }}"
            }

            assertNotNull(firstOrNull { it.name == A::class.java.name })
            assertNotNull(firstOrNull { it.name == B::class.java.name })
            assertNotNull(firstOrNull { it.name == C::class.java.name })
            assertNotNull(firstOrNull { it.name == D::class.java.name })
        }
    }

    @Test
    fun `get all methods`() {
        val c = cp.findClass<C>()
        val signatures = c.methods.map { it.jcdbSignature }
        assertTrue(c.methods.size > 15)
        assertTrue(signatures.contains("saySmth(java.lang.String;)void;"))
        assertTrue(signatures.contains("saySmth()void;"))
        assertTrue(signatures.contains("<init>()void;"))
        assertEquals(3, c.constructors.size)
    }

    @Test
    fun `method parameters`() {
        val generics = cp.findClass<Generics<*>>()
        val method = generics.methods.first { it.name == "merge" }

        assertEquals(1, method.parameters.size)
        with(method.parameters.first()) {
            assertEquals(generics.name, type.typeName)
            assertEquals(method, this.method)
            assertEquals(0, index)
            assertNull(name)
        }
    }

    @Test
    fun `find method overrides`() {
        val creatureClass = cp.findClass<Creature>()

        assertEquals(2, creatureClass.declaredMethods.size)
        val sayMethod = creatureClass.declaredMethods.first { it.name == "say" }
        val helloMethod = creatureClass.declaredMethods.first { it.name == "hello" }

        var overrides = hierarchyExt.findOverrides(sayMethod).toList()

        with(overrides) {
            assertEquals(4, size)

            assertNotNull(firstOrNull { it.enclosingClass == cp.findClass<Creature.DinosaurImpl>() })
            assertNotNull(firstOrNull { it.enclosingClass == cp.findClass<Creature.Fish>() })
            assertNotNull(firstOrNull { it.enclosingClass == cp.findClass<Creature.TRex>() })
            assertNotNull(firstOrNull { it.enclosingClass == cp.findClass<Creature.Pterodactyl>() })
        }
        overrides = hierarchyExt.findOverrides(helloMethod).toList()
        with(overrides) {
            assertEquals(1, size)

            assertNotNull(firstOrNull { it.enclosingClass == cp.findClass<Creature.TRex>() })

        }
    }


    @Test
    fun `classes common methods usages`() = runBlocking {
        val runnable = cp.findClass<Runnable>()
        val runMethod = runnable.declaredMethods.first { it.name == "run" }
        assertTrue(hierarchyExt.findOverrides(runMethod).count() > 300)
    }

    @Test
    fun `classes common hierarchy`() = runBlocking {
        val runnable = cp.findClass<Runnable>()
        assertTrue(hierarchyExt.findSubClasses(runnable, true).count() > 300)
    }

    private inline fun <reified T> findSubClasses(allHierarchy: Boolean = false): Sequence<JcClassOrInterface> {
        return hierarchyExt.findSubClasses(T::class.java.name, allHierarchy)
    }
}