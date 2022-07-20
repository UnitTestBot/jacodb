package org.utbot.jcdb.remote.rd

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.utbot.jcdb.api.*
import org.utbot.jcdb.compilationDatabase
import org.utbot.jcdb.impl.*
import org.utbot.jcdb.impl.hierarchies.Creature
import org.utbot.jcdb.impl.hierarchies.Creature.*
import org.utbot.jcdb.impl.index.findClassOrNull
import org.utbot.jcdb.impl.index.hierarchyExt
import org.utbot.jcdb.impl.signature.*
import org.utbot.jcdb.impl.types.ArrayClassIdImpl
import org.utbot.jcdb.impl.types.byte
import org.utbot.jcdb.impl.usages.Generics
import org.utbot.jcdb.impl.usages.HelloWorldAnonymousClasses
import org.utbot.jcdb.impl.usages.WithInner
import org.w3c.dom.Document
import org.w3c.dom.DocumentType
import java.util.*
import java.util.concurrent.ConcurrentHashMap

@Disabled
class RemoteClientTest {
    companion object : LibrariesMixin {
        var serverDB: CompilationDatabase? = runBlocking {
            compilationDatabase {
                predefinedDirOrJars = allClasspath
                useProcessJavaRuntime()
                exposeRd(8080)
            }.also {
                it.awaitBackgroundJobs()
            }
        }

        @AfterAll
        @JvmStatic
        fun cleanup() {
            serverDB?.close()
            serverDB = null
        }
    }

    private val db = rdDatabase(8080)

    private val cp = runBlocking { db.classpathSet(allClasspath) }

    @AfterEach
    fun close() {
        cp.close()
        db.close()
    }

    @Test
    fun `find class from build dir folder`() = runBlocking {
        val clazz = cp.findClass<Foo>()
        Assertions.assertEquals(Foo::class.java.name, clazz.name)
        Assertions.assertTrue(clazz.isFinal())
        Assertions.assertTrue(clazz.isPublic())
        Assertions.assertFalse(clazz.isInterface())

        val annotations = clazz.annotations()
        Assertions.assertTrue(annotations.size > 1)
        Assertions.assertNotNull(annotations.firstOrNull { it.name == Nested::class.java.name })

        val fields = clazz.fields()
        Assertions.assertEquals(2, fields.size)

        with(fields.first()) {
            Assertions.assertEquals("foo", name)
            Assertions.assertEquals("int", type().name)
        }
        with(fields[1]) {
            Assertions.assertEquals("bar", name)
            Assertions.assertEquals(String::class.java.name, type().name)
        }

        val methods = clazz.methods()
        Assertions.assertEquals(5, methods.size)
        with(methods.first { it.name == "smthPublic" }) {
            Assertions.assertEquals(1, parameters().size)
            Assertions.assertEquals("int", parameters().first().name)
            Assertions.assertTrue(isPublic())
        }

        with(methods.first { it.name == "smthPrivate" }) {
            Assertions.assertTrue(parameters().isEmpty())
            Assertions.assertTrue(isPrivate())
        }
    }

    @Test
    fun `array types`() = runBlocking {
        val clazz = cp.findClass<Bar>()
        Assertions.assertEquals(Bar::class.java.name, clazz.name)

        val fields = clazz.fields()
        Assertions.assertEquals(3, fields.size)

        with(fields.first()) {
            Assertions.assertEquals("byteArray", name)
            Assertions.assertEquals("byte[]", type().name)
            Assertions.assertEquals(cp.byte, (type() as ArrayClassIdImpl).elementClass)
        }

        with(fields.get(1)) {
            Assertions.assertEquals("objectArray", name)
            Assertions.assertEquals("Object[]", type().name)
            Assertions.assertEquals(cp.findClass<Any>(), (type() as ArrayClassIdImpl).elementClass)
        }

        with(fields.get(2)) {
            Assertions.assertEquals("objectObjectArray", name)
            Assertions.assertEquals("Object[][]", type().name)
            Assertions.assertEquals(
                cp.findClass<Any>(),
                ((type() as ArrayClassIdImpl).elementClass as ArrayClassIdImpl).elementClass
            )
        }

        val methods = clazz.methods()
        Assertions.assertEquals(2, methods.size)

        with(methods.first { it.name == "smth" }) {
            val parameters = parameters()
            Assertions.assertEquals(1, parameters.size)
            Assertions.assertEquals("byte[]", parameters.first().name)
            Assertions.assertEquals("byte[]", returnType().name)
        }
    }

    @Test
    fun `inner and static`() = runBlocking {
        val withInner = cp.findClass<WithInner>()
        val inner = cp.findClass<WithInner.Inner>()
        val staticInner = cp.findClass<WithInner.StaticInner>()

        val anon = cp.findClass("org.utbot.jcdb.impl.usages.WithInner$1")

        Assertions.assertEquals(withInner, anon.outerClass())
        Assertions.assertEquals(withInner, inner.outerClass())
        Assertions.assertEquals(withInner, staticInner.outerClass())
        Assertions.assertEquals(withInner.findMethodOrNull("sayHello", "()V"), anon.outerMethod())
        Assertions.assertNull(staticInner.outerMethod())
    }

    @Test
    fun `get signature of class`() = runBlocking {
        val a = cp.findClass<Generics<*>>()

        val classSignature = a.signature()

        with(classSignature) {
            this as TypeResolutionImpl
            Assertions.assertEquals("java.lang.Object", (superClass as RawType).name)
        }
    }

    @Test
    fun `get signature of methods`() = runBlocking {
        val a = cp.findClass<Generics<*>>()

        val methodSignatures = a.methods().map { it.name to it.signature() }
        Assertions.assertEquals(3, methodSignatures.size)
        with(methodSignatures[0]) {
            val (name, signature) = this
            Assertions.assertEquals("<init>", name)
            Assertions.assertEquals(Raw, signature)
        }
        with(methodSignatures[1]) {
            val (name, signature) = this
            Assertions.assertEquals("merge", name)
            signature as MethodResolutionImpl
            Assertions.assertEquals("void", (signature.returnType as PrimitiveType).ref.name)
            Assertions.assertEquals(1, signature.parameterTypes.size)
            with(signature.parameterTypes.first()) {
                this as ParameterizedType
                Assertions.assertEquals(Generics::class.java.name, this.name)
                Assertions.assertEquals(1, parameterTypes.size)
                with(parameterTypes.first()) {
                    this as TypeVariable
                    Assertions.assertEquals("T", this.symbol)
                }
            }
            Assertions.assertEquals(1, signature.parameterTypes.size)
            val parameterizedType = signature.parameterTypes.first() as ParameterizedType
            Assertions.assertEquals(1, parameterizedType.parameterTypes.size)
            Assertions.assertEquals(Generics::class.java.name, parameterizedType.name)
            val typeVariable = parameterizedType.parameterTypes.first() as TypeVariable
            Assertions.assertEquals("T", typeVariable.symbol)
        }
        with(methodSignatures[2]) {
            val (name, signature) = this
            Assertions.assertEquals("merge1", name)
            signature as MethodResolutionImpl
            Assertions.assertEquals("W", (signature.returnType as TypeVariable).symbol)

            Assertions.assertEquals(1, signature.typeVariables.size)
            with(signature.typeVariables.first()) {
                this as Formal
                Assertions.assertEquals("W", symbol)
                Assertions.assertEquals(1, boundTypeTokens?.size)
                with(boundTypeTokens!!.first()) {
                    this as ParameterizedType
                    Assertions.assertEquals("java.util.Collection", this.name)
                    Assertions.assertEquals(1, parameterTypes.size)
                    with(parameterTypes.first()) {
                        this as TypeVariable
                        Assertions.assertEquals("T", symbol)
                    }
                }
            }
            Assertions.assertEquals(1, signature.parameterTypes.size)
            val parameterizedType = signature.parameterTypes.first() as ParameterizedType
            Assertions.assertEquals(1, parameterizedType.parameterTypes.size)
            Assertions.assertEquals(Generics::class.java.name, parameterizedType.name)
            val typeVariable = parameterizedType.parameterTypes.first() as TypeVariable
            Assertions.assertEquals("T", typeVariable.symbol)
        }
    }

    @Test
    fun `get signature of fields`() = runBlocking {
        val a = cp.findClass<Generics<*>>()

        val fieldSignatures = a.fields().map { it.name to it.signature() }

        Assertions.assertEquals(2, fieldSignatures.size)

        with(fieldSignatures.first()) {
            val (name, signature) = this
            signature as FieldResolutionImpl
            val fieldType = signature.fieldType as TypeVariable
            Assertions.assertEquals("niceField", name)
            Assertions.assertEquals("T", fieldType.symbol)
        }
        with(fieldSignatures.get(1)) {
            val (name, signature) = this
            signature as FieldResolutionImpl
            val fieldType = signature.fieldType as ParameterizedType
            Assertions.assertEquals("niceList", name)
            Assertions.assertEquals("java.util.List", fieldType.name)
            with(fieldType.parameterTypes) {
                Assertions.assertEquals(1, size)
                with(first()) {
                    this as BoundWildcard.UpperBoundWildcard
                    val bondType = boundType as TypeVariable
                    Assertions.assertEquals("T", bondType.symbol)
                }
            }
            Assertions.assertEquals("java.util.List", fieldType.name)
        }
    }

    @Test
    fun `local and anonymous classes`() = runBlocking {
        val withAnonymous = cp.findClass<HelloWorldAnonymousClasses>()

        val helloWorld = cp.findClass<HelloWorldAnonymousClasses.HelloWorld>()
        Assertions.assertTrue(helloWorld.isMemberClass())

        val innerClasses = withAnonymous.innerClasses()
        Assertions.assertEquals(4, innerClasses.size)
        val notHelloWorld = innerClasses.filterNot { it.name.contains("\$HelloWorld") }
        val englishGreetings = notHelloWorld.first { it.name.contains("EnglishGreeting") }
        Assertions.assertTrue(englishGreetings.isLocal())
        Assertions.assertFalse(englishGreetings.isAnonymous())

        (notHelloWorld - englishGreetings).forEach {
            Assertions.assertFalse(it.isLocal())
            Assertions.assertTrue(it.isAnonymous())
            Assertions.assertFalse(it.isMemberClass())
        }
    }

    @Test
    fun `find lazy-loaded class`() = runBlocking {
        val domClass = cp.findClass<Document>()

        Assertions.assertTrue(domClass.isPublic())
        Assertions.assertTrue(domClass.isInterface())

        val methods = domClass.methods()
        Assertions.assertTrue(methods.isNotEmpty())
        with(methods.first { it.name == "getDoctype" }) {
            Assertions.assertTrue(parameters().isEmpty())
            Assertions.assertEquals(DocumentType::class.java.name, returnType().name)
            Assertions.assertTrue(isPublic())
        }
    }

    @Test
    fun `find sub-types from lazy loaded classes`() = runBlocking {
        with(cp.findSubClasses<AbstractMap<*, *>>()) {
            Assertions.assertTrue(size > 10) {
                "expected more then 10 but got only: ${joinToString { it.name }}"
            }

            Assertions.assertNotNull(firstOrNull { it.name == EnumMap::class.java.name })
            Assertions.assertNotNull(firstOrNull { it.name == HashMap::class.java.name })
            Assertions.assertNotNull(firstOrNull { it.name == WeakHashMap::class.java.name })
            Assertions.assertNotNull(firstOrNull { it.name == TreeMap::class.java.name })
            Assertions.assertNotNull(firstOrNull { it.name == ConcurrentHashMap::class.java.name })
        }

        with(cp.findSubClasses(Document::class.java.name)) {
            Assertions.assertTrue(isNotEmpty())
        }
    }

    @Test
    fun `find sub-types of array`() = runBlocking {
        val stringArray = cp.findClass("java.lang.String[]")

        with(cp.findSubClasses(stringArray, true)) {
            Assertions.assertTrue(isEmpty())
        }
    }

    @Test
    fun `find sub-types with all hierarchy`() = runBlocking {
        val clazz = cp.findClassOrNull<SuperDuper>()
        Assertions.assertNotNull(clazz!!)

        with(cp.findSubClasses(clazz, allHierarchy = true)) {
            Assertions.assertEquals(4, size) {
                "expected more then 10 but got only: ${joinToString { it.name }}"
            }

            Assertions.assertNotNull(firstOrNull { it.name == A::class.java.name })
            Assertions.assertNotNull(firstOrNull { it.name == B::class.java.name })
            Assertions.assertNotNull(firstOrNull { it.name == C::class.java.name })
            Assertions.assertNotNull(firstOrNull { it.name == D::class.java.name })
        }
    }

    @Test
    fun `find method overrides`() = runBlocking {
        val creatureClass = cp.findClass<Creature>()

        Assertions.assertEquals(2, creatureClass.methods().size)
        val sayMethod = creatureClass.methods().first { it.name == "say" }
        val helloMethod = creatureClass.methods().first { it.name == "hello" }

        var overrides = cp.hierarchyExt.findOverrides(sayMethod)

        with(overrides) {
            Assertions.assertEquals(4, size)

            Assertions.assertNotNull(firstOrNull { it.classId == cp.findClass<DinosaurImpl>() })
            Assertions.assertNotNull(firstOrNull { it.classId == cp.findClass<Fish>() })
            Assertions.assertNotNull(firstOrNull { it.classId == cp.findClass<TRex>() })
            Assertions.assertNotNull(firstOrNull { it.classId == cp.findClass<Pterodactyl>() })
        }
        overrides = cp.hierarchyExt.findOverrides(helloMethod)
        with(overrides) {
            Assertions.assertEquals(1, size)

            Assertions.assertNotNull(firstOrNull { it.classId == cp.findClass<TRex>() })

        }
    }


}

