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

    override fun toString(): String = javaClass.simpleName
}

object UnknownType : Type {
    override val typeName: String
        get() = "unknown"

    override fun toString(): String = javaClass.simpleName
}

interface PrimitiveType : Type

object BooleanType : PrimitiveType {
    override val typeName: String
        get() = "boolean"

    override fun toString(): String = javaClass.simpleName
}

object NumberType : PrimitiveType {
    override val typeName: String
        get() = "number"

    override fun toString(): String = javaClass.simpleName
}

object StringType : PrimitiveType {
    override val typeName: String
        get() = "string"

    override fun toString(): String = javaClass.simpleName
}

object NullType : PrimitiveType {
    override val typeName: String
        get() = "null"

    override fun toString(): String = javaClass.simpleName
}

object UndefinedType : PrimitiveType {
    override val typeName: String
        get() = "undefined"

    override fun toString(): String = javaClass.simpleName
}

object VoidType : PrimitiveType {
    override val typeName: String
        get() = "void"

    override fun toString(): String = javaClass.simpleName
}

object NeverType : PrimitiveType {
    override val typeName: String
        get() = "never"

    override fun toString(): String = javaClass.simpleName
}

data class LiteralType(
    val literalTypeName: String,
) : PrimitiveType {
    override val typeName: String
        get() = "literal"
}

data class UnionType(
    val types: List<Type>,
) : Type {
    override val typeName: String
        get() = types.joinToString(separator = " | ") { it.typeName }
}

data class TupleType(
    val types: List<Type>,
) : Type {
    override val typeName: String
        get() = types.joinToString(prefix = "[", postfix = "]") { it.typeName }
}

interface RefType : Type

interface ClassType : RefType {
    val className: String
}

interface ArrayType : RefType {
    val elementType: Type
    val dimensions: Int
}

data class ArrayTypeImpl(
    override val elementType: Type,
    override val dimensions: Int,
) : ArrayType {
    override val typeName: String
        get() = elementType.typeName + "[]".repeat(dimensions)
}
