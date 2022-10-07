package org.utbot.jcdb.impl.types

import org.utbot.jcdb.api.JcClassOrInterface
import org.utbot.jcdb.api.JcClassType
import org.utbot.jcdb.api.JcRefType
import org.utbot.jcdb.api.JcTypedField
import org.utbot.jcdb.api.JcTypedMethod
import org.utbot.jcdb.api.TypeResolution
import org.utbot.jcdb.api.toType
import org.utbot.jcdb.impl.signature.SType
import org.utbot.jcdb.impl.signature.STypeVariable
import org.utbot.jcdb.impl.signature.TypeResolutionImpl
import org.utbot.jcdb.impl.signature.TypeSignature
import org.utbot.jcdb.impl.suspendableLazy

open class JcClassTypeImpl(
    override val jcClass: JcClassOrInterface,
    private val resolution: TypeResolution = TypeSignature.of(jcClass.signature),
    private val parametrization: List<SType>? = null,
    override val nullable: Boolean
) : JcClassType {

    private val typeBindings: JcTypeBindings

    init {
        if (parametrization != null && resolution is TypeResolutionImpl && resolution.typeVariables.size != parametrization.size) {
            val msg = "Expected ${resolution.typeVariables.joinToString()} but was ${parametrization.joinToString()}"
            throw IllegalStateException(msg)
        }

        val bindings = ifSyncSignature {
            it.typeVariables.mapIndexed { index, declaration ->
                declaration.symbol to (parametrization?.get(index) ?: STypeVariable(declaration.symbol))
            }.toMap()
        } ?: emptyMap()

        val declarations = ifSyncSignature {
            it.typeVariables.associateBy { it.symbol }
        } ?: emptyMap()

        typeBindings = JcTypeBindings(bindings, declarations)
    }

    override val classpath get() = jcClass.classpath

    override val typeName: String
        get() {
            if (parametrization == null) {
                val generics = ifSyncSignature { it.typeVariables.joinToString() } ?: return jcClass.name
                return jcClass.name + ("<$generics>".takeIf { generics.isNotEmpty() } ?: "")
            }
            return jcClass.name + ("<${parametrization.joinToString { it.displayName }}>".takeIf { parametrization.isNotEmpty() } ?: "")
        }

    private val originParametrizationGetter = suspendableLazy {
        ifSignature {
            classpath.typeDeclarations(it.typeVariables, JcTypeBindings.empty)
        } ?: emptyList()
    }

    private val parametrizationGetter = suspendableLazy {
        originalParametrization().associate { original ->
            val direct = typeBindings.findDirectBinding(original.symbol)
            if (direct != null) {
                original.symbol to direct.apply(typeBindings, original.symbol).toJcRefType()
            } else {
                original.symbol to typeBindings.resolve(original.symbol).apply(typeBindings, null).toJcRefType()
            }
        }
    }

    override suspend fun originalParametrization() = originParametrizationGetter()

    override suspend fun parametrization() = parametrizationGetter()

    override suspend fun superType(): JcRefType? {
        return ifSignature {
            classpath.typeOf(it.superClass, typeBindings) as? JcRefType
        } ?: jcClass.superclass()?.toType()
    }

    override suspend fun interfaces(): List<JcRefType> {
        return ifSignature {
            jcClass.interfaces().map { it.toType() }
        } ?: emptyList()
    }

    override suspend fun outerType(): JcRefType? {
        return jcClass.outerClass()?.toType()
    }

    override suspend fun outerMethod(): JcTypedMethod? {
        return jcClass.outerMethod()?.let {
            JcTypedMethodImpl(enclosingType = it.enclosingClass.toType(), it, JcTypeBindings.empty)
        }
    }

    override suspend fun innerTypes(): List<JcRefType> = TODO("Not yet implemented")

    override suspend fun methods(): List<JcTypedMethod> {
        return jcClass.methods.map {
            JcTypedMethodImpl(enclosingType = this, it, typeBindings)
        }
    }

    override suspend fun fields(): List<JcTypedField> {
        return jcClass.fields.map {
            JcTypedFieldImpl(enclosingType = this, it, typeBindings = typeBindings)
        }
    }

    override fun notNullable() = JcClassTypeImpl(jcClass, resolution, parametrization, false)

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

    private suspend fun <T> ifSignature(map: suspend (TypeResolutionImpl) -> T?): T? {
        return when (resolution) {
            is TypeResolutionImpl -> map(resolution)
            else -> null
        }
    }

    private fun <T> ifSyncSignature(map: (TypeResolutionImpl) -> T?): T? {
        return when (resolution) {
            is TypeResolutionImpl -> map(resolution)
            else -> null
        }
    }

    private suspend fun SType.toJcRefType(): JcRefType {
        return classpath.typeOf(this, typeBindings) as JcRefType
    }

}