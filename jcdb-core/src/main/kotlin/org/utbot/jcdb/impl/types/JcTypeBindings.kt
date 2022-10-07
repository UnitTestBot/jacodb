package org.utbot.jcdb.impl.types

import org.utbot.jcdb.api.JcClasspath
import org.utbot.jcdb.api.JcRefType
import org.utbot.jcdb.api.JcType
import org.utbot.jcdb.api.JcTypeVariableDeclaration
import org.utbot.jcdb.api.PredefinedPrimitives
import org.utbot.jcdb.api.ext.findClass
import org.utbot.jcdb.impl.signature.Formal
import org.utbot.jcdb.impl.signature.FormalTypeVariable
import org.utbot.jcdb.impl.signature.SArrayType
import org.utbot.jcdb.impl.signature.SBoundWildcard
import org.utbot.jcdb.impl.signature.SClassRefType
import org.utbot.jcdb.impl.signature.SParameterizedType
import org.utbot.jcdb.impl.signature.SPrimitiveType
import org.utbot.jcdb.impl.signature.SResolvedTypeVariable
import org.utbot.jcdb.impl.signature.SType
import org.utbot.jcdb.impl.signature.STypeVariable
import org.utbot.jcdb.impl.signature.SUnboundWildcard

class JcTypeBindings(
    incoming: Map<String, SType>,
    private val declarations: Map<String, FormalTypeVariable>
) {
    companion object {
        val empty = JcTypeBindings(emptyMap(), emptyMap())
    }

    internal val bindings = incoming.filterValues { it !is STypeVariable }

    fun override(overrides: List<FormalTypeVariable>): JcTypeBindings {
        val newDeclarations = declarations + overrides.associateBy { it.symbol }
        val newSymbols = overrides.map { it.symbol }.toSet()
        val newBindings = bindings.filterKeys { !newSymbols.contains(it) }
        return JcTypeBindings(newBindings, newDeclarations)
    }

    fun findDirectBinding(symbol: String): SType? {
        return bindings[symbol]
    }

    fun resolve(symbol: String): SResolvedTypeVariable {
        val bounds = declarations[symbol]?.boundTypeTokens?.map { it.applyTypeDeclarations(this, null) }
        return SResolvedTypeVariable(symbol, bounds.orEmpty())
    }

    suspend fun toJcRefType(stype: SType, classpath: JcClasspath): JcRefType {
        return classpath.typeOf(stype.apply(this, null), this) as JcRefType
//        val bindings = this
//
//        suspend fun SType.toJcRefType(): JcRefType {
//            return classpath.typeOf(this, bindings) as JcRefType
//        }

//        if (stype is STypeVariable) {
//            val symbol = stype.symbol
//            val direct = findDirectBinding(symbol)
//            if (direct != null) {
//                return direct.toJcRefType()
//            }
//            val resolved = resolve(symbol)
//            return JcTypeVariableImpl(
//                classpath,
//                JcTypeVariableDeclarationImpl(
//                    symbol,
//                    resolved.boundTypeTokens?.map { it.toJcRefType() }.orEmpty()
//                ), true
//            )
//        }
//        return stype.apply(bindings, null).toJcRefType()

    }
}

internal suspend fun JcClasspath.typeOf(stype: SType, bindings: JcTypeBindings): JcType {
    return when (stype) {
        is SPrimitiveType -> {
            PredefinedPrimitives.of(stype.ref, this)
                ?: throw IllegalStateException("primitive type ${stype.ref} not found")
        }

        is SClassRefType -> typeOf(findClass(stype.name))
        is SArrayType -> arrayTypeOf(typeOf(stype.elementType, bindings))
        is SParameterizedType -> {
            val clazz = findClass(stype.name)
            JcClassTypeImpl(
                clazz,
                parametrization = stype.parameterTypes,
                nullable = true
            )
        }

        is SResolvedTypeVariable -> {
            val resolved = stype.boundaries.map { typeOf(it, bindings) as JcRefType }
            JcTypeVariableImpl(this, JcTypeVariableDeclarationImpl(stype.symbol, resolved), true)
        }
        is STypeVariable -> {
            JcTypeVariableImpl(this, JcTypeVariableDeclarationImpl(stype.symbol, emptyList()), true)
        }

        is SUnboundWildcard -> JcUnboundWildcardImpl(this)
        is SBoundWildcard.SUpperBoundWildcard -> typeOf(stype.bound,bindings)

        is SBoundWildcard.SLowerBoundWildcard -> JcLowerBoundWildcardImpl(
            typeOf(
                stype.bound,
                bindings
            ) as JcRefType, true
        )

        else -> throw IllegalStateException("unknown type")
    }
}

class JcTypeVariableDeclarationImpl(
    override val symbol: String,
    override val bounds: List<JcRefType>
) : JcTypeVariableDeclaration

internal suspend fun JcClasspath.typeDeclaration(
    formal: FormalTypeVariable,
    bindings: JcTypeBindings
): JcTypeVariableDeclaration {
    return when (formal) {
        is Formal -> JcTypeVariableDeclarationImpl(
            formal.symbol,
            formal.boundTypeTokens?.map { typeOf(it, bindings) as JcRefType }.orEmpty()
        )

        else -> throw IllegalStateException("Unknown type $formal")
    }
}

internal suspend fun JcClasspath.typeDeclarations(
    formals: List<FormalTypeVariable>,
    bindings: JcTypeBindings
): List<JcTypeVariableDeclaration> {
    return formals.map { typeDeclaration(it, bindings) }
}