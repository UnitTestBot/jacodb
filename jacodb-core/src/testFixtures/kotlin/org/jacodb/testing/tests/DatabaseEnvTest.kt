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

package org.jacodb.testing.tests

import kotlinx.coroutines.runBlocking
import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcClassProcessingTask
import org.jacodb.api.JcClasspath
import org.jacodb.api.PredefinedPrimitives
import org.jacodb.api.ext.*
import org.jacodb.impl.features.classpaths.ClasspathCache
import org.jacodb.impl.features.classpaths.VirtualClassContent
import org.jacodb.impl.features.classpaths.VirtualClasses
import org.jacodb.impl.features.classpaths.virtual.JcVirtualClass
import org.jacodb.impl.features.classpaths.virtual.JcVirtualField
import org.jacodb.impl.features.classpaths.virtual.JcVirtualMethod
import org.jacodb.testing.*
import org.jacodb.testing.hierarchies.Creature
import org.jacodb.testing.structure.FieldsAndMethods
import org.jacodb.testing.structure.HiddenFieldSuperClass.HiddenFieldSuccClass
import org.jacodb.testing.types.AAA
import org.jacodb.testing.types.AAA.CCC
import org.jacodb.testing.usages.Generics
import org.jacodb.testing.usages.HelloWorldAnonymousClasses
import org.jacodb.testing.usages.WithInner
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.JRE
import org.w3c.dom.Document
import org.w3c.dom.DocumentType
import org.w3c.dom.Element
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

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

        with(fields.single { it.name == "foo" }) {
            assertEquals("int", type.typeName)
            assertEquals(false, isNullable)
        }
        with(fields.single { it.name == "bar" }) {
            assertEquals(String::class.java.name, type.typeName)
            assertEquals(false, isNullable)
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

        val anon = cp.findClass("org.jacodb.testing.usages.WithInner$1")

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
            assertEquals("org.w3c.dom.Document#getDoctype():org.w3c.dom.DocumentType", humanReadableSignature)
            assertTrue(isPublic)
        }

        with(methods.first { it.name == "createElement" }) {
            assertEquals(listOf("java.lang.String"), parameters.map { it.type.typeName })
            assertEquals(Element::class.java.name, returnType.typeName)
            assertEquals("createElement(java.lang.String;)org.w3c.dom.Element;", jcdbSignature)
            assertEquals("createElement(Ljava/lang/String;)Lorg/w3c/dom/Element;", jvmSignature)
            assertEquals("org.w3c.dom.Document#createElement(java.lang.String):org.w3c.dom.Element", humanReadableSignature)
        }

        with(methods.first { it.name == "importNode" }) {
            assertEquals("importNode(org.w3c.dom.Node;boolean;)org.w3c.dom.Node;", jcdbSignature)
            assertEquals("org.w3c.dom.Document#importNode(org.w3c.dom.Node,boolean):org.w3c.dom.Node", humanReadableSignature)
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

    @Test
    fun `body of method`() = runBlocking {
        val runnable = cp.findClass<Runnable>()

        val method = runnable.declaredMethods.first()
        assertFalse(method.hasBody)
        assertNotNull(method.asmNode())
        assertTrue(method.asmNode().instructions.toList().isEmpty())
    }

    @Test
    fun `class task should work`() = runBlocking {
        val counter = AtomicLong()
        cp.execute(object : JcClassProcessingTask {
            override fun process(clazz: JcClassOrInterface) {
                counter.incrementAndGet()
            }
        })
        val count = counter.get()
        println("Number of classes is $count")
        assertTrue(count > 30_000, "counter is $count expected to be > 30_000")
    }

    @Test
    fun `all visible fields should work`() = runBlocking {
        val clazz = cp.findClass<FieldsAndMethods.Common1Child>()
        with(clazz.fields) {
            assertNull(firstOrNull { it.name == "privateField" })
            assertNull(firstOrNull { it.name == "packageField" })
            assertNotNull(firstOrNull { it.name == "privateFieldsAndMethods" })
            assertNotNull(firstOrNull { it.name == "publicField" })
            assertNotNull(firstOrNull { it.name == "protectedField" })
        }
    }

    @Test
    fun `all visible methods should work`() = runBlocking {
        val clazz = cp.findClass<FieldsAndMethods.Common1Child>()
        with(clazz.methods) {
            assertNull(firstOrNull { it.name == "privateMethod" })
            assertNull(firstOrNull { it.name == "packageMethod" })
            assertNotNull(firstOrNull { it.name == "privateFieldsAndMethods" })
            assertNotNull(firstOrNull { it.name == "publicMethod" })
            assertNotNull(firstOrNull { it.name == "protectedMethod" })
        }
    }

    @Test
    fun `virtual classes should work`() {
        val fakeClassName = "xxx.Fake"
        val fakeFieldName = "fakeField"
        val fakeMethodName = "fakeMethod"
        val cp = runBlocking {
            cp.db.classpath(allClasspath, listOf(VirtualClasses.builder {
                virtualClass(fakeClassName) {
                    field(fakeFieldName)
                    method(fakeMethodName) {
                        returnType(PredefinedPrimitives.Int)
                        params(PredefinedPrimitives.Int)
                    }
                }
            }))
        }
        val clazz = cp.findClass(fakeClassName)
        assertTrue(clazz is JcVirtualClass)
        with(clazz) {
            val field = findDeclaredFieldOrNull(fakeFieldName)
            assertTrue(field is JcVirtualField)
            assertNotNull(field?.enclosingClass)
        }
        with(clazz) {
            val method = findDeclaredMethodOrNull(fakeMethodName, "(I)I")
            assertTrue(method is JcVirtualMethod)
            assertNotNull(method?.enclosingClass)
        }

        val classes = cp.findClasses(fakeClassName)
        assertEquals(1, classes.size)
    }

    @Test
    fun `class override should work`() {
        val fakeClassName = "java.lang.String"
        val fakeMethodName = "fakeMethod"
        val cp = runBlocking {
            cp.db.classpath(allClasspath, listOf(VirtualClasses.builder {
                virtualClass(fakeClassName) {
                    method(fakeMethodName) {
                        returnType(PredefinedPrimitives.Int)
                        params(PredefinedPrimitives.Int)
                    }
                }
            }))
        }
        val clazz = cp.findClass(fakeClassName)
        assertTrue(clazz is JcVirtualClass)

        with(clazz) {
            val method = findDeclaredMethodOrNull(fakeMethodName, "(I)I")
            assertTrue(method is JcVirtualMethod)
            assertNotNull(method?.enclosingClass)
        }

        val classes = cp.findClasses(fakeClassName)
        assertEquals(2, classes.size)
    }

    @Test
    fun `virtual class methods override should work`() {
        val fakeClassName = "fakeClass"
        val fakeMethodName1 = "fakeMethod1"
        val fakeMethodName2 = "fakeMethod2"
        val cp = runBlocking {
            cp.db.classpath(allClasspath, listOf(
                VirtualClasses.builder {
                    virtualClass(fakeClassName) {
                        method(fakeMethodName1) {
                            returnType(PredefinedPrimitives.Int)
                            params(PredefinedPrimitives.Int)
                        }
                    }
                },
                VirtualClassContent.builder {
                    content {
                        matcher { it.name == fakeClassName }
                        method { builder, _ ->
                            builder.name(fakeMethodName2)
                            builder.returnType(PredefinedPrimitives.Int)
                            builder.params(PredefinedPrimitives.Int)
                        }
                    }
                }
            ))
        }
        val clazz = cp.findClass(fakeClassName)
        assertTrue(clazz is JcVirtualClass)

        with(clazz) {
            val method = findDeclaredMethodOrNull(fakeMethodName2, "(I)I")
            assertTrue(method is JcVirtualMethod)
            assertNotNull(method?.enclosingClass)
        }

        val classes = cp.findClasses(fakeClassName)
        assertEquals(1, classes.size)
    }

    @Test
    fun `virtual fields and methods of virtual classes`() {
        val fakeFieldName = "fakeField"
        val fakeMethodName = "fakeMethod"
        val cp = runBlocking {
            cp.db.classpath(
                allClasspath, listOf(
                    VirtualClassContent
                        .builder()
                        .content {
                            matcher { it.name == "java.lang.String" }
                            field { builder, _ ->
                                builder.name(fakeFieldName)
                                builder.type(PredefinedPrimitives.Int)
                            }
                            method { builder, _ ->
                                builder.name(fakeMethodName)
                                builder.returnType(PredefinedPrimitives.Int)
                                builder.params(PredefinedPrimitives.Int)
                            }
                        }.build()
                )
            )
        }
        val clazz = cp.findClass<String>()
        val field = clazz.findDeclaredFieldOrNull(fakeFieldName)
        assertTrue(field is JcVirtualField)
        assertEquals(PredefinedPrimitives.Int, field!!.type.typeName)
        assertNotNull(field.enclosingClass)

        val method = clazz.declaredMethods.first { it.name == fakeMethodName }
        assertTrue(method is JcVirtualMethod)
        assertEquals(PredefinedPrimitives.Int, method.returnType.typeName)
        assertEquals(1, method.parameters.size)
        assertEquals(PredefinedPrimitives.Int, method.parameters.first().type.typeName)
        assertNotNull(method.enclosingClass)
        method.parameters.forEach {
            assertNotNull(it.method)
        }
    }

    @Test
    fun `override existed field and method`() {
        val fieldName = "byteArray"
        val methodName = "smth"
        val byteArrayTypeName = PredefinedPrimitives.Byte + "[]"

        val cp = runBlocking {
            cp.db.classpath(
                allClasspath, listOf(
                    VirtualClassContent
                        .builder()
                        .content {
                            matcher { it.name == Bar::class.java.name }
                            field { builder, _ ->
                                builder.name(fieldName)
                                builder.type(PredefinedPrimitives.Int)
                            }
                            method { builder, _ ->
                                builder.name(methodName)
                                builder.returnType(byteArrayTypeName)
                                builder.params(byteArrayTypeName)
                            }
                        }.build()
                )
            )
        }
        val clazz = cp.findClass<Bar>()
        val field = clazz.findDeclaredFieldOrNull(fieldName)

        val fields = clazz.declaredFields.filter { it.name == fieldName }
        assertTrue(fields.size == 1)

        assertTrue(field is JcVirtualField)
        assertEquals(PredefinedPrimitives.Int, field!!.type.typeName)
        assertNotNull(field.enclosingClass)

        val method = clazz.declaredMethods.single { it.name == methodName }

        assertTrue(method is JcVirtualMethod)
        assertEquals(byteArrayTypeName, method.returnType.typeName)
        assertEquals(1, method.parameters.size)
        assertEquals(byteArrayTypeName, method.parameters.first().type.typeName)
        assertNotNull(method.enclosingClass)
        method.parameters.forEach {
            assertNotNull(it.method)
        }
    }

    @Test
    fun `class caching feature works for not existed class`() {
        val notExistedClass = "xxx.Xxx"
        val clazz = cp.findClassOrNull(notExistedClass)
        assertNull(clazz)
        val cache = cp.features?.first { it is ClasspathCache } as ClasspathCache
        val optional = cache.tryFindClass(cp, notExistedClass)
        assertNotNull(optional)
        assertNull(optional!!.clazz)
    }

    @Test
    fun `class caching feature works for existed class`() {
        val existedClass = "java.lang.String"
        val clazz = cp.findClassOrNull(existedClass)
        assertNotNull(clazz)
        val cache = cp.features?.first { it is ClasspathCache } as ClasspathCache
        val optional = cache.tryFindClass(cp, existedClass)
        assertNotNull(optional)
        assertNotNull(optional!!.clazz)
    }

    @Test
    fun `hidden fields`() {
        val hiddenFieldSuccClass = cp.findClass<HiddenFieldSuccClass>()
        assertTrue(hiddenFieldSuccClass.toType().fields.size == hiddenFieldSuccClass.fields.size)
    }

    @Test
    fun `static flag on classes`() {
        val aaa = cp.findClass<AAA>()

        val bbb = cp.findClass<AAA.BBB>()
        val ccc = cp.findClass<CCC>()
        assertFalse(bbb.isStatic)
        assertTrue(ccc.isStatic)

        assertTrue(ccc.innerClasses.isEmpty())
        assertTrue(bbb.innerClasses.isEmpty())

        val inners = aaa.innerClasses.toList()
        assertEquals(2, inners.size)
        assertTrue(inners.first { it.name.contains("CCC") }.isStatic)
        assertFalse(inners.first { it.name.contains("BBB") }.isStatic)
    }

    private inline fun <reified T> findSubClasses(allHierarchy: Boolean = false): Sequence<JcClassOrInterface> {
        return hierarchyExt.findSubClasses(T::class.java.name, allHierarchy)
    }
}