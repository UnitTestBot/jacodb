package org.utbot.jcdb.impl.types

import kotlinx.collections.immutable.toPersistentMap
import org.utbot.jcdb.api.JcAccessible
import org.utbot.jcdb.api.JcClassOrInterface
import org.utbot.jcdb.api.JcMethod
import org.utbot.jcdb.api.JcRefType
import org.utbot.jcdb.api.JcTypeVariableDeclaration
import org.utbot.jcdb.api.isStatic
import org.utbot.jcdb.impl.types.signature.JvmTypeParameterDeclaration
import org.utbot.jcdb.impl.types.signature.MethodResolutionImpl
import org.utbot.jcdb.impl.types.signature.MethodSignature
import org.utbot.jcdb.impl.types.signature.TypeResolutionImpl
import org.utbot.jcdb.impl.types.signature.TypeSignature

val JcClassOrInterface.typeParameters: List<JvmTypeParameterDeclaration>
    get() {
        return (TypeSignature.of(this) as? TypeResolutionImpl)?.typeVariables ?: emptyList()
    }

val JcMethod.typeParameters: List<JvmTypeParameterDeclaration>
    get() {
        return (MethodSignature.of(this) as? MethodResolutionImpl)?.typeVariables ?: emptyList()
    }

fun JcClassOrInterface.directTypeParameters(): List<JvmTypeParameterDeclaration> {
    val declaredSymbols = typeParameters.map { it.symbol }.toHashSet()
    return allVisibleTypeParameters().filterKeys { declaredSymbols.contains(it) }.values.toList()
}

/**
 * returns all visible declaration without JvmTypeParameterDeclaration#declaration
 */
fun JcClassOrInterface.allVisibleTypeParameters(): Map<String, JvmTypeParameterDeclaration> {
    val direct = typeParameters.associateBy { it.symbol }
    if (!isStatic) {
        val fromOuter = outerClass?.allVisibleTypeParameters()
        val fromMethod = outerMethod?.allVisibleTypeParameters()
        return (direct + (fromMethod ?: fromOuter).orEmpty()).toPersistentMap()
    }
    return direct
}

fun JcMethod.allVisibleTypeParameters(): Map<String, JvmTypeParameterDeclaration> {
    return typeParameters.associateBy { it.symbol } + enclosingClass.allVisibleTypeParameters().takeIf { !isStatic }
        .orEmpty()
}

fun JvmTypeParameterDeclaration.asJcDeclaration(owner: JcAccessible): JcTypeVariableDeclaration {
    val classpath = when (owner) {
        is JcClassOrInterface -> owner.classpath
        is JcMethod -> owner.enclosingClass.classpath
        else -> throw IllegalStateException("Unknown owner type $owner")
    }
    val bounds = bounds?.map { classpath.typeOf(it) as JcRefType }
    return JcTypeVariableDeclarationImpl(symbol, bounds.orEmpty(), owner = owner)
}