package org.utbot.jcdb.impl.types

import org.objectweb.asm.Type
import org.objectweb.asm.tree.LocalVariableNode
import org.utbot.jcdb.api.JcClassOrInterface
import org.utbot.jcdb.api.JcMethod
import org.utbot.jcdb.api.JcRefType
import org.utbot.jcdb.api.JcType
import org.utbot.jcdb.api.JcTypeVariableDeclaration
import org.utbot.jcdb.api.JcTypedMethod
import org.utbot.jcdb.api.JcTypedMethodParameter
import org.utbot.jcdb.api.ext.findClass
import org.utbot.jcdb.api.isStatic
import org.utbot.jcdb.api.throwClassNotFound
import org.utbot.jcdb.impl.types.signature.FieldResolutionImpl
import org.utbot.jcdb.impl.types.signature.FieldSignature
import org.utbot.jcdb.impl.types.signature.MethodResolutionImpl
import org.utbot.jcdb.impl.types.signature.MethodSignature
import org.utbot.jcdb.impl.types.substition.JcSubstitutor

class JcTypedMethodImpl(
    override val enclosingType: JcRefType,
    override val method: JcMethod,
    jcSubstitutor: JcSubstitutor
) : JcTypedMethod {

    private val resolution = MethodSignature.of(method)
    private val impl = resolution as? MethodResolutionImpl
    private val classpath = method.enclosingClass.classpath

    override val name: String
        get() = method.name

    private val substitutor = resolveSubstitutor(jcSubstitutor)

    private fun resolveSubstitutor(parent: JcSubstitutor): JcSubstitutor {
        return if (!method.isStatic) {
            parent.newScope(impl?.typeVariables.orEmpty())
        } else {
            JcSubstitutor.empty.newScope(impl?.typeVariables.orEmpty())
        }
    }

    override suspend fun typeParameters(): List<JcTypeVariableDeclaration> {
        if (impl == null) {
            return emptyList()
        }
        return impl.typeVariables.map { it.asJcDeclaration(method) }
    }

    override suspend fun exceptions(): List<JcClassOrInterface> = impl?.exceptionTypes?.map {
        classpath.findClass(it.name)
    } ?: emptyList()

    override suspend fun typeArguments(): List<JcRefType> {
        return emptyList()
    }

    override suspend fun parameters(): List<JcTypedMethodParameter> {
        return method.parameters.mapIndexed { index, jcParameter ->
            JcTypedMethodParameterImpl(
                enclosingMethod = this,
                substitutor = substitutor,
                parameter = jcParameter,
                jvmType = impl?.parameterTypes?.get(index)
            )
        }
    }

    override suspend fun returnType(): JcType {
        val typeName = method.returnType.typeName
        if (impl == null) {
            return classpath.findTypeOrNull(typeName)
                ?: throw IllegalStateException("Can't resolve type by name $typeName")
        }
        return classpath.typeOf(substitutor.substitute(impl.returnType))
    }

    override suspend fun typeOf(inst: LocalVariableNode): JcType {
        val variableSignature = FieldSignature.of(inst.signature, method) as? FieldResolutionImpl
        if (variableSignature == null) {
            val type = Type.getType(inst.desc)
            return classpath.findTypeOrNull(type.className) ?: type.className.throwClassNotFound()
        }
        return classpath.typeOf(substitutor.substitute(variableSignature.fieldType))
    }

}