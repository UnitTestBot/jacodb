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

package org.utbot.jacodb.impl.types.signature

import mu.KLogging
import org.objectweb.asm.signature.SignatureVisitor
import org.utbot.jacodb.api.JcMethod
import org.utbot.jacodb.api.Malformed
import org.utbot.jacodb.api.MethodResolution
import org.utbot.jacodb.api.Pure
import org.utbot.jacodb.impl.bytecode.JcMethodImpl
import org.utbot.jacodb.impl.bytecode.kmFunction
import org.utbot.jacodb.impl.bytecode.kmReturnType
import org.utbot.jacodb.impl.bytecode.kmType
import org.utbot.jacodb.impl.types.allVisibleTypeParameters
import org.utbot.jacodb.impl.types.substition.JvmTypeVisitor
import org.utbot.jacodb.impl.types.substition.fixDeclarationVisitor

val logger = object : KLogging() {}.logger

internal class MethodSignature(private val method: JcMethod) : Signature<MethodResolution>(method, method.kmFunction?.typeParameters) {

    private val parameterTypes = ArrayList<JvmType>()
    private val exceptionTypes = ArrayList<JvmClassRefType>()
    private lateinit var returnType: JvmType

    override fun visitParameterType(): SignatureVisitor {
        return TypeExtractor(ParameterTypeRegistrant())
    }

    override fun visitReturnType(): SignatureVisitor {
        collectTypeParameter()
        return TypeExtractor(ReturnTypeTypeRegistrant())
    }

    override fun visitExceptionType(): SignatureVisitor {
        return TypeExtractor(ExceptionTypeRegistrant())
    }

    override fun resolve(): MethodResolution {
        return MethodResolutionImpl(
            returnType,
            parameterTypes,
            exceptionTypes,
            typeVariables
        )
    }

    private inner class ParameterTypeRegistrant : TypeRegistrant {
        override fun register(token: JvmType) {
            var outToken = method.parameters[parameterTypes.size].kmType?.let {
                token.relaxWithKmType(it)
            } ?: token

            (method as? JcMethodImpl)?.let {
                outToken = outToken.relaxWithAnnotations(it.parameterTypeAnnotations(parameterTypes.size), it.enclosingClass.classpath)
            }

            parameterTypes.add(outToken)
        }
    }

    private inner class ReturnTypeTypeRegistrant : TypeRegistrant {
        override fun register(token: JvmType) {
            returnType = method.kmReturnType?.let { token.relaxWithKmType(it) } ?: token

            (method as? JcMethodImpl)?.let {
                returnType = returnType.relaxWithAnnotations(it.returnTypeAnnotations, it.enclosingClass.classpath)
            }
        }
    }

    private inner class ExceptionTypeRegistrant : TypeRegistrant {
        override fun register(token: JvmType) {
            exceptionTypes.add(token as JvmClassRefType)
        }
    }

    companion object {

        private fun MethodResolutionImpl.apply(visitor: JvmTypeVisitor) = MethodResolutionImpl(
            visitor.visitType(returnType),
            parameterTypes.map { visitor.visitType(it) },
            exceptionTypes,
            typeVariables.map { visitor.visitDeclaration(it) }
        )

        fun of(jcMethod: JcMethod): MethodResolution {
            val signature = jcMethod.signature
            signature ?: return Pure
            return try {
                of(signature, MethodSignature(jcMethod))
            } catch (ignored: RuntimeException) {
                Malformed
            }
        }

        fun withDeclarations(jcMethod: JcMethod): MethodResolution {
            val signature = jcMethod.signature
            signature ?: return Pure
            return try {
                of(signature, MethodSignature(jcMethod)).let {
                    if (it is MethodResolutionImpl) {
                        it.apply(jcMethod.allVisibleTypeParameters().fixDeclarationVisitor)
                    } else {
                        it
                    }
                }
            } catch (ignored: RuntimeException) {
                logger.warn { ignored.printStackTrace() }
                Malformed
            }
        }
    }
}