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

import org.utbot.jcdb.api.PredefinedPrimitives

sealed class JvmType(val isNullable: Boolean) {

    abstract val displayName: String
}

internal sealed class JvmRefType(isNullable: Boolean) : JvmType(isNullable)

internal class JvmArrayType(val elementType: JvmType, isNullable: Boolean = true) : JvmRefType(isNullable) {

    override val displayName: String
        get() = elementType.displayName + "[]"

}

internal class JvmParameterizedType(
    val name: String,
    val parameterTypes: List<JvmType>,
    isNullable: Boolean = true
) : JvmRefType(isNullable) {

    override val displayName: String
        get() = name + "<${parameterTypes.joinToString { it.displayName }}>"

    class JvmNestedType(
        val name: String,
        val parameterTypes: List<JvmType>,
        val ownerType: JvmType,
        isNullable: Boolean = true
    ) : JvmRefType(isNullable) {

        override val displayName: String
            get() = name + "<${parameterTypes.joinToString { it.displayName }}>"

    }

}

internal class JvmClassRefType(val name: String, isNullable: Boolean = true) : JvmRefType(isNullable) {

    override val displayName: String
        get() = name

}

/**
 * For type variable we think that it is [isNullable] iff for every concrete type obtained by this from substitution,
 * the resulting type may contain null.
 *
 * For example, type `T?` from Kotlin is [isNullable] because even if T is replaced with not-nullable type,
 * the resulting type is nullable. Both in Java and Kotlin, type `T` is not [isNullable] because when replaced with notnull
 * type, the result may not contain null.
 *
 * This is important to properly handle nullability w.r.t substitutions:
 * substituted type is nullable iff type variable is nullable or substituting type is nullable
 */
class JvmTypeVariable(val symbol: String, isNullable: Boolean = false) : JvmType(isNullable) {

    constructor(declaration: JvmTypeParameterDeclaration, isNullable: Boolean = false) : this(declaration.symbol, isNullable) {
        this.declaration = declaration
    }

    var declaration: JvmTypeParameterDeclaration? = null

    override val displayName: String
        get() = symbol

}

// Nullability has no sense in wildcards, so we suppose them to be always nullable for definiteness
internal sealed class JvmWildcard: JvmType(true)

internal sealed class JvmBoundWildcard(val bound: JvmType) : JvmWildcard() {

    internal class JvmUpperBoundWildcard(boundType: JvmType) : JvmBoundWildcard(boundType) {
        override val displayName: String
            get() = "? extends ${bound.displayName}"

    }

    internal class JvmLowerBoundWildcard(boundType: JvmType) : JvmBoundWildcard(boundType) {
        override val displayName: String
            get() = "? super ${bound.displayName}"

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

}