/*
 *  Copyright 2022 UnitTestBot contributors (utbot.org)
 * <p>
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * <p>
 *  http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.jacodb.impl.types

import kotlinx.collections.immutable.toPersistentMap
import org.jacodb.impl.types.signature.MethodResolutionImpl
import org.jacodb.impl.types.signature.MethodSignature
import org.jacodb.impl.types.signature.TypeResolutionImpl
import org.jacodb.impl.types.signature.TypeSignature
import org.jacodb.api.jvm.JcAccessible
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.JcTypeVariableDeclaration
import org.jacodb.api.jvm.JvmTypeParameterDeclaration

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
    val fromMethod = outerMethod?.allVisibleTypeParameters().orEmpty()
    if (!isStatic) {
        val fromOuter = outerClass?.allVisibleTypeParameters().orEmpty()
        return (direct + fromOuter + fromMethod).toPersistentMap()
    }
    return (direct + fromMethod).toPersistentMap()
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
    return JcTypeVariableDeclarationImpl(symbol, classpath, bounds.orEmpty(), owner = owner)
}