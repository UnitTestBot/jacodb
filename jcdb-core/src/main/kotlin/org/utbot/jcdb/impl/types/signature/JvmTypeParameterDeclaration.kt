package org.utbot.jcdb.impl.types.signature

import org.utbot.jcdb.api.JcAccessible

interface JvmTypeParameterDeclaration {
    val symbol: String
    val owner: JcAccessible
    val bounds: List<JvmType>?
}

internal class JvmTypeParameterDeclarationImpl(
    override val symbol: String,
    override val owner: JcAccessible,
    override val bounds: List<JvmType>? = null
) : JvmTypeParameterDeclaration {


    override fun toString(): String {
        return "$symbol : ${bounds?.joinToString { it.displayName }}"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as JvmTypeParameterDeclarationImpl

        if (symbol != other.symbol) return false
        if (owner != other.owner) return false

        return true
    }

    override fun hashCode(): Int {
        var result = symbol.hashCode()
        result = 31 * result + owner.hashCode()
        return result
    }

}