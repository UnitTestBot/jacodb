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

package org.utbot.jcdb.impl.types.signature

import kotlinx.metadata.KmType
import org.utbot.jcdb.api.PredefinedPrimitives
import org.utbot.jcdb.api.ext.isNullable

sealed class JvmType(val isNullable: Boolean) {

    abstract val displayName: String

    abstract fun setNullability(nullable: Boolean): JvmType

    open fun relaxWithKmType(kmType: KmType): JvmType =
        setNullability(kmType.isNullable)
}

internal sealed class JvmRefType(isNullable: Boolean) : JvmType(isNullable)

internal class JvmArrayType(val elementType: JvmType, isNullable: Boolean = true) : JvmRefType(isNullable) {

    override val displayName: String
        get() = elementType.displayName + "[]"

    override fun setNullability(nullable: Boolean): JvmType =
        JvmArrayType(elementType, nullable)

    override fun relaxWithKmType(kmType: KmType): JvmType {
        // NB: kmType may have zero (for primitive arrays) one (for object arrays) argument
        val updatedElementType = kmType.arguments.singleOrNull()?.type?.let {
            elementType.relaxWithKmType(it)
        } ?: elementType

        return JvmArrayType(updatedElementType, kmType.isNullable)
    }

}

internal class JvmParameterizedType(
    val name: String,
    val parameterTypes: List<JvmType>,
    isNullable: Boolean = true
) : JvmRefType(isNullable) {

    override val displayName: String
        get() = name + "<${parameterTypes.joinToString { it.displayName }}>"

    override fun setNullability(nullable: Boolean): JvmType =
        JvmParameterizedType(name, parameterTypes, nullable)

    override fun relaxWithKmType(kmType: KmType): JvmType {
        val types = parameterTypes.zip(kmType.arguments.map { it.type }) { parameterType, kmParameterType ->
            kmParameterType?.let {
                parameterType.relaxWithKmType(it)
            } ?: parameterType
        }
        return JvmParameterizedType(name, types, kmType.isNullable)
    }

    class JvmNestedType(
        val name: String,
        val parameterTypes: List<JvmType>,
        val ownerType: JvmType,
        isNullable: Boolean = true
    ) : JvmRefType(isNullable) {

        override val displayName: String
            get() = name + "<${parameterTypes.joinToString { it.displayName }}>"

        override fun setNullability(nullable: Boolean): JvmType =
            JvmNestedType(name, parameterTypes, ownerType, nullable)

        override fun relaxWithKmType(kmType: KmType): JvmType {
            val types = parameterTypes.zip(kmType.arguments.map { it.type }) { parameterType, kmParameterType ->
                kmParameterType?.let {
                    parameterType.relaxWithKmType(it)
                } ?: parameterType
            }
            return JvmNestedType(name, types, ownerType, kmType.isNullable)
        }

    }

}

internal class JvmClassRefType(val name: String, isNullable: Boolean = true) : JvmRefType(isNullable) {

    override val displayName: String
        get() = name

    override fun setNullability(nullable: Boolean): JvmType =
        JvmClassRefType(name, nullable)

}

// Unless explicitly declared as T?, we think that type variable is not-nullable
// If it is then substituted with nullable type, the resulting type will still be nullable
open class JvmTypeVariable(val symbol: String, isNullable: Boolean = false) : JvmType(isNullable) {

    constructor(declaration: JvmTypeParameterDeclaration, isNullable: Boolean = false) : this(declaration.symbol, isNullable) {
        this.declaration = declaration
    }

    var declaration: JvmTypeParameterDeclaration? = null

    override val displayName: String
        get() = symbol

    override fun setNullability(nullable: Boolean): JvmType =
        JvmTypeVariable(symbol, nullable).also {
            it.declaration = declaration
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as JvmTypeVariable

        if (symbol != other.symbol) return false
        if (declaration != other.declaration) return false

        return true
    }

    override fun hashCode(): Int {
        var result = symbol.hashCode()
        result = 31 * result + (declaration?.hashCode() ?: 0)
        return result
    }
}

// Nullability has no sense in wildcards, so we suppose them to be always nullable for definiteness
internal sealed class JvmWildcard: JvmType(true) {
    override fun setNullability(nullable: Boolean): JvmType {
        if (!nullable)
            error("Attempting to make wildcard not-nullable, which are always nullable by convention")
        return this
    }
}

internal sealed class JvmBoundWildcard(val bound: JvmType) : JvmWildcard() {

    internal class JvmUpperBoundWildcard(boundType: JvmType) : JvmBoundWildcard(boundType) {
        override val displayName: String
            get() = "? extends ${bound.displayName}"

        override fun relaxWithKmType(kmType: KmType): JvmType {
            // Kotlin metadata is constructed in terms of projections => there is no explicit type for wildcard.
            // Therefore, we don't look for kmType.arguments and relax bound with kmType directly, not with kmType.arguments.single()
            // Same applies to JvmLowerBoundWildcard.relaxWithKmType
            return JvmUpperBoundWildcard(bound.relaxWithKmType(kmType))
        }
    }

    internal class JvmLowerBoundWildcard(boundType: JvmType) : JvmBoundWildcard(boundType) {
        override val displayName: String
            get() = "? super ${bound.displayName}"

        override fun relaxWithKmType(kmType: KmType): JvmType {
            return JvmLowerBoundWildcard(bound.relaxWithKmType(kmType))
        }

    }
}

internal object JvmUnboundWildcard : JvmWildcard() {

    override val displayName: String
        get() = "*"
}

internal class JvmPrimitiveType(val ref: String) : JvmRefType(isNullable = false) {

    companion object {
        fun of(descriptor: Char): JvmType {
            return when (descriptor) {
                'V' -> JvmPrimitiveType(PredefinedPrimitives.void)
                'Z' -> JvmPrimitiveType(PredefinedPrimitives.boolean)
                'B' -> JvmPrimitiveType(PredefinedPrimitives.byte)
                'S' -> JvmPrimitiveType(PredefinedPrimitives.short)
                'C' -> JvmPrimitiveType(PredefinedPrimitives.char)
                'I' -> JvmPrimitiveType(PredefinedPrimitives.int)
                'J' -> JvmPrimitiveType(PredefinedPrimitives.long)
                'F' -> JvmPrimitiveType(PredefinedPrimitives.float)
                'D' -> JvmPrimitiveType(PredefinedPrimitives.double)
                else -> throw IllegalArgumentException("Not a valid primitive type descriptor: $descriptor")
            }
        }
    }

    override val displayName: String
        get() = ref

    override fun setNullability(nullable: Boolean): JvmType {
        if (nullable)
            error("Attempting to make a nullable primitive")
        return this
    }

}