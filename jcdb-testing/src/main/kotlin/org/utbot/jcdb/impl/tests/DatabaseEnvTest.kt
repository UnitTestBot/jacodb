package org.utbot.jcdb.impl.tests

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.utbot.jcdb.api.*
import org.utbot.jcdb.api.ext.HierarchyExtension
import org.utbot.jcdb.api.ext.findClass
import org.utbot.jcdb.api.ext.findClassOrNull
import org.utbot.jcdb.api.ext.findSubClasses
import org.utbot.jcdb.impl.*
import org.utbot.jcdb.impl.hierarchies.Creature
import org.utbot.jcdb.impl.types.byte
import org.utbot.jcdb.impl.usages.HelloWorldAnonymousClasses
import org.utbot.jcdb.impl.usages.WithInner
import org.w3c.dom.Document
import org.w3c.dom.DocumentType
import org.w3c.dom.Element
import java.util.*
import java.util.concurrent.ConcurrentHashMap

abstract class DatabaseEnvTest {

    abstract val cp: ClasspathSet
    abstract val hierarchyExt: HierarchyExtension

    @AfterEach
    open fun close() {
        cp.close()
    }

    @Test
    fun `find class from build dir folder`() = runBlocking {
        val clazz = cp.findClass<Foo>()
        assertEquals(Foo::class.java.name, clazz.name)
        assertTrue(clazz.isFinal())
        assertTrue(clazz.isPublic())
        assertFalse(clazz.isInterface())

        val annotations = clazz.annotations()
        assertTrue(annotations.size > 1)
        assertNotNull(annotations.firstOrNull { it.name == Nested::class.java.name })

        val fields = clazz.fields()
        assertEquals(2, fields.size)

        with(fields.first()) {
            assertEquals("foo", name)
            assertEquals("int", type().name)
        }
        with(fields[1]) {
            assertEquals("bar", name)
            assertEquals(String::class.java.name, type().name)
        }

        val methods = clazz.methods()
        assertEquals(5, methods.size)
        with(methods.first { it.name == "smthPublic" }) {
            assertEquals(1, parameters().size)
            assertEquals("int", parameters().first().name)
            assertTrue(isPublic())
        }

        with(methods.first { it.name == "smthPrivate" }) {
            assertTrue(parameters().isEmpty())
            assertTrue(isPrivate())
        }
    }

    @Test
    fun `array types`() = runBlocking {
        val clazz = cp.findClass<Bar>()
        assertEquals(Bar::class.java.name, clazz.name)

        val fields = clazz.fields()
        assertEquals(3, fields.size)

        with(fields.first()) {
            assertEquals("byteArray", name)
            assertEquals("byte[]", type().name)
            assertEquals(cp.byte, (type() as ArrayClassId).elementClass)
        }

        with(fields.get(1)) {
            assertEquals("objectArray", name)
            assertEquals("java.lang.Object[]", type().name)
            assertEquals(
                cp.findClass<Any>(),
                (type() as ArrayClassId).elementClass
            )
        }

        with(fields.get(2)) {
            assertEquals("objectObjectArray", name)
            assertEquals("java.lang.Object[][]", type().name)
            assertEquals(
                cp.findClass<Any>(),
                ((type() as ArrayClassId).elementClass as ArrayClassId).elementClass
            )
        }

        val methods = clazz.methods()
        assertEquals(2, methods.size)

        with(methods.first { it.name == "smth" }) {
            val parameters = parameters()
            assertEquals(1, parameters.size)
            assertEquals("byte[]", parameters.first().name)
            assertEquals("byte[]", returnType().name)
        }
    }

    @Test
    fun `inner and static`() = runBlocking {
        val withInner = cp.findClass<WithInner>()
        val inner = cp.findClass<WithInner.Inner>()
        val staticInner = cp.findClass<WithInner.StaticInner>()

        val anon = cp.findClass("org.utbot.jcdb.impl.usages.WithInner$1")

        assertEquals(withInner, anon.outerClass())
        assertEquals(withInner, inner.outerClass())
        assertEquals(withInner, staticInner.outerClass())
        assertEquals(withInner.findMethodOrNull("sayHello", "()V"), anon.outerMethod())
        assertNull(staticInner.outerMethod())
    }

    @Test
    fun `local and anonymous classes`() = runBlocking {
        val withAnonymous = cp.findClass<HelloWorldAnonymousClasses>()

        val helloWorld = cp.findClass<HelloWorldAnonymousClasses.HelloWorld>()
        assertTrue(helloWorld.isMemberClass())

        val innerClasses = withAnonymous.innerClasses()
        assertEquals(4, innerClasses.size)
        val notHelloWorld = innerClasses.filterNot { it.name.contains("\$HelloWorld") }
        val englishGreetings = notHelloWorld.first { it.name.contains("EnglishGreeting") }
        assertTrue(englishGreetings.isLocal())
        assertFalse(englishGreetings.isAnonymous())

        (notHelloWorld - englishGreetings).forEach {
            assertFalse(it.isLocal())
            assertTrue(it.isAnonymous())
            assertFalse(it.isMemberClass())
        }
    }

    @Test
    fun `find lazy-loaded class`() = runBlocking {
        val domClass = cp.findClass<Document>()

        assertTrue(domClass.isPublic())
        assertTrue(domClass.isInterface())

        val methods = domClass.methods()
        assertTrue(methods.isNotEmpty())
        with(methods.first { it.name == "getDoctype" }) {
            assertTrue(parameters().isEmpty())
            assertEquals(DocumentType::class.java.name, returnType().name)
            assertEquals("getDoctype()org.w3c.dom.DocumentType;", signature(false))
            assertEquals("getDoctype()Lorg/w3c/dom/DocumentType;", signature(true))
            assertTrue(isPublic())
        }

        with(methods.first { it.name == "createElement" }) {
            assertEquals(listOf(cp.findClass<String>()), parameters())
            assertEquals(Element::class.java.name, returnType().name)
            assertEquals("createElement(java.lang.String;)org.w3c.dom.Element;", signature(false))
            assertEquals("createElement(Ljava/lang/String;)Lorg/w3c/dom/Element;", signature(true))

        }
    }

    @Test
    fun `find sub-types from lazy loaded classes`() = runBlocking {
        with(cp.findSubClasses<AbstractMap<*, *>>()) {
            assertTrue(size > 10) {
                "expected more then 10 but got only: ${joinToString { it.name }}"
            }

            assertNotNull(firstOrNull { it.name == EnumMap::class.java.name })
            assertNotNull(firstOrNull { it.name == HashMap::class.java.name })
            assertNotNull(firstOrNull { it.name == WeakHashMap::class.java.name })
            assertNotNull(firstOrNull { it.name == TreeMap::class.java.name })
            assertNotNull(firstOrNull { it.name == ConcurrentHashMap::class.java.name })
        }

        with(cp.findSubClasses(Document::class.java.name)) {
            assertTrue(isNotEmpty())
        }
    }

    @Test
    fun `find sub-types of array`() = runBlocking {
        val stringArray = cp.findClass("java.lang.String[]")

        with(cp.findSubClasses(stringArray, true)) {
            assertTrue(isEmpty())
        }
    }

    @Test
    fun `enum values`() = runBlocking {
        val enum = cp.findClass<Enums>()
        assertTrue(enum.isEnum())
        assertEquals(
            listOf("SIMPLE", "COMPLEX", "SUPER_COMPLEX").sorted(),
            enum.enumValues()?.map { it.name }?.sorted()
        )

        val notEnum = cp.findClass<String>()
        assertFalse(notEnum.isEnum())
        assertNull(notEnum.enumValues())
    }

    @Test
    fun `find sub-types with all hierarchy`() = runBlocking {
        val clazz = cp.findClassOrNull<SuperDuper>()
        assertNotNull(clazz!!)

        with(cp.findSubClasses(clazz, allHierarchy = true)) {
            assertEquals(4, size) {
                "expected more then 10 but got only: ${joinToString { it.name }}"
            }

            assertNotNull(firstOrNull { it.name == A::class.java.name })
            assertNotNull(firstOrNull { it.name == B::class.java.name })
            assertNotNull(firstOrNull { it.name == C::class.java.name })
            assertNotNull(firstOrNull { it.name == D::class.java.name })
        }
    }

    @Test
    fun `find method overrides`() = runBlocking {
        val creatureClass = cp.findClass<Creature>()

        assertEquals(2, creatureClass.methods().size)
        val sayMethod = creatureClass.methods().first { it.name == "say" }
        val helloMethod = creatureClass.methods().first { it.name == "hello" }

        var overrides = hierarchyExt.findOverrides(sayMethod)

        with(overrides) {
            assertEquals(4, size)

            assertNotNull(firstOrNull { it.classId == cp.findClass<Creature.DinosaurImpl>() })
            assertNotNull(firstOrNull { it.classId == cp.findClass<Creature.Fish>() })
            assertNotNull(firstOrNull { it.classId == cp.findClass<Creature.TRex>() })
            assertNotNull(firstOrNull { it.classId == cp.findClass<Creature.Pterodactyl>() })
        }
        overrides = hierarchyExt.findOverrides(helloMethod)
        with(overrides) {
            assertEquals(1, size)

            assertNotNull(firstOrNull { it.classId == cp.findClass<Creature.TRex>() })

        }
    }

}