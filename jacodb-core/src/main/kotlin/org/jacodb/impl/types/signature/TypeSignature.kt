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

package org.jacodb.impl.types.signature

import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JvmType
import org.jacodb.api.Malformed
import org.jacodb.api.Pure
import org.jacodb.api.TypeResolution
import org.jacodb.impl.bytecode.kMetadata
import org.jacodb.impl.types.allVisibleTypeParameters
import org.jacodb.impl.types.substition.RecursiveJvmTypeVisitor
import org.jacodb.impl.types.substition.fixDeclarationVisitor
import org.objectweb.asm.signature.SignatureVisitor

internal class TypeSignature(jcClass: JcClassOrInterface) :
    Signature<TypeResolution>(jcClass, jcClass.kMetadata?.kmTypeParameters) {

    private val interfaceTypes = ArrayList<JvmType>()
    private lateinit var superClass: JvmType

    override fun visitSuperclass(): SignatureVisitor {
        collectTypeParameter()
        return TypeExtractor(SuperClassRegistrant())
    }

    override fun visitInterface(): SignatureVisitor {
        return TypeExtractor(InterfaceTypeRegistrant())
    }

    override fun resolve(): TypeResolution {
        return TypeResolutionImpl(superClass, interfaceTypes, typeVariables)
    }

    private inner class SuperClassRegistrant : TypeRegistrant {

        override fun register(token: JvmType) {
            superClass = token
        }
    }

    private inner class InterfaceTypeRegistrant : TypeRegistrant {

        override fun register(token: JvmType) {
            interfaceTypes.add(token)
        }
    }

    companion object {

        private fun TypeResolutionImpl.apply(visitor: RecursiveJvmTypeVisitor) = TypeResolutionImpl(
            visitor.visitType(superClass),
            interfaceType.map { visitor.visitType(it) },
            typeVariables.map { visitor.visitDeclaration(it) }
        )

        fun of(jcClass: JcClassOrInterface): TypeResolution {
            val signature = jcClass.signature ?: return Pure
            return try {
                of(signature, TypeSignature(jcClass))
            } catch (ignored: RuntimeException) {
                Malformed
            }
        }

        fun withDeclarations(jcClass: JcClassOrInterface): TypeResolution {
            val signature = jcClass.signature ?: return Pure
            return try {
                of(signature, TypeSignature(jcClass)).let {
                    if (it is TypeResolutionImpl) {
                        it.apply(jcClass.allVisibleTypeParameters().fixDeclarationVisitor)
                    } else {
                        it
                    }
                }
            } catch (ignored: RuntimeException) {
                Malformed
            }
        }
    }
}
