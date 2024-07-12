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

package org.jacodb.panda.dynamic.ets.dto

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@Serializable
@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("_")
sealed interface TypeDto

@Serializable
@SerialName("AnyType")
object AnyTypeDto : TypeDto {
    override fun toString(): String {
        return "any"
    }
}

@Serializable
@SerialName("UnknownType")
object UnknownTypeDto : TypeDto {
    override fun toString(): String {
        return "unknown"
    }
}

@Serializable
@SerialName("VoidType")
object VoidTypeDto : TypeDto {
    override fun toString(): String {
        return "void"
    }
}

@Serializable
@SerialName("NeverType")
object NeverTypeDto : TypeDto {
    override fun toString(): String {
        return "never"
    }
}

@Serializable
@SerialName("UnionType")
data class UnionTypeDto(
    val types: List<TypeDto>,
) : TypeDto {
    override fun toString(): String {
        return types.joinToString(" | ")
    }
}

@Serializable
@SerialName("TupleType")
data class TupleTypeDto(
    val types: List<TypeDto>,
) : TypeDto {
    override fun toString(): String {
        return "[${types.joinToString()}]"
    }
}

@Serializable
sealed interface PrimitiveTypeDto : TypeDto {
    val name: String
}

@Serializable
@SerialName("BooleanType")
object BooleanTypeDto : PrimitiveTypeDto {
    override val name: String
        get() = "boolean"

    override fun toString(): String {
        return name
    }
}

@Serializable
@SerialName("NumberType")
object NumberTypeDto : PrimitiveTypeDto {
    override val name: String
        get() = "number"

    override fun toString(): String {
        return name
    }
}

@Serializable
@SerialName("StringType")
object StringTypeDto : PrimitiveTypeDto {
    override val name: String
        get() = "string"

    override fun toString(): String {
        return name
    }
}

@Serializable
@SerialName("NullType")
object NullTypeDto : PrimitiveTypeDto {
    override val name: String
        get() = "null"

    override fun toString(): String {
        return name
    }
}

@Serializable
@SerialName("UndefinedType")
object UndefinedTypeDto : PrimitiveTypeDto {
    override val name: String
        get() = "undefined"

    override fun toString(): String {
        return name
    }
}

@Serializable
@SerialName("LiteralType")
data class LiteralTypeDto(
    val literal: String,
) : PrimitiveTypeDto {
    override val name: String
        get() = "literal"

    override fun toString(): String {
        return literal
    }
}

@Serializable
@SerialName("ClassType")
data class ClassTypeDto(
    val signature: ClassSignatureDto,
) : TypeDto {
    override fun toString(): String {
        return signature.toString()
    }
}

@Serializable
@SerialName("CallableType")
data class CallableTypeDto(
    val signature: MethodSignatureDto,
) : TypeDto {
    override fun toString(): String {
        return "(${signature.parameters.joinToString()}) => ${signature.returnType}"
    }
}

@Serializable
@SerialName("ArrayType")
data class ArrayTypeDto(
    val elementType: TypeDto,
    val dimensions: Int,
) : TypeDto {
    override fun toString(): String {
        return "$elementType[]".repeat(dimensions)
    }
}

@Serializable
@SerialName("UnclearReferenceType")
data class UnclearReferenceTypeDto(
    val name: String,
) : TypeDto {
    override fun toString(): String {
        return name
    }
}

@Serializable
@SerialName("UNKNOWN_TYPE")
data class AbsolutelyUnknownTypeDto(
    val type: String? = null,
) : TypeDto {
    override fun toString(): String {
        return type ?: "UNKNOWN"
    }
}
