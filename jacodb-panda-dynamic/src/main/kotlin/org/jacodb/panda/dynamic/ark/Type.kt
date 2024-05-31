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

package org.jacodb.panda.dynamic.ark

interface Type {
    val typeName: String
}

object AnyType : Type {
    override val typeName: String
        get() = "any"

    override fun toString(): String = typeName
}

object UnknownType : Type {
    override val typeName: String
        get() = "unknown"

    override fun toString(): String = typeName
}

data class UnionType(
    val types: List<Type>,
) : Type {
    override val typeName: String
        get() = types.joinToString(separator = " | ") { it.typeName }

    override fun toString(): String = typeName
}

data class TupleType(
    val types: List<Type>,
) : Type {
    override val typeName: String
        get() = types.joinToString(prefix = "[", postfix = "]") { it.typeName }

    override fun toString(): String = typeName
}

// TODO: EnumType

interface PrimitiveType : Type

object BooleanType : PrimitiveType {
    override val typeName: String
        get() = "boolean"

    override fun toString(): String = typeName
}

object NumberType : PrimitiveType {
    override val typeName: String
        get() = "number"

    override fun toString(): String = typeName
}

object StringType : PrimitiveType {
    override val typeName: String
        get() = "string"

    override fun toString(): String = typeName
}

object NullType : PrimitiveType {
    override val typeName: String
        get() = "null"

    override fun toString(): String = typeName
}

object UndefinedType : PrimitiveType {
    override val typeName: String
        get() = "undefined"

    override fun toString(): String = typeName
}

object VoidType : PrimitiveType {
    override val typeName: String
        get() = "void"

    override fun toString(): String = typeName
}

object NeverType : PrimitiveType {
    override val typeName: String
        get() = "never"

    override fun toString(): String = typeName
}

data class LiteralType(
    val literalTypeName: String,
) : PrimitiveType {
    override val typeName: String
        get() = "literal"

    override fun toString(): String = typeName
}

interface RefType : Type

data class ClassType(
    val classSignature: ClassSignature,
) : RefType {
    override val typeName: String
        get() = classSignature.name

    override fun toString(): String = typeName
}

data class ArrayType(
    val elementType: Type,
    val dimensions: Int,
) : RefType {
    override val typeName: String
        get() = elementType.typeName + "[]".repeat(dimensions)

    override fun toString(): String = typeName
}

data class ArrayObjectType(
    val elementType: Type,
) : RefType {
    override val typeName: String
        get() = "Array<${elementType.typeName}>"

    override fun toString(): String = typeName
}

data class UnclearRefType(
    override val typeName: String,
) : RefType {
    override fun toString(): String = typeName
}
