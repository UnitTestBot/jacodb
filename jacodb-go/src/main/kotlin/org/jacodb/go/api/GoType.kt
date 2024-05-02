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

interface GoType : CommonType, CommonTypeName {
    override val nullable: Boolean?
        get() = false
}

abstract class AbstractGoType : GoType {
    override fun equals(other: Any?): Boolean {
        if (other !is GoType) {
            return false
        }
        return this.typeName == other.typeName
    }

    override fun hashCode(): Int {
        return javaClass.hashCode()
    }
}

class NullType: GoType, AbstractGoType() {
    override val typeName = "null"
}

class LongType: GoType, AbstractGoType() {
    override val typeName = "long"
}

class ArrayType(
    val len: Long,
    val elementType: GoType
) : GoType, AbstractGoType() {
    override val typeName: String
        get() = "[${len}]${elementType.typeName}"
}

class BasicType(
    override val typeName: String
) : GoType, AbstractGoType()

class ChanType(
    val direction: Long,
    val elementType: GoType
) : GoType, AbstractGoType() {
    override val typeName: String
        get(): String {
            var res = elementType.typeName
            if (direction == 0L) {
                res = "chan $res"
            }
            else if (direction == 1L) {
                res = "<-chan $res"
            }
            else if (direction == 2L) {
                res = "chan <-$res"
            }
            return res
        }
}

class InterfaceType() : GoType, AbstractGoType() {
    override val typeName = "Any"
}

class MapType(
    val keyType: GoType,
    val valueType: GoType
) : GoType, AbstractGoType() {
    override val typeName: String
        get() = "map[${keyType.typeName}]${valueType.typeName}"
}

class NamedType(
    var underlyingType: GoType
) : GoType, AbstractGoType() {
    override val typeName: String
        get() = underlyingType.typeName
}

class PointerType(
    var baseType: GoType
) : GoType, AbstractGoType() {
    override val typeName: String
        get() = baseType.typeName
}

class SignatureType(
    val params: TupleType,
    val results: TupleType
) : GoType, AbstractGoType() {
    override val typeName: String
        get(): String {
            return "func " + params.typeName + " " + results.typeName
        }
}

class SliceType(
    val elementType: GoType
) : GoType, AbstractGoType() {
    override val typeName: String
        get() = "[]${elementType.typeName}"
}

class StructType(
    val fields: List<GoType>?,
    val tags: List<String>?
) : GoType, AbstractGoType() {
    override val typeName: String
        get(): String {
            var res = "struct {\n"
            fields!!.forEachIndexed { ind, elem ->
                res += elem.typeName
                if (tags != null && tags.size > ind) {
                    res += " " + tags[ind]
                }
                res += "\n"
            }
            return "$res}"
        }
}

class TupleType(
    val names: List<String>
) : GoType, AbstractGoType() {
    override val typeName: String
        get(): String {
            return "(" + names.joinToString(", ") + ")"
        }
}

class TypeParam(
    override val typeName: String
) : GoType, AbstractGoType()

class UnionType(
    val terms: List<GoType>
) : GoType, AbstractGoType() {
    override val typeName: String
        get(): String {
            var res = "enum {\n"
            for (t in terms) {
                res += t.typeName + ",\n"
            }
            return "$res}"
        }
}

class OpaqueType(
    override val typeName: String
) : GoType, AbstractGoType()
