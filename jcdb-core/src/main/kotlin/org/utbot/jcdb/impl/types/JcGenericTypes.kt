package org.utbot.jcdb.impl.types

import org.utbot.jcdb.api.JcBoundWildcard
import org.utbot.jcdb.api.JcClassOrInterface
import org.utbot.jcdb.api.JcClassType
import org.utbot.jcdb.api.JcClasspath
import org.utbot.jcdb.api.JcLowerBoundWildcard
import org.utbot.jcdb.api.JcRefType
import org.utbot.jcdb.api.JcTypeVariable
import org.utbot.jcdb.api.JcTypedField
import org.utbot.jcdb.api.JcTypedMethod
import org.utbot.jcdb.api.JcUnboundWildcard
import org.utbot.jcdb.api.JcUpperBoundWildcard

class JcUnboundWildcardImpl(private val anyType: JcClassType, override val nullable: Boolean = true) :
    JcUnboundWildcard {

    override val classpath: JcClasspath
        get() = anyType.classpath
    override val typeName: String
        get() = "*"

    override val jcClass: JcClassOrInterface
        get() = anyType.jcClass
    override val methods: List<JcTypedMethod>
        get() = anyType.methods
    override val fields: List<JcTypedField>
        get() = anyType.fields

    override fun notNullable(): JcRefType {
        return JcUnboundWildcardImpl(anyType, false)
    }
}

abstract class AbstractJcBoundWildcard(override val boundType: JcRefType, override val nullable: Boolean) :
    JcBoundWildcard {

    override val jcClass: JcClassOrInterface
        get() = boundType.jcClass
    override val methods: List<JcTypedMethod>
        get() = boundType.methods

    override val fields: List<JcTypedField>
        get() = boundType.fields

    override val classpath: JcClasspath
        get() = boundType.classpath
}

class JcUpperBoundWildcardImpl(boundType: JcRefType, nullable: Boolean) : AbstractJcBoundWildcard(boundType, nullable),
    JcUpperBoundWildcard {

    override val typeName: String
        get() = "? extends ${boundType.typeName}"

    override fun notNullable(): JcRefType {
        return JcUpperBoundWildcardImpl(boundType, false)
    }
}

class JcLowerBoundWildcardImpl(boundType: JcRefType, nullable: Boolean) : AbstractJcBoundWildcard(boundType, nullable),
    JcLowerBoundWildcard {

    override val typeName: String
        get() = "? super ${boundType.typeName}"

    override fun notNullable(): JcRefType {
        return JcLowerBoundWildcardImpl(boundType, false)
    }
}

class JcTypeVariableImpl(
    override val typeSymbol: String,
    override val nullable: Boolean,
    private val anyType: JcClassType
) : JcTypeVariable {

    override val classpath: JcClasspath
        get() = anyType.classpath

    override val typeName: String
        get() = typeSymbol

    override val methods: List<JcTypedMethod>
        get() = anyType.methods

    override val fields: List<JcTypedField>
        get() = anyType.fields

    override val jcClass: JcClassOrInterface
        get() = anyType.jcClass

    override fun notNullable(): JcRefType {
        return JcTypeVariableImpl(typeSymbol, false, anyType)
    }
}