package org.utbot.jcdb.impl.types

import org.utbot.jcdb.api.JcClassOrInterface
import org.utbot.jcdb.api.JcClassType
import org.utbot.jcdb.api.JcRefType
import org.utbot.jcdb.api.JcTypedField
import org.utbot.jcdb.api.JcTypedMethod
import org.utbot.jcdb.api.TypeResolution
import org.utbot.jcdb.api.anyType
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

    val classBindings: JcTypeBindings

    init {
        if (parametrization != null && resolution is TypeResolutionImpl && resolution.typeVariable.size != parametrization.size) {
            val msg = "Expected ${resolution.typeVariable.joinToString()} but was ${parametrization.joinToString()}"
            throw IllegalStateException(msg)
        }
        val bindings = ifSyncSignature {
            it.typeVariable.mapIndexed { index, declaration ->
                declaration.symbol to (parametrization?.get(index) ?: STypeVariable(declaration.symbol))
            }.toMap()
        } ?: emptyMap()
        classBindings = JcTypeBindings(bindings)
    }

    override val classpath get() = jcClass.classpath

    override val typeName: String
        get() = jcClass.name

    private val originParametrizationGetter = suspendableLazy {
        ifSignature {
            classpath.typeDeclarations(it.typeVariable)
        } ?: emptyList()
    }

    private val parametrizationGetter = suspendableLazy {
        originalParametrization().mapIndexed { index, declaration ->
            declaration.symbol to (parametrization?.get(index)?.let { classpath.typeOf(it) as JcRefType} ?: JcTypeVariableImpl(declaration.symbol, true, classpath.anyType()))
        }.toMap()
    }

    override suspend fun originalParametrization() = originParametrizationGetter()

    override suspend fun parametrization() = parametrizationGetter()

    override suspend fun superType(): JcRefType? {
        return ifSignature {
            classpath.typeOf(it.superClass) as? JcRefType
        } ?: jcClass.superclass()?.let { classpath.typeOf(it) }
    }

    override suspend fun interfaces(): List<JcRefType> {
        return ifSignature {
            jcClass.interfaces().map { classpath.typeOf(it) }
        } ?: emptyList()
    }

    override suspend fun outerType(): JcRefType? = TODO("Not yet implemented")

    override suspend fun outerMethod(): JcTypedMethod? = TODO("Not yet implemented")

    override suspend fun innerTypes(): List<JcRefType> = TODO("Not yet implemented")

    override suspend fun methods(): List<JcTypedMethod> {
        return jcClass.methods.map {
            JcTypedMethodImpl(enclosingType = this, it, classBindings)
        }
    }

    override suspend fun fields(): List<JcTypedField> {
        return jcClass.fields.map {
            JcTypedFieldImpl(enclosingType = this, it, classBindings = classBindings)
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


}