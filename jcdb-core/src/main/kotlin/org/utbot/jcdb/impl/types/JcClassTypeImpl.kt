package org.utbot.jcdb.impl.types

import org.utbot.jcdb.api.JcClassOrInterface
import org.utbot.jcdb.api.JcClassType
import org.utbot.jcdb.api.JcClasspath
import org.utbot.jcdb.api.JcRefType
import org.utbot.jcdb.api.JcTypedField
import org.utbot.jcdb.api.JcTypedMethod
import org.utbot.jcdb.api.TypeResolution
import org.utbot.jcdb.impl.signature.TypeResolutionImpl
import org.utbot.jcdb.impl.signature.TypeSignature

class JcClassTypeImpl(
    override val jcClass: JcClassOrInterface,
    private val resolution: TypeResolution = TypeSignature.of(jcClass.signature),
    override val nullable: Boolean
) : JcClassType {

    override val classpath: JcClasspath
        get() = jcClass.classpath

    override val typeName: String
        get() = jcClass.name

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

    override val methods: List<JcTypedMethod>
        get() = jcClass.methods.map {
            JcTypedMethodImpl(ownerType = this, it)
        }

    override val fields: List<JcTypedField>
        get() = TODO("Not yet implemented")

    override fun notNullable() = JcClassTypeImpl(jcClass, resolution, false)

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


}