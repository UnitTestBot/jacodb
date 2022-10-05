package org.utbot.jcdb.impl.types

import org.utbot.jcdb.api.JcClasspath
import org.utbot.jcdb.api.JcRefType
import org.utbot.jcdb.api.JcType
import org.utbot.jcdb.api.JcTypeVariableDeclaration
import org.utbot.jcdb.api.PredefinedPrimitives
import org.utbot.jcdb.api.anyType
import org.utbot.jcdb.api.ext.findClass
import org.utbot.jcdb.impl.signature.Formal
import org.utbot.jcdb.impl.signature.FormalTypeVariable
import org.utbot.jcdb.impl.signature.SArrayType
import org.utbot.jcdb.impl.signature.SBoundWildcard
import org.utbot.jcdb.impl.signature.SClassRefType
import org.utbot.jcdb.impl.signature.SParameterizedType
import org.utbot.jcdb.impl.signature.SPrimitiveType
import org.utbot.jcdb.impl.signature.SType
import org.utbot.jcdb.impl.signature.STypeVariable
import org.utbot.jcdb.impl.signature.SUnboundWildcard

class JcTypeBindings(internal val bindings: Map<String, SType> = emptyMap()) {

    fun override(incoming: Set<String>): JcTypeBindings {
        return JcTypeBindings(bindings.filterKeys { !incoming.contains(it) })
    }
}

internal suspend fun JcClasspath.typeOf(stype: SType): JcType {
    return when (stype) {
        is SPrimitiveType -> {
            PredefinedPrimitives.of(stype.ref, this)
                ?: throw IllegalStateException("primitive type ${stype.ref} not found")
        }

        is SClassRefType -> typeOf(findClass(stype.name))
        is SArrayType -> arrayTypeOf(typeOf(stype.elementType))
        is SParameterizedType -> {
            val clazz = findClass(stype.name)
            JcClassTypeImpl(
                clazz,
                parametrization = stype.parameterTypes,
                nullable = true
            )
        }

        is STypeVariable -> JcTypeVariableImpl(stype.symbol, true, anyType())
        is SUnboundWildcard -> JcUnboundWildcardImpl(anyType())
        is SBoundWildcard.SUpperBoundWildcard -> JcUpperBoundWildcardImpl(typeOf(stype.boundType) as JcRefType, true)
        is SBoundWildcard.SLowerBoundWildcard -> JcLowerBoundWildcardImpl(typeOf(stype.boundType) as JcRefType, true)
        else -> throw IllegalStateException("unknown type")
    }
}

class JcTypeVariableDeclarationImpl(
    override val symbol: String,
    override val bounds: List<JcRefType>
) : JcTypeVariableDeclaration

internal suspend fun JcClasspath.typeDeclaration(formal: FormalTypeVariable): JcTypeVariableDeclaration {
    return when (formal) {
        is Formal -> JcTypeVariableDeclarationImpl(
            formal.symbol,
            formal.boundTypeTokens?.map { typeOf(it) as JcRefType }.orEmpty()
        )

        else -> throw IllegalStateException("Unknown type $formal")
    }
}

internal suspend fun JcClasspath.typeDeclarations(formals: List<FormalTypeVariable>): List<JcTypeVariableDeclaration> {
    return formals.map { typeDeclaration(it) }
}