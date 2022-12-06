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

sealed class JvmType {

    abstract val displayName: String

}

internal sealed class JvmRefType : JvmType()

internal class JvmArrayType(val elementType: JvmType) : JvmRefType() {

    override val displayName: String
        get() = elementType.displayName + "[]"

}

internal class JvmParameterizedType(
    val name: String,
    val parameterTypes: List<JvmType>
) : JvmRefType() {

    override val displayName: String
        get() = name + "<${parameterTypes.joinToString { it.displayName }}>"

    class JvmNestedType(
        val name: String,
        val parameterTypes: List<JvmType>,
        val ownerType: JvmType
    ) : JvmRefType() {

        override val displayName: String
            get() = name + "<${parameterTypes.joinToString { it.displayName }}>"

    }

}

internal class JvmClassRefType(val name: String) : JvmRefType() {

    override val displayName: String
        get() = name

}

open class JvmTypeVariable(val symbol: String) : JvmType() {

    constructor(declaration: JvmTypeParameterDeclaration) : this(declaration.symbol) {
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

        return true
    }

    override fun hashCode(): Int {
        var result = symbol.hashCode()
        result = 31 * result + (declaration?.hashCode() ?: 0)
        return result
    }


}

internal sealed class JvmBoundWildcard(val bound: JvmType) : JvmType() {
    internal class JvmUpperBoundWildcard(boundType: JvmType) : JvmBoundWildcard(boundType) {
        override val displayName: String
            get() = "? extends ${bound.displayName}"

    }

    internal class JvmLowerBoundWildcard(boundType: JvmType) : JvmBoundWildcard(boundType) {
        override val displayName: String
            get() = "? super ${bound.displayName}"

    }
}

internal object JvmUnboundWildcard : JvmType() {

    override val displayName: String
        get() = "*"
}

internal class JvmPrimitiveType(val ref: String) : JvmRefType() {

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