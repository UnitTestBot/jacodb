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

import kotlinx.coroutines.runBlocking
import org.jacodb.testing.usages.Generics
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.utbot.jacodb.api.JcClassOrInterface
import org.utbot.jacodb.api.JcField
import org.utbot.jacodb.api.JcMethod
import org.utbot.jacodb.api.Pure
import org.utbot.jacodb.api.ext.findClass
import org.utbot.jacodb.impl.types.signature.*
import org.utbot.jacodb.impl.types.typeParameters


class SignatureTest: BaseTest() {

    companion object : WithDB()

    @Test
    fun `get signature of class`() = runBlocking {
        val a = cp.findClass<Generics<*>>()

        val classSignature = a.resolution

        with(classSignature) {
            this as TypeResolutionImpl
            assertEquals("java.lang.Object", (superClass as JvmClassRefType).name)
        }
    }

    @Test
    fun `get signature of methods`() = runBlocking {
        val a = cp.findClass<Generics<*>>()

        val methodSignatures = a.declaredMethods.map { it.name to it.resolution }
        assertEquals(3, methodSignatures.size)
        with(methodSignatures[0]) {
            val (name, signature) = this
            assertEquals("<init>", name)
            assertEquals(Pure, signature)
        }
        with(methodSignatures[1]) {
            val (name, signature) = this
            assertEquals("merge", name)
            signature as MethodResolutionImpl
            assertEquals("void", (signature.returnType as JvmPrimitiveType).ref)
            assertEquals(1, signature.parameterTypes.size)
            with(signature.parameterTypes.first()) {
                this as JvmParameterizedType
                assertEquals(Generics::class.java.name, this.name)
                assertEquals(1, parameterTypes.size)
                with(parameterTypes.first()) {
                    this as JvmTypeVariable
                    assertEquals("T", this.symbol)
                }
            }
            assertEquals(1, signature.parameterTypes.size)
            val parameterizedType = signature.parameterTypes.first() as JvmParameterizedType
            assertEquals(1, parameterizedType.parameterTypes.size)
            assertEquals(Generics::class.java.name, parameterizedType.name)
            val typeVariable = parameterizedType.parameterTypes.first() as JvmTypeVariable
            assertEquals("T", typeVariable.symbol)
        }
        with(methodSignatures[2]) {
            val (name, signature) = this
            assertEquals("merge1", name)
            signature as MethodResolutionImpl
            assertEquals("W", (signature.returnType as JvmTypeVariable).symbol)

            assertEquals(1, signature.typeVariables.size)
            with(signature.typeVariables.first()) {
                this as JvmTypeParameterDeclarationImpl
                assertEquals("W", symbol)
                assertEquals(1, bounds?.size)
                with(bounds!!.first()) {
                    this as JvmParameterizedType
                    assertEquals("java.util.Collection", this.name)
                    assertEquals(1, parameterTypes.size)
                    with(parameterTypes.first()) {
                        this as JvmTypeVariable
                        assertEquals("T", symbol)
                    }
                }
            }
            assertEquals(1, signature.parameterTypes.size)
            val parameterizedType = signature.parameterTypes.first() as JvmParameterizedType
            assertEquals(1, parameterizedType.parameterTypes.size)
            assertEquals(Generics::class.java.name, parameterizedType.name)
            val variable = parameterizedType.parameterTypes.first() as JvmTypeVariable
            assertEquals("T", variable.symbol)
        }
    }

    @Test
    fun `get signature of fields`() = runBlocking {
        val a = cp.findClass<Generics<*>>()

        val fieldSignatures = a.declaredFields.map { it.name to it.resolution }

        assertEquals(2, fieldSignatures.size)

        with(fieldSignatures.first()) {
            val (name, signature) = this
            signature as FieldResolutionImpl
            val fieldType = signature.fieldType as JvmTypeVariable
            assertEquals("niceField", name)
            assertEquals("T", fieldType.symbol)
        }
        with(fieldSignatures.get(1)) {
            val (name, signature) = this
            signature as FieldResolutionImpl
            val fieldType = signature.fieldType as JvmParameterizedType
            assertEquals("niceList", name)
            assertEquals("java.util.List", fieldType.name)
            with(fieldType.parameterTypes) {
                assertEquals(1, size)
                with(first()) {
                    this as JvmBoundWildcard.JvmUpperBoundWildcard
                    val bondType = bound as JvmTypeVariable
                    assertEquals("T", bondType.symbol)
                }
            }
            assertEquals("java.util.List", fieldType.name)
        }
    }


    private val JcClassOrInterface.resolution get() = TypeSignature.of(this)
    private val JcMethod.resolution get() = MethodSignature.of(this)
    private val JcField.resolution get() = FieldSignature.of(signature, enclosingClass.typeParameters.associateBy { it.symbol }, this)
}

