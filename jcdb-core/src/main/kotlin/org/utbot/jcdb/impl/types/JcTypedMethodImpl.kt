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
import org.utbot.jcdb.api.throwClassNotFound
import org.utbot.jcdb.impl.signature.FieldResolutionImpl
import org.utbot.jcdb.impl.signature.FieldSignature
import org.utbot.jcdb.impl.signature.Formal
import org.utbot.jcdb.impl.signature.MethodResolutionImpl
import org.utbot.jcdb.impl.signature.MethodSignature

class JcTypedMethodImpl(
    override val enclosingType: JcRefType,
    override val method: JcMethod,
    classBindings: JcTypeBindings
) : JcTypedMethod {

    private val resolution = MethodSignature.of(method.signature) as? MethodResolutionImpl
    private val classpath = method.enclosingClass.classpath

    override val name: String
        get() = method.name

    private val methodBindings = classBindings.override(resolution?.typeVariables.orEmpty())

    override suspend fun originalParameterization(): List<JcTypeVariableDeclaration> {
        if (resolution == null) {
            return emptyList()
        }
        return classpath.typeDeclarations(resolution.typeVariables.map {
            Formal(it.symbol, it.boundTypeTokens?.map { it.apply(methodBindings, null) })
        }, JcTypeBindings.empty)
    }

    override suspend fun exceptions(): List<JcClassOrInterface> = resolution?.exceptionTypes?.map {
        classpath.findClass(it.name)
    } ?: emptyList()

    override suspend fun parameterization(): Map<String, JcRefType> {
        return emptyMap()
    }

    override suspend fun parameters(): List<JcTypedMethodParameter> {
        return method.parameters.mapIndexed { index, jcParameter ->
            val stype = resolution?.parameterTypes?.get(index)
            JcTypedMethodParameterImpl(
                enclosingMethod = this,
                bindings = methodBindings,
                parameter = jcParameter,
                stype = stype
            )
        }
    }

    override suspend fun returnType(): JcType {
        val typeName = method.returnType.typeName
        if(resolution == null) {
            return classpath.findTypeOrNull(typeName)
                ?: throw IllegalStateException("Can't resolve type by name $typeName")
        }
        return methodBindings.toJcRefType(resolution.returnType, classpath)
    }

    override suspend fun typeOf(inst: LocalVariableNode): JcType {
        val variableSignature = FieldSignature.of(inst.signature) as? FieldResolutionImpl
        if (variableSignature == null) {
            val type = Type.getType(inst.desc)
            return classpath.findTypeOrNull(type.className) ?: type.className.throwClassNotFound()
        }
        return methodBindings.toJcRefType(variableSignature.fieldType, classpath)
    }

}