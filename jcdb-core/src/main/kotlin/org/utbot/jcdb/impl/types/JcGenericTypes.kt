package org.utbot.jcdb.impl.types

import org.utbot.jcdb.api.JcBoundWildcard
import org.utbot.jcdb.api.JcClasspath
import org.utbot.jcdb.api.JcLowerBoundWildcard
import org.utbot.jcdb.api.JcRefType
import org.utbot.jcdb.api.JcTypeVariable
import org.utbot.jcdb.api.JcTypeVariableDeclaration
import org.utbot.jcdb.api.JcUnboundWildcard
import org.utbot.jcdb.api.JcUpperBoundWildcard

class JcUnboundWildcardImpl(override val classpath: JcClasspath, override val nullable: Boolean = true) :
    JcUnboundWildcard {

    override val typeName: String
        get() = "*"

    override fun notNullable(): JcRefType {
        return JcUnboundWildcardImpl(classpath, false)
    }
}

abstract class AbstractJcBoundWildcard(override val boundType: JcRefType, override val nullable: Boolean) :
    JcBoundWildcard {

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
    override val classpath: JcClasspath,
    private val declaration: JcTypeVariableDeclaration,
    override val nullable: Boolean
) : JcTypeVariable {

    override val typeName: String
        get() = symbol

    override val symbol: String get() = declaration.symbol

    override val bounds: List<JcRefType>
        get() = declaration.bounds

    override fun notNullable(): JcRefType {
        return JcTypeVariableImpl(classpath, declaration, nullable)
    }
}