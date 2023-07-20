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

package org.jacodb.impl.types

import org.jacodb.api.*
import org.jacodb.api.ext.findTypeOrNull
import org.jacodb.api.ext.isEnum
import org.jacodb.api.ext.isNullable
import org.jacodb.impl.bytecode.JcAnnotationImpl
import org.jacodb.impl.bytecode.JcMethodImpl
import org.jacodb.impl.types.signature.FieldResolutionImpl
import org.jacodb.impl.types.signature.FieldSignature
import org.jacodb.impl.types.signature.MethodResolutionImpl
import org.jacodb.impl.types.signature.MethodSignature
import org.jacodb.impl.types.substition.JcSubstitutor
import org.objectweb.asm.Type
import org.objectweb.asm.tree.LocalVariableNode

class JcTypedMethodImpl(
    override val enclosingType: JcRefType,
    override val method: JcMethod,
    private val parentSubstitutor: JcSubstitutor
) : JcTypedMethod {

    private class TypedMethodInfo(
        val substitutor: JcSubstitutor,
        private val resolution: MethodResolution
    ) {
        val impl: MethodResolutionImpl? get() = resolution as? MethodResolutionImpl
    }

    private val classpath = method.enclosingClass.classpath

    override val access: Int
        get() = this.method.access

    private val info by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val signature = MethodSignature.withDeclarations(method)
        val impl = signature as? MethodResolutionImpl
        val substitutor = if (!method.isStatic) {
            parentSubstitutor.newScope(impl?.typeVariables.orEmpty())
        } else {
            JcSubstitutor.empty.newScope(impl?.typeVariables.orEmpty())
        }

        TypedMethodInfo(
            substitutor = substitutor,
            resolution = signature
        )
    }

    override val name: String
        get() = method.name

    override val typeParameters: List<JcTypeVariableDeclaration>
        get() {
            val impl = info.impl ?: return emptyList()
            return impl.typeVariables.map { it.asJcDeclaration(method) }
        }

    override val exceptions: List<JcRefType>
        get() {
            val typesFromSignature = info.impl?.exceptionTypes?.map {
                classpath.typeOf(info.substitutor.substitute(it)) as JcRefType
            } ?: emptyList()

            return typesFromSignature.ifEmpty {
                method.exceptions.map { classpath.findTypeOrNull(it) as JcRefType }
            }
        }

    override val typeArguments: List<JcRefType>
        get() {
            return emptyList()
        }

    override val parameters: List<JcTypedMethodParameter>
        get() {
            val methodInfo = info
            if (method.isConstructor && method.enclosingClass.isEnum) {
                return method.parameters.map { jcParameter ->
                    JcTypedMethodParameterImpl(
                        enclosingMethod = this,
                        substitutor = methodInfo.substitutor,
                        parameter = jcParameter,
                        jvmType = null
                    )
                }
            }

            return method.parameters.mapIndexed { index, jcParameter ->
                JcTypedMethodParameterImpl(
                    enclosingMethod = this,
                    substitutor = methodInfo.substitutor,
                    parameter = jcParameter,
                    jvmType = methodInfo.impl?.parameterTypes?.getOrNull(index)
                )
            }
        }

    override val returnType: JcType by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val typeName = method.returnType.typeName
        val info = info
        val impl = info.impl
        val type = if (impl == null) {
            classpath.findTypeOrNull(typeName)
                ?.copyWithAnnotations(
                    (method as? JcMethodImpl)?.returnTypeAnnotationInfos?.map { JcAnnotationImpl(it, classpath) }
                        ?: listOf()
                )
                ?: throw IllegalStateException("Can't resolve type by name $typeName")
        } else {
            classpath.typeOf(info.substitutor.substitute(impl.returnType))
        }

        method.isNullable?.let {
            (type as? JcRefType)?.copyWithNullability(it)
        } ?: type
    }

    override fun typeOf(inst: LocalVariableNode): JcType {
        val variableSignature =
            FieldSignature.of(inst.signature, method.allVisibleTypeParameters(), null) as? FieldResolutionImpl
        if (variableSignature == null) {
            val type = Type.getType(inst.desc)
            return classpath.findTypeOrNull(type.className) ?: type.className.throwClassNotFound()
        }
        val info = info
        return classpath.typeOf(info.substitutor.substitute(variableSignature.fieldType))
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is JcTypedMethodImpl) return false

        if (enclosingType != other.enclosingType) return false
        if (method != other.method) return false
        return typeArguments == other.typeArguments
    }

    override fun hashCode(): Int {
        var result = enclosingType.hashCode()
        result = 31 * result + method.hashCode()
        return result
    }


}