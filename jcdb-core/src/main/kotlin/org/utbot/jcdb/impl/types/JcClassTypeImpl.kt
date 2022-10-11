package org.utbot.jcdb.impl.types

import org.utbot.jcdb.api.JcClassOrInterface
import org.utbot.jcdb.api.JcClassType
import org.utbot.jcdb.api.JcRefType
import org.utbot.jcdb.api.JcTypedField
import org.utbot.jcdb.api.JcTypedMethod
import org.utbot.jcdb.api.toType
import org.utbot.jcdb.impl.signature.SType
import org.utbot.jcdb.impl.signature.TypeResolutionImpl
import org.utbot.jcdb.impl.signature.TypeSignature
import org.utbot.jcdb.impl.suspendableLazy

open class JcClassTypeImpl(
    override val jcClass: JcClassOrInterface,
    private val outerType: JcClassType? = null,
    private val typeBindings: JcTypeBindings,
    override val nullable: Boolean
) : JcClassType {

    private val resolutionImpl = TypeSignature.of(jcClass.signature) as? TypeResolutionImpl

    override val classpath get() = jcClass.classpath

    override val typeName: String
        get() {
            if (typeBindings.parametrization == null) {
                val generics = resolutionImpl?.typeVariables?.joinToString() ?: return jcClass.name
                return jcClass.name + ("<$generics>".takeIf { generics.isNotEmpty() } ?: "")
            }
            return jcClass.name + ("<${typeBindings.parametrization.joinToString { it.displayName }}>".takeIf { typeBindings.parametrization.isNotEmpty() }
                ?: "")
        }

    private val originParametrizationGetter = suspendableLazy {
        resolutionImpl?.let {
            classpath.typeDeclarations(it.typeVariables, JcTypeBindings.empty)
        } ?: emptyList()
    }

    private val parametrizationGetter = suspendableLazy {
        originalParametrization().associate { original ->
            val direct = typeBindings.findTypeBinding(original.symbol)
            if (direct != null) {
                original.symbol to direct.apply(typeBindings, original.symbol).toJcRefType()
            } else {
                original.symbol to typeBindings.resolve(original.symbol).apply(typeBindings, null).toJcRefType()
            }
        }
    }

    override suspend fun originalParametrization() = originParametrizationGetter()

    override suspend fun parametrization() = parametrizationGetter()

    override suspend fun superType(): JcClassType? {
        return resolutionImpl?.let {
            classpath.typeOf(it.superClass, typeBindings) as? JcClassType
        } ?: jcClass.superclass()?.toType()
    }

    override suspend fun interfaces(): List<JcRefType> {
        return resolutionImpl?.let {
            it.interfaceType.map { classpath.typeOf(it, typeBindings) as JcRefType }
        } ?: jcClass.interfaces().map { it.toType() } ?: emptyList()
    }

    override suspend fun outerType(): JcClassType? {
        return jcClass.outerClass()?.toType()
    }

    override suspend fun outerMethod(): JcTypedMethod? {
        return jcClass.outerMethod()?.let {
            JcTypedMethodImpl(enclosingType = it.enclosingClass.toType(), it, JcTypeBindings.empty)
        }
    }

    override suspend fun innerTypes(): List<JcClassType> {
        return jcClass.innerClasses().map {
            val resolution = TypeSignature.of(it.signature)
            JcClassTypeImpl(it, this, JcTypeBindings.ofClass(it, typeBindings), true)
        }
    }

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

    override fun notNullable() = JcClassTypeImpl(jcClass, outerType, typeBindings, false)

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

    private suspend fun SType.toJcRefType(): JcRefType {
        return classpath.typeOf(this, typeBindings) as JcRefType
    }

}