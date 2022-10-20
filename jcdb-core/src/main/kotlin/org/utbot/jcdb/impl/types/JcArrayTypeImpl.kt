package org.utbot.jcdb.impl.types

import org.utbot.jcdb.api.JcArrayType
import org.utbot.jcdb.api.JcClasspath
import org.utbot.jcdb.api.JcRefType
import org.utbot.jcdb.api.JcType

class JcArrayTypeImpl(
    override val elementType: JcType,
    override val nullable: Boolean = true
) : JcArrayType {

    override val typeName = elementType.typeName + "[]"

    override fun notNullable(): JcRefType {
        return JcArrayTypeImpl(elementType, false)
    }

    override val classpath: JcClasspath
        get() = elementType.classpath

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as JcArrayTypeImpl

        if (elementType != other.elementType) return false

        return true
    }

    override fun hashCode(): Int {
        return elementType.hashCode()
    }

}