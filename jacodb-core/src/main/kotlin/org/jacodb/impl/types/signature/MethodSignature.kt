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

import mu.KLogging
import org.jacodb.api.*
import org.jacodb.impl.bytecode.JcMethodImpl
import org.jacodb.impl.bytecode.kmFunction
import org.jacodb.impl.bytecode.kmReturnType
import org.jacodb.impl.bytecode.kmType
import org.jacodb.impl.types.allVisibleTypeParameters
import org.jacodb.impl.types.substition.RecursiveJvmTypeVisitor
import org.jacodb.impl.types.substition.fixDeclarationVisitor
import org.objectweb.asm.signature.SignatureVisitor

val logger = object : KLogging() {}.logger

internal class MethodSignature(private val method: JcMethod) :
    Signature<MethodResolution>(method, method.kmFunction?.typeParameters) {

    private val parameterTypes = ArrayList<JvmType>()
    private val exceptionTypes = ArrayList<JvmRefType>()
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
                JvmTypeKMetadataUpdateVisitor.visitType(token, it)
            } ?: token

            (method as? JcMethodImpl)?.let {
                outToken = outToken.withTypeAnnotations(it.parameterTypeAnnotationInfos(parameterTypes.size), it.enclosingClass.classpath)
            }

            parameterTypes.add(outToken)
        }
    }

    private inner class ReturnTypeTypeRegistrant : TypeRegistrant {
        override fun register(token: JvmType) {
            returnType = method.kmReturnType?.let { JvmTypeKMetadataUpdateVisitor.visitType(token, it) } ?: token

            (method as? JcMethodImpl)?.let {
                returnType = returnType.withTypeAnnotations(it.returnTypeAnnotationInfos, it.enclosingClass.classpath)
            }
        }
    }

    private inner class ExceptionTypeRegistrant : TypeRegistrant {
        override fun register(token: JvmType) {
            exceptionTypes.add(token as JvmRefType)
        }
    }

    companion object : KLogging() {

        private fun MethodResolutionImpl.apply(visitor: RecursiveJvmTypeVisitor) = MethodResolutionImpl(
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
                logger.warn(ignored) { "Can't parse signature '$signature' of field $jcMethod" }
                Malformed
            }
        }
    }
}