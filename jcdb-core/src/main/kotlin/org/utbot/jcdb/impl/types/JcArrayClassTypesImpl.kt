package org.utbot.jcdb.impl.types

import org.utbot.jcdb.api.JcArrayType
import org.utbot.jcdb.api.JcClassOrInterface
import org.utbot.jcdb.api.JcClassType
import org.utbot.jcdb.api.JcClasspath
import org.utbot.jcdb.api.JcRefType
import org.utbot.jcdb.api.JcType
import org.utbot.jcdb.api.JcTypedField
import org.utbot.jcdb.api.JcTypedMethod

class JcArrayClassTypesImpl(
    override val elementType: JcType,
    override val nullable: Boolean = true,
    private val anyType: JcClassType
) : JcArrayType {

    override val typeName = elementType.typeName + "[]"

    override val methods: List<JcTypedMethod>
        get() = anyType.methods

    override val fields: List<JcTypedField>
        get() = emptyList()

    override val jcClass: JcClassOrInterface
        get() = anyType.jcClass

    override fun notNullable(): JcRefType {
        return JcArrayClassTypesImpl(elementType, false, anyType)
    }

    override val classpath: JcClasspath
        get() = elementType.classpath

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as JcArrayClassTypesImpl

        if (elementType != other.elementType) return false

        return true
    }

    override fun hashCode(): Int {
        return elementType.hashCode()
    }

}