package org.utbot.jcdb.impl.types

import org.utbot.jcdb.api.JcClassOrInterface
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
import org.utbot.jcdb.impl.signature.TypeResolutionImpl
import org.utbot.jcdb.impl.signature.TypeSignature

class JcTypeBindings(
    internal val parametrization: List<SType>? = null,
    bindings: Map<String, SType>,
    private val declarations: Map<String, FormalTypeVariable>,
    private val parent: JcTypeBindings? = null
) {
    companion object {

        val empty = JcTypeBindings(null, emptyMap(), emptyMap(), null)

        fun ofClass(
            jcClass: JcClassOrInterface,
            parent: JcTypeBindings?,
            parametrization: List<SType>? = null
        ): JcTypeBindings {
            val resolution = TypeSignature.of(jcClass.signature)
            if (parametrization != null && resolution is TypeResolutionImpl && resolution.typeVariables.size != parametrization.size) {
                val msg = "Expected ${resolution.typeVariables.joinToString()} but " +
                        "was ${parametrization.joinToString()}"
                throw IllegalStateException(msg)
            }

            val resolutionImpl = resolution as? TypeResolutionImpl

            val bindings = resolutionImpl?.typeVariables?.mapIndexed { index, declaration ->
                declaration.symbol to (parametrization?.get(index) ?: STypeVariable(declaration.symbol))
            }?.toMap() ?: emptyMap()

            val declarations = resolutionImpl?.let {
                it.typeVariables.associateBy { it.symbol }
            } ?: emptyMap()
            return parent?.override(JcTypeBindings(parametrization, bindings, declarations), true)
                ?: JcTypeBindings(parametrization, bindings, declarations)
        }
    }

    internal val typeBindings = bindings.filterValues { it !is STypeVariable }

    fun override(overrides: JcTypeBindings, join: Boolean = false): JcTypeBindings {
        return JcTypeBindings(
            overrides.parametrization,
            overrides.typeBindings,
            overrides.declarations,
            takeIf { join })
    }

    fun override(typeVariables: List<FormalTypeVariable>): JcTypeBindings {
        return JcTypeBindings(
            null,
            emptyMap(),
            typeVariables.associateBy { it.symbol },
            this
        )
    }

    fun findTypeBinding(symbol: String): SType? {
        return typeBindings[symbol] ?: parent?.findTypeBinding(symbol)
    }

    fun resolve(symbol: String): SResolvedTypeVariable {
        val typeVariable = declarations[symbol]
        if (typeVariable == null && parent != null) {
            return parent.resolve(symbol)
        }
        val bounds = typeVariable?.boundTypeTokens?.map {
            it.applyTypeDeclarations(this, null)
        }
        return SResolvedTypeVariable(symbol, bounds.orEmpty())
    }

    suspend fun toJcRefType(stype: SType, classpath: JcClasspath): JcRefType {
        return classpath.typeOf(stype.apply(this, null), this) as JcRefType
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
                null,
                JcTypeBindings.ofClass(clazz, bindings, stype.parameterTypes),
                nullable = true
            )
        }

        is SParameterizedType.SNestedType -> {
            val clazz = findClass(stype.name)
            val outerType = typeOf(stype.ownerType, bindings)
            val outerParameters = (stype.ownerType as? SParameterizedType)?.parameterTypes
            JcClassTypeImpl(
                clazz,
                outerType as JcClassTypeImpl,
                JcTypeBindings.ofClass(clazz, bindings, outerParameters),
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
        is SBoundWildcard.SUpperBoundWildcard -> typeOf(stype.bound, bindings)

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
            formal.boundTypeTokens?.map { typeOf(it.applyKnownBindings(bindings), bindings) as JcRefType }.orEmpty()
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