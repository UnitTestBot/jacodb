package org.utbot.jcdb.impl

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.utbot.jcdb.api.JCDB
import org.utbot.jcdb.api.JcClassOrInterface
import org.utbot.jcdb.api.JcField
import org.utbot.jcdb.api.JcMethod
import org.utbot.jcdb.api.Pure
import org.utbot.jcdb.api.ext.findClass
import org.utbot.jcdb.impl.signature.FieldResolutionImpl
import org.utbot.jcdb.impl.signature.FieldSignature
import org.utbot.jcdb.impl.signature.Formal
import org.utbot.jcdb.impl.signature.MethodResolutionImpl
import org.utbot.jcdb.impl.signature.MethodSignature
import org.utbot.jcdb.impl.signature.SBoundWildcard
import org.utbot.jcdb.impl.signature.SClassRefType
import org.utbot.jcdb.impl.signature.SParameterizedType
import org.utbot.jcdb.impl.signature.SPrimitiveType
import org.utbot.jcdb.impl.signature.STypeVariable
import org.utbot.jcdb.impl.signature.TypeResolutionImpl
import org.utbot.jcdb.impl.signature.TypeSignature
import org.utbot.jcdb.impl.usages.Generics
import org.utbot.jcdb.jcdb

class SignatureTest {
    companion object : LibrariesMixin {
        var db: JCDB? = runBlocking {
            jcdb {
                predefinedDirOrJars = allClasspath
                useProcessJavaRuntime()
            }.also {
                it.awaitBackgroundJobs()
            }
        }

        @AfterAll
        @JvmStatic
        fun cleanup() {
            db?.close()
            db = null
        }
    }

    private val cp = runBlocking { db!!.classpath(allClasspath) }

    @AfterEach
    fun close() {
        cp.close()
    }

    @Test
    fun `get signature of class`() = runBlocking {
        val a = cp.findClass<Generics<*>>()

        val classSignature = a.resolution

        with(classSignature) {
            this as TypeResolutionImpl
            Assertions.assertEquals("java.lang.Object", (superClass as SClassRefType).name)
        }
    }

    @Test
    fun `get signature of methods`() = runBlocking {
        val a = cp.findClass<Generics<*>>()

        val methodSignatures = a.methods.map { it.name to it.resolution }
        Assertions.assertEquals(3, methodSignatures.size)
        with(methodSignatures[0]) {
            val (name, signature) = this
            Assertions.assertEquals("<init>", name)
            Assertions.assertEquals(Pure, signature)
        }
        with(methodSignatures[1]) {
            val (name, signature) = this
            Assertions.assertEquals("merge", name)
            signature as MethodResolutionImpl
            Assertions.assertEquals("void", (signature.returnType as SPrimitiveType).ref)
            Assertions.assertEquals(1, signature.parameterTypes.size)
            with(signature.parameterTypes.first()) {
                this as SParameterizedType
                Assertions.assertEquals(Generics::class.java.name, this.name)
                Assertions.assertEquals(1, parameterTypes.size)
                with(parameterTypes.first()) {
                    this as STypeVariable
                    Assertions.assertEquals("T", this.symbol)
                }
            }
            Assertions.assertEquals(1, signature.parameterTypes.size)
            val parameterizedType = signature.parameterTypes.first() as SParameterizedType
            Assertions.assertEquals(1, parameterizedType.parameterTypes.size)
            Assertions.assertEquals(Generics::class.java.name, parameterizedType.name)
            val STypeVariable = parameterizedType.parameterTypes.first() as STypeVariable
            Assertions.assertEquals("T", STypeVariable.symbol)
        }
        with(methodSignatures[2]) {
            val (name, signature) = this
            Assertions.assertEquals("merge1", name)
            signature as MethodResolutionImpl
            Assertions.assertEquals("W", (signature.returnType as STypeVariable).symbol)

            Assertions.assertEquals(1, signature.typeVariables.size)
            with(signature.typeVariables.first()) {
                this as Formal
                Assertions.assertEquals("W", symbol)
                Assertions.assertEquals(1, boundTypeTokens?.size)
                with(boundTypeTokens!!.first()) {
                    this as SParameterizedType
                    Assertions.assertEquals("java.util.Collection", this.name)
                    Assertions.assertEquals(1, parameterTypes.size)
                    with(parameterTypes.first()) {
                        this as STypeVariable
                        Assertions.assertEquals("T", symbol)
                    }
                }
            }
            Assertions.assertEquals(1, signature.parameterTypes.size)
            val parameterizedType = signature.parameterTypes.first() as SParameterizedType
            Assertions.assertEquals(1, parameterizedType.parameterTypes.size)
            Assertions.assertEquals(Generics::class.java.name, parameterizedType.name)
            val STypeVariable = parameterizedType.parameterTypes.first() as STypeVariable
            Assertions.assertEquals("T", STypeVariable.symbol)
        }
    }

    @Test
    fun `get signature of fields`() = runBlocking {
        val a = cp.findClass<Generics<*>>()

        val fieldSignatures = a.fields.map { it.name to it.resolution }

        Assertions.assertEquals(2, fieldSignatures.size)

        with(fieldSignatures.first()) {
            val (name, signature) = this
            signature as FieldResolutionImpl
            val fieldType = signature.fieldType as STypeVariable
            Assertions.assertEquals("niceField", name)
            Assertions.assertEquals("T", fieldType.symbol)
        }
        with(fieldSignatures.get(1)) {
            val (name, signature) = this
            signature as FieldResolutionImpl
            val fieldType = signature.fieldType as SParameterizedType
            Assertions.assertEquals("niceList", name)
            Assertions.assertEquals("java.util.List", fieldType.name)
            with(fieldType.parameterTypes) {
                Assertions.assertEquals(1, size)
                with(first()) {
                    this as SBoundWildcard.UpperSBoundWildcard
                    val bondType = boundType as STypeVariable
                    Assertions.assertEquals("T", bondType.symbol)
                }
            }
            Assertions.assertEquals("java.util.List", fieldType.name)
        }
    }


    private val JcClassOrInterface.resolution get() = TypeSignature.of(signature)
    private val JcMethod.resolution get() = MethodSignature.of(signature)
    private val JcField.resolution get() = FieldSignature.of(signature)
}

