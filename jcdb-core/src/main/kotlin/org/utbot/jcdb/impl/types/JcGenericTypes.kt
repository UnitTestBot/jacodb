package org.utbot.jcdb.impl.types

import org.utbot.jcdb.api.JcBoundedWildcard
import org.utbot.jcdb.api.JcClasspath
import org.utbot.jcdb.api.JcRefType
import org.utbot.jcdb.api.JcTypeVariable
import org.utbot.jcdb.api.JcTypeVariableDeclaration
import org.utbot.jcdb.api.JcUnboundWildcard

class JcUnboundWildcardImpl(override val classpath: JcClasspath, override val nullable: Boolean = true) :
    JcUnboundWildcard {

    override val typeName: String
        get() = "*"

    override fun notNullable(): JcRefType {
        return JcUnboundWildcardImpl(classpath, false)
    }
}

class JcBoundedWildcardImpl(
    override val upperBounds: List<JcRefType>,
    override val lowerBounds: List<JcRefType>,
    override val nullable: Boolean
) : JcBoundedWildcard {

    override val classpath: JcClasspath
        get() = upperBounds.firstOrNull()?.classpath ?: lowerBounds.firstOrNull()?.classpath
        ?: throw IllegalStateException("Upper or lower bound should be specified")

    override val typeName: String
        get() {
            val (name, bounds) = when{
                upperBounds.isNotEmpty() -> "extends" to upperBounds
                else -> "super" to lowerBounds
            }
            return "? $name ${bounds.joinToString(" & ") { it.typeName }}"
        }


    override fun notNullable(): JcRefType {
        return JcBoundedWildcardImpl(upperBounds, lowerBounds, false)
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