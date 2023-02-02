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

package org.jacodb.testing.types.nullability

import org.utbot.jacodb.api.*

data class TypeNullabilityTree(val isNullable: Boolean?, val innerTypes: List<TypeNullabilityTree>)

class TreeBuilder(private val isNullable: Boolean?) {
    private val innerTypes: MutableList<TypeNullabilityTree> = mutableListOf()

    operator fun TypeNullabilityTree.unaryPlus() {
        this@TreeBuilder.innerTypes.add(this)
    }

    fun build(): TypeNullabilityTree = TypeNullabilityTree(isNullable, innerTypes)
}

fun buildTree(isNullable: Boolean?, actions: TreeBuilder.() -> Unit = {}) =
    TreeBuilder(isNullable).apply(actions).build()

val JcType.nullabilityTree: TypeNullabilityTree
    get() {
        return when (this) {
            is JcClassType -> TypeNullabilityTree(nullable, typeArguments.map { it.nullabilityTree })
            is JcArrayType -> TypeNullabilityTree(nullable, listOf(elementType.nullabilityTree))
            is JcBoundedWildcard -> (upperBounds + lowerBounds).map { it.nullabilityTree }.single()  // For bounded wildcard we are interested only in nullability of bound, not of the wildcard itself
            is JcUnboundWildcard -> TypeNullabilityTree(nullable, listOf())
            is JcTypeVariable -> TypeNullabilityTree(nullable, bounds.map { it.nullabilityTree })
            is JcTypeVariableDeclaration -> TypeNullabilityTree(nullable, bounds.map { it.nullabilityTree })
            else -> TypeNullabilityTree(nullable, listOf())
        }
    }