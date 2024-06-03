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

package org.jacodb.panda.dynamic.ark.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface Type

@Serializable
@SerialName("AnyType")
object AnyType : Type {
    override fun toString(): String = javaClass.simpleName
}

@Serializable
@SerialName("UnknownType")
object UnknownType : Type {
    override fun toString(): String = javaClass.simpleName
}

@Serializable
@SerialName("UnionType")
data class UnionType(
    val types: List<Type>,
) : Type

@Serializable
@SerialName("IntersectionType")
data class TupleType(
    val types: List<Type>,
) : Type

// TODO: EnumType

@Serializable
sealed interface PrimitiveType : Type

@Serializable
@SerialName("BooleanType")
object BooleanType : PrimitiveType {
    override fun toString(): String = javaClass.simpleName
}

@Serializable
@SerialName("NumberType")
object NumberType : PrimitiveType {
    override fun toString(): String = javaClass.simpleName
}

@Serializable
@SerialName("StringType")
object StringType : PrimitiveType {
    override fun toString(): String = javaClass.simpleName
}

@Serializable
@SerialName("NullType")
object NullType : PrimitiveType {
    override fun toString(): String = javaClass.simpleName
}

@Serializable
@SerialName("UndefinedType")
object UndefinedType : PrimitiveType {
    override fun toString(): String = javaClass.simpleName
}

@Serializable
@SerialName("VoidType")
object VoidType : PrimitiveType {
    override fun toString(): String = javaClass.simpleName
}

@Serializable
@SerialName("NeverType")
object NeverType : PrimitiveType {
    override fun toString(): String = javaClass.simpleName
}

@Serializable
@SerialName("LiteralType")
data class LiteralType(
    val literal: String,
) : PrimitiveType

@Serializable
sealed interface RefType : Type

@Serializable
@SerialName("ClassType")
data class ClassType(
    val className: String,
) : RefType

@Serializable
@SerialName("ArrayType")
data class ArrayType(
    val elementType: Type,
    val dimensions: Int,
) : RefType

@Serializable
@SerialName("ArrayObjectType")
data class ArrayObjectType(
    val elementType: Type,
) : RefType

@Serializable
@SerialName("UnclearRefType")
data class UnclearRefType(
    val typeName: String,
) : RefType
