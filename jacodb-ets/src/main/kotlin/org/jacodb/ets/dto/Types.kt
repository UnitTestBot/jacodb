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

package org.jacodb.ets.dto

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonObject

@Serializable
@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("_")
sealed interface TypeDto

@Serializable(with = RawTypeSerializer::class)
@SerialName("RawType")
@OptIn(ExperimentalSerializationApi::class)
data class RawTypeDto(
    val kind: String, // constructor name
    val extra: JsonObject,
) : TypeDto

@Serializable
@SerialName("AnyType")
data object AnyTypeDto : TypeDto

@Serializable
@SerialName("UnknownType")
data object UnknownTypeDto : TypeDto

@Serializable
@SerialName("GenericType")
data class GenericTypeDto(
    val name: String,
    val constraint: TypeDto? = null,
    val defaultType: TypeDto? = null,
) : TypeDto

@Serializable
@SerialName("AliasType")
data class AliasTypeDto(
    val name: String,
    val originalType: TypeDto,
    val signature: LocalSignatureDto,
) : TypeDto

@Serializable
@SerialName("EnumValueType")
data class EnumValueTypeDto(
    val signature: FieldSignatureDto,
    val constant: ConstantDto? = null,
): TypeDto

@Serializable
@SerialName("VoidType")
data object VoidTypeDto : TypeDto

@Serializable
@SerialName("NeverType")
data object NeverTypeDto : TypeDto

@Serializable
@SerialName("UnionType")
data class UnionTypeDto(
    val types: List<TypeDto>,
) : TypeDto

@Serializable
@SerialName("IntersectionType")
data class IntersectionTypeDto(
    val types: List<TypeDto>,
) : TypeDto

@Serializable
sealed interface PrimitiveTypeDto : TypeDto {
    val name: String
}

@Serializable
@SerialName("BooleanType")
data object BooleanTypeDto : PrimitiveTypeDto {
    override val name: String
        get() = "boolean"
}

@Serializable
@SerialName("NumberType")
data object NumberTypeDto : PrimitiveTypeDto {
    override val name: String
        get() = "number"
}

@Serializable
@SerialName("StringType")
data object StringTypeDto : PrimitiveTypeDto {
    override val name: String
        get() = "string"
}

@Serializable
@SerialName("NullType")
data object NullTypeDto : PrimitiveTypeDto {
    override val name: String
        get() = "null"
}

@Serializable
@SerialName("UndefinedType")
data object UndefinedTypeDto : PrimitiveTypeDto {
    override val name: String
        get() = "undefined"
}

@Serializable
@SerialName("LiteralType")
data class LiteralTypeDto(
    val literal: PrimitiveLiteralDto,
) : PrimitiveTypeDto {
    override val name: String
        get() = literal.toString()
}

@Serializable(with = PrimitiveLiteralSerializer::class)
sealed class PrimitiveLiteralDto {
    data class StringLiteral(val value: String) : PrimitiveLiteralDto()

    data class NumberLiteral(val value: Double) : PrimitiveLiteralDto()

    data class BooleanLiteral(val value: Boolean) : PrimitiveLiteralDto()
}

@Serializable
@SerialName("ClassType")
data class ClassTypeDto(
    val signature: ClassSignatureDto,
    val typeParameters: List<TypeDto> = emptyList(),
) : TypeDto

@Serializable
@SerialName("UnclearReferenceType")
data class UnclearReferenceTypeDto(
    val name: String,
    val typeParameters: List<TypeDto> = emptyList(),
) : TypeDto

@Serializable
@SerialName("ArrayType")
data class ArrayTypeDto(
    val elementType: TypeDto,
    val dimensions: Int,
) : TypeDto

@Serializable
@SerialName("TupleType")
data class TupleTypeDto(
    val types: List<TypeDto>,
) : TypeDto

@Serializable
@SerialName("FunctionType")
data class FunctionTypeDto(
    val signature: MethodSignatureDto,
    val typeParameters: List<TypeDto> = emptyList(),
) : TypeDto
