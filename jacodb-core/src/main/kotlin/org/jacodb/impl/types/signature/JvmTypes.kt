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

package org.jacodb.impl.types.signature

import org.jacodb.api.JcAnnotation
import org.jacodb.api.PredefinedPrimitives

/**
 * @property isNullable denotes the nullability of the type in terms of Kotlin type system.
 * It has three possible values:
 * - true -- means that type is nullable, a.k.a. T?
 * - false -- means that type is non-nullable, a.k.a. T
 * - null -- means that type has unknown nullability, a.k.a. T!
 */
sealed class JvmType(val isNullable: Boolean?, val annotations: List<JcAnnotation>) {

    abstract val displayName: String
}

internal sealed class JvmRefType(isNullable: Boolean?, annotations: List<JcAnnotation>)
    : JvmType(isNullable, annotations)

internal class JvmArrayType(val elementType: JvmType, isNullable: Boolean? = null, annotations: List<JcAnnotation>)
    : JvmRefType(isNullable, annotations) {

    override val displayName: String
        get() = elementType.displayName + "[]"

}

internal class JvmParameterizedType(
    val name: String,
    val parameterTypes: List<JvmType>,
    isNullable: Boolean? = null,
    annotations: List<JcAnnotation>
) : JvmRefType(isNullable, annotations) {

    override val displayName: String
        get() = name + "<${parameterTypes.joinToString { it.displayName }}>"

    class JvmNestedType(
        val name: String,
        val parameterTypes: List<JvmType>,
        val ownerType: JvmType,
        isNullable: Boolean? = null,
        annotations: List<JcAnnotation>
    ) : JvmRefType(isNullable, annotations) {

        override val displayName: String
            get() = name + "<${parameterTypes.joinToString { it.displayName }}>"

    }

}

internal class JvmClassRefType(val name: String, isNullable: Boolean? = null, annotations: List<JcAnnotation>)
    : JvmRefType(isNullable, annotations) {

    override val displayName: String
        get() = name

}

/**
 * For type variables, the nullability is defined similarly to all other types:
 *  - kt T? and java @Nullable T -- nullable (true)
 *  - kt T and java @NotNull T -- non-nullable (false)
 *  - java T -- undefined nullability (null)
 *
 *  This is important to properly handle nullability during substitutions. Not that kt T and java @NotNull T still have
 *  differences -- see comment for `JcSubstitutorImpl.relaxNullabilityAfterSubstitution` for more details
 */
internal class JvmTypeVariable(val symbol: String, isNullable: Boolean? = null, annotations: List<JcAnnotation>)
    : JvmRefType(isNullable, annotations) {

    constructor(declaration: JvmTypeParameterDeclaration, isNullable: Boolean? = null, annotations: List<JcAnnotation>) : this(
        declaration.symbol,
        isNullable
    , annotations) {
        this.declaration = declaration
    }

    var declaration: JvmTypeParameterDeclaration? = null

    override val displayName: String
        get() = symbol

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as JvmTypeVariable

        if (symbol != other.symbol) return false
        if (declaration != other.declaration) return false
        if (isNullable != other.isNullable) return false

        return true
    }

    override fun hashCode(): Int {
        var result = symbol.hashCode()
        result = 63 * result + 31 * (declaration?.hashCode() ?: 1) + (isNullable?.hashCode() ?: 0)
        return result
    }
}

// Nullability has no sense in wildcards, so we suppose them to be always nullable for definiteness
internal sealed class JvmWildcard : JvmType(isNullable = true, listOf())

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

internal class JvmPrimitiveType(val ref: String, annotations: List<JcAnnotation> = listOf())
    : JvmRefType(isNullable = false, annotations) {

    companion object {
        fun of(descriptor: Char): JvmType {
            return when (descriptor) {
                'V' -> JvmPrimitiveType(PredefinedPrimitives.Void)
                'Z' -> JvmPrimitiveType(PredefinedPrimitives.Boolean)
                'B' -> JvmPrimitiveType(PredefinedPrimitives.Byte)
                'S' -> JvmPrimitiveType(PredefinedPrimitives.Short)
                'C' -> JvmPrimitiveType(PredefinedPrimitives.Char)
                'I' -> JvmPrimitiveType(PredefinedPrimitives.Int)
                'J' -> JvmPrimitiveType(PredefinedPrimitives.Long)
                'F' -> JvmPrimitiveType(PredefinedPrimitives.Float)
                'D' -> JvmPrimitiveType(PredefinedPrimitives.Double)
                else -> throw IllegalArgumentException("Not a valid primitive type descriptor: $descriptor")
            }
        }
    }

    override val displayName: String
        get() = ref

}

internal interface JvmTypeVisitor<ContextType> {
    fun visitType(type: JvmType, context: ContextType): JvmType {
        return when (type) {
            is JvmPrimitiveType -> type
            is JvmBoundWildcard.JvmLowerBoundWildcard -> visitLowerBound(type, context)
            is JvmBoundWildcard.JvmUpperBoundWildcard -> visitUpperBound(type, context)
            is JvmParameterizedType -> visitParameterizedType(type, context)
            is JvmArrayType -> visitArrayType(type, context)
            is JvmClassRefType -> visitClassRef(type, context)
            is JvmTypeVariable -> visitTypeVariable(type, context)
            is JvmUnboundWildcard -> type
            is JvmParameterizedType.JvmNestedType -> visitNested(type, context)
        }
    }

    fun visitUpperBound(type: JvmBoundWildcard.JvmUpperBoundWildcard, context: ContextType): JvmType

    fun visitLowerBound(type: JvmBoundWildcard.JvmLowerBoundWildcard, context: ContextType): JvmType

    fun visitArrayType(type: JvmArrayType, context: ContextType): JvmType

    fun visitTypeVariable(type: JvmTypeVariable, context: ContextType): JvmType

    fun visitClassRef(type: JvmClassRefType, context: ContextType): JvmType

    fun visitNested(type: JvmParameterizedType.JvmNestedType, context: ContextType): JvmType

    fun visitParameterizedType(type: JvmParameterizedType, context: ContextType): JvmType
}