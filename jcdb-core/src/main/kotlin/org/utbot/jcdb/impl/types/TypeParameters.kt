package org.utbot.jcdb.impl.types

import com.google.common.cache.CacheBuilder
import kotlinx.collections.immutable.PersistentMap
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
import java.time.Duration

val JcClassOrInterface.typeParameters: List<JvmTypeParameterDeclaration>
    get() {
        return (TypeSignature.of(this) as? TypeResolutionImpl)?.typeVariables ?: emptyList()
    }

val JcMethod.typeParameters: List<JvmTypeParameterDeclaration>
    get() {
        return (MethodSignature.of(this) as? MethodResolutionImpl)?.typeVariables ?: emptyList()
    }

private val classParamsCache = CacheBuilder.newBuilder()
    .maximumSize(1_000)
    .expireAfterAccess(Duration.ofSeconds(10))
    .build<JcClassOrInterface, PersistentMap<String, JvmTypeParameterDeclaration>>()

fun JcClassOrInterface.directTypeParameters(): List<JvmTypeParameterDeclaration> {
    val declaredSymbols = typeParameters.map { it.symbol }.toHashSet()
    return allVisibleTypeParameters().filterKeys { declaredSymbols.contains(it) }.values.toList()
}

/**
 * returns all visible declaration without JvmTypeParameterDeclaration#declaration
 */
fun JcClassOrInterface.allVisibleTypeParameters(): Map<String, JvmTypeParameterDeclaration> {
    val result = classParamsCache.getIfPresent(this)
    if (result != null) {
        return result
    }
    val direct = typeParameters.associateBy { it.symbol }
    if (!isStatic) {
        val fromOuter = outerClass?.allVisibleTypeParameters()
        val fromMethod = outerMethod?.allVisibleTypeParameters()
        val res = (direct + (fromMethod ?: fromOuter).orEmpty()).toPersistentMap()
        classParamsCache.put(this, res)
        return res
    }
    return direct.also {
        classParamsCache.put(this, it.toPersistentMap())
    }
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