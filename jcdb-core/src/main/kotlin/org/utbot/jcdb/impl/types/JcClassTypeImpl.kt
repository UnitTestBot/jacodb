package org.utbot.jcdb.impl.types

import org.utbot.jcdb.api.JcClassOrInterface
import org.utbot.jcdb.api.JcClassType
import org.utbot.jcdb.api.JcRefType
import org.utbot.jcdb.api.JcTypedField
import org.utbot.jcdb.api.JcTypedMethod
import org.utbot.jcdb.api.isProtected
import org.utbot.jcdb.api.isPublic
import org.utbot.jcdb.api.toType
import org.utbot.jcdb.impl.suspendableLazy
import org.utbot.jcdb.impl.types.signature.JvmClassRefType
import org.utbot.jcdb.impl.types.signature.JvmParameterizedType
import org.utbot.jcdb.impl.types.signature.JvmType
import org.utbot.jcdb.impl.types.signature.TypeResolutionImpl
import org.utbot.jcdb.impl.types.signature.TypeSignature
import org.utbot.jcdb.impl.types.substition.JcSubstitutor
import org.utbot.jcdb.impl.types.substition.substitute

open class JcClassTypeImpl(
    override val jcClass: JcClassOrInterface,
    private val outerType: JcClassTypeImpl? = null,
    internal val substitutor: JcSubstitutor = JcSubstitutor.empty,
    override val nullable: Boolean
) : JcClassType {

    constructor(
        jcClass: JcClassOrInterface,
        outerType: JcClassTypeImpl? = null,
        parameters: List<JvmType>,
        nullable: Boolean
    ) : this(jcClass, outerType, jcClass.substitute(parameters), nullable)

    private val resolutionImpl by lazy(LazyThreadSafetyMode.NONE) { TypeSignature.of(jcClass) as? TypeResolutionImpl }
    private val declaredTypeParameters by lazy(LazyThreadSafetyMode.NONE) { jcClass.typeParameters }

    override val classpath get() = jcClass.classpath

    override val typeName: String
        get() {
            val generics = if (substitutor.substitutions.isEmpty()) {
                declaredTypeParameters.joinToString() { it.symbol }
            } else {
                declaredTypeParameters.joinToString() {
                    substitutor.substitution(it)?.displayName ?: it.symbol
                }
            }
            return jcClass.name + ("<${generics}>".takeIf { generics.isNotEmpty() } ?: "")
        }

    private val originParametrizationGetter = suspendableLazy {
        declaredTypeParameters.map { it.asJcDeclaration(jcClass) }
    }

    private val parametrizationGetter = suspendableLazy {
        declaredTypeParameters.map { declaration ->
            val jvmType = substitutor.substitution(declaration)
            if (jvmType != null) {
                classpath.typeOf(jvmType) as JcRefType
            } else {
                JcTypeVariableImpl(classpath, declaration.asJcDeclaration(jcClass), true)
            }
        }
    }

    override suspend fun typeParameters() = originParametrizationGetter()

    override suspend fun typeArguments() = parametrizationGetter()

    override suspend fun superType(): JcClassType? {
        val superClass = jcClass.superclass() ?: return null
        return resolutionImpl?.let {
            val newSubstitutor = superSubstitutor(superClass, it.superClass)
            JcClassTypeImpl(superClass, outerType, newSubstitutor, nullable)
        } ?: superClass.toType()
    }

    override suspend fun interfaces(): List<JcClassType> {
        return jcClass.interfaces().map { iface ->
            val ifaceType = resolutionImpl?.interfaceType?.firstOrNull { it.isReferencesClass(iface.name) }
            if (ifaceType != null) {
                val newSubstitutor = superSubstitutor(iface, ifaceType)
                JcClassTypeImpl(iface, null, newSubstitutor, nullable)
            } else {
                iface.toType()
            }
        }
    }

    override suspend fun innerTypes(): List<JcClassType> {
        return jcClass.innerClasses().map {
            JcClassTypeImpl(it, this, substitutor, true)
        }
    }

    override suspend fun methods(): List<JcTypedMethod> {
        //let's calculate visible methods from super types
        return jcClass.typedMethods(true)
    }

    override suspend fun fields(): List<JcTypedField> {
        return jcClass.typedFields(true)
    }

    override fun notNullable() = JcClassTypeImpl(jcClass, outerType, substitutor, false)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as JcClassTypeImpl

        if (nullable != other.nullable) return false
        if (typeName != other.typeName) return false

        return true
    }

    override fun hashCode(): Int {
        val result = nullable.hashCode()
        return 31 * result + typeName.hashCode()
    }

    private suspend fun JcClassOrInterface.typedMethods(all: Boolean): List<JcTypedMethod> {
        val methodSet = if (all) {
            methods
        } else {
            methods.filter { it.isPublic || it.isProtected } // add package check
        }
        return methodSet.map {
            JcTypedMethodImpl(this@JcClassTypeImpl, it, substitutor)
        }
    }

    private suspend fun JcClassOrInterface.typedFields(all: Boolean): List<JcTypedField> {
        val fieldSet = if (all) {
            fields
        } else {
            fields.filter { it.isPublic || it.isProtected } // add package check
        }
        val directSet = fieldSet.map {
            JcTypedFieldImpl(this@JcClassTypeImpl, it, substitutor)
        }
        return directSet
    }


    private fun superSubstitutor(superClass: JcClassOrInterface, superType: JvmType): JcSubstitutor {
        val superParameters = superClass.typeParameters
        val substitutions = (superType as? JvmParameterizedType)?.parameterTypes
        if (substitutions == null || superParameters.size != substitutions.size) {
            return JcSubstitutor.empty
        }
        return substitutor.fork(superParameters.mapIndexed { index, declaration -> declaration to substitutions[index] }
            .toMap())

    }

}

fun JvmType.isReferencesClass(name: String): Boolean {
    return when (val type = this) {
        is JvmClassRefType -> type.name == name
        is JvmParameterizedType -> type.name == name
        is JvmParameterizedType.JvmNestedType -> type.name == name
        else -> false
    }
}