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

package org.utbot.jacodb.impl.types

import org.objectweb.asm.Type
import org.objectweb.asm.tree.LocalVariableNode
import org.utbot.jacodb.api.JcClassOrInterface
import org.utbot.jacodb.api.JcMethod
import org.utbot.jacodb.api.JcRefType
import org.utbot.jacodb.api.JcType
import org.utbot.jacodb.api.JcTypeVariableDeclaration
import org.utbot.jacodb.api.JcTypedMethod
import org.utbot.jacodb.api.JcTypedMethodParameter
import org.utbot.jacodb.api.MethodResolution
import org.utbot.jacodb.api.ext.findClass
import org.utbot.jacodb.api.ext.isNullable
import org.utbot.jacodb.api.ext.isStatic
import org.utbot.jacodb.api.throwClassNotFound
import org.utbot.jacodb.impl.bytecode.JcAnnotationImpl
import org.utbot.jacodb.impl.bytecode.JcMethodImpl
import org.utbot.jacodb.impl.types.signature.FieldResolutionImpl
import org.utbot.jacodb.impl.types.signature.FieldSignature
import org.utbot.jacodb.impl.types.signature.MethodResolutionImpl
import org.utbot.jacodb.impl.types.signature.MethodSignature
import org.utbot.jacodb.impl.types.substition.JcSubstitutor

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

    private val info by lazy(LazyThreadSafetyMode.NONE) {
        val signature = MethodSignature.withDeclarations(method)
        val impl = signature as? MethodResolutionImpl
        val substitutor = if (!method.isStatic) {
            parentSubstitutor.newScope(impl?.typeVariables.orEmpty())
        } else {
            JcSubstitutor.empty.newScope(impl?.typeVariables.orEmpty())
        }

        TypedMethodInfo(
            substitutor = substitutor,
            resolution = MethodSignature.withDeclarations(method)
        )
    }

    override val name: String
        get() = method.name

    override val typeParameters: List<JcTypeVariableDeclaration>
        get() {
            val impl = info.impl ?: return emptyList()
            return impl.typeVariables.map { it.asJcDeclaration(method) }
        }

    override val exceptions: List<JcClassOrInterface>
        get() {
            val impl = info.impl ?: return emptyList()
            return impl.exceptionTypes.map {
                classpath.findClass(it.name)
            }
        }

    override val typeArguments: List<JcRefType>
        get() {
            return emptyList()
        }

    override val parameters: List<JcTypedMethodParameter>
        get() {
            val methodInfo = info
            return method.parameters.mapIndexed { index, jcParameter ->
                JcTypedMethodParameterImpl(
                    enclosingMethod = this,
                    substitutor = methodInfo.substitutor,
                    parameter = jcParameter,
                    jvmType = methodInfo.impl?.parameterTypes?.get(index)
                )
            }
        }

    override val returnType: JcType by lazy(LazyThreadSafetyMode.NONE) {
        val typeName = method.returnType.typeName
        val info = info
        val impl = info.impl
        val type = if (impl == null) {
            classpath.findTypeOrNull(typeName)
                ?.copyWithAnnotations(
                    (method as? JcMethodImpl)?.returnTypeAnnotationInfos?.map { JcAnnotationImpl(it, classpath) } ?: listOf()
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

}