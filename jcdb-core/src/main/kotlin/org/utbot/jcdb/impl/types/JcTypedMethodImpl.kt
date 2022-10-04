package org.utbot.jcdb.impl.types

import org.objectweb.asm.tree.LocalVariableNode
import org.utbot.jcdb.api.JcClassOrInterface
import org.utbot.jcdb.api.JcMethod
import org.utbot.jcdb.api.JcRefType
import org.utbot.jcdb.api.JcType
import org.utbot.jcdb.api.JcTypeVariableDeclaration
import org.utbot.jcdb.api.JcTypedMethod
import org.utbot.jcdb.api.JcTypedMethodParameter
import org.utbot.jcdb.api.ext.findClass
import org.utbot.jcdb.impl.signature.MethodResolutionImpl
import org.utbot.jcdb.impl.signature.MethodSignature
import org.utbot.jcdb.impl.suspendableLazy

class JcTypedMethodImpl(
    override val ownerType: JcRefType,
    override val method: JcMethod,
    classBindings: JcTypeBindings = JcTypeBindings()
) : JcTypedMethod {


    private val classpath = method.enclosingClass.classpath

    private val methodBindingsGetter = suspendableLazy {
        classBindings.join(
            when (resolution) {
                is MethodResolutionImpl -> classpath.typeDeclarations(resolution.typeVariables)
                else -> emptyList()
            }
        )
    }
    private val resolution = MethodSignature.of(method.signature)

    override val name: String
        get() = method.name

    private suspend fun methodBindings() = methodBindingsGetter()

    override suspend fun exceptions(): List<JcClassOrInterface> = ifSignature {
        it.exceptionTypes.map {
            classpath.findClass(it.name)
        }
    } ?: emptyList()

    override suspend fun parameterization(): List<JcTypeVariableDeclaration> {
        return ifSignature {
            classpath.typeDeclarations(it.typeVariables)
        } ?: emptyList()
    }

    override suspend fun parameters(): List<JcTypedMethodParameter> {
        val bindings = methodBindings()
        return method.parameters.mapIndexed { index, jcParameter ->
            val stype = ifSignature { it.parameterTypes[index] }
            JcTypedMethodParameterImpl(
                ownerMethod = this,
                bindings = bindings,
                parameter = jcParameter,
                stype = stype
            )
        }
    }

    override suspend fun returnType(): JcType {
        val typeName = method.returnType.typeName
        return ifSignature {
            classpath.typeOf(it.returnType)
        } ?: classpath.findTypeOrNull(typeName) ?: throw IllegalStateException("Can't resolve type by name $typeName")
    }

    private suspend fun <T> ifSignature(map: suspend (MethodResolutionImpl) -> T): T? {
        return when (resolution) {
            is MethodResolutionImpl -> map(resolution)
            else -> null
        }
    }

    override suspend fun typeOf(inst: LocalVariableNode): JcType {
        TODO("Not yet implemented")
    }

}