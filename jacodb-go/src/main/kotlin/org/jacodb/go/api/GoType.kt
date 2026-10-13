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

package org.jacodb.go.api

import org.jacodb.api.common.CommonType
import org.jacodb.api.common.CommonTypeName
import java.util.IdentityHashMap

interface GoType : CommonType, CommonTypeName {
    override val nullable: Boolean?
        get() = false
}

abstract class AbstractGoType : GoType {
    override fun equals(other: Any?): Boolean {
        if (other !is GoType || javaClass != other.javaClass) {
            return false
        }
        return this.typeName == other.typeName
    }

    override fun hashCode(): Int {
        return 31 * javaClass.hashCode() + typeName.hashCode()
    }

    override fun toString(): String = typeName
}

class NullType : GoType, AbstractGoType() {
    override val typeName = "null"
}

class LongType : GoType, AbstractGoType() {
    override val typeName = "long"
}

class ArrayType(
    val len: Long,
    val elementType: GoType
) : GoType, AbstractGoType() {
    override val typeName: String
        get() = renderGoType(this)
}

class BasicType(
    override val typeName: String
) : GoType, AbstractGoType()

class ChanType(
    val direction: Long,
    val elementType: GoType
) : GoType, AbstractGoType() {
    override val typeName: String
        get() = renderGoType(this)
}

class InterfaceType(
    val methods: List<String>,
    val name: String,
) : GoType, AbstractGoType() {
    override val typeName: String
        get() = name
}

class MapType(
    val keyType: GoType,
    val valueType: GoType
) : GoType, AbstractGoType() {
    override val typeName: String
        get() = renderGoType(this)
}

class NamedType(
    var underlyingType: GoType,
    val name: String,
    val methods: List<String>
) : GoType, AbstractGoType() {
    override val typeName: String
        get() = name
}

class PointerType(
    var baseType: GoType
) : GoType, AbstractGoType() {
    override val typeName: String
        get() = renderGoType(this)
}

class SignatureType(
    val params: TupleType,
    val results: TupleType
) : GoType, AbstractGoType() {
    override val typeName: String
        get() = renderGoType(this)
}

class SliceType(
    val elementType: GoType
) : GoType, AbstractGoType() {
    override val typeName: String
        get() = renderGoType(this)
}

class StructType(
    val fields: List<GoType>?,
    val tags: List<String>?
) : GoType, AbstractGoType() {
    override val typeName: String
        get() = renderGoType(this)
}

class TupleType(
    val types: List<GoType>
) : GoType, AbstractGoType() {
    override val typeName: String
        get() = renderGoType(this)
}

class TypeParam(
    override val typeName: String
) : GoType, AbstractGoType()

class UnionType(
    val terms: List<GoType>
) : GoType, AbstractGoType() {
    override val typeName: String
        get() = renderGoType(this)
}

class OpaqueType(
    override val typeName: String
) : GoType, AbstractGoType()

/** Named declarations terminate recursion; mutable placeholder cycles are also safe to inspect. */
private fun renderGoType(type: GoType): String {
    val visiting = IdentityHashMap<GoType, Boolean>()

    fun render(current: GoType): String {
        if (visiting.put(current, true) != null) return "<recursive:${current.javaClass.simpleName}>"

        val result = when (current) {
            is ArrayType -> "[${current.len}]${render(current.elementType)}"
            is ChanType -> {
                val element = render(current.elementType)
                when (current.direction) {
                    0L -> "chan $element"
                    1L -> "<-chan $element"
                    2L -> "chan <-$element"
                    else -> "invalid chan $element"
                }
            }
            is MapType -> "map[${render(current.keyType)}]${render(current.valueType)}"
            is PointerType -> "*${render(current.baseType)}"
            is SignatureType -> "func ${render(current.params)} ${render(current.results)}"
            is SliceType -> "[]${render(current.elementType)}"
            is StructType -> buildString {
                append("struct {\n")
                current.fields.orEmpty().forEachIndexed { index, field ->
                    append(render(field))
                    val tag = current.tags?.getOrNull(index)
                    if (tag != null) append(" $tag")
                    append('\n')
                }
                append('}')
            }
            is TupleType -> current.types.joinToString(prefix = "(", postfix = ")", separator = ", ") { render(it) }
            is UnionType -> current.terms.joinToString(prefix = "enum {\n", postfix = "}", separator = ",\n") {
                render(it)
            }
            else -> current.typeName
        }
        visiting.remove(current)
        return result
    }

    return render(type)
}
