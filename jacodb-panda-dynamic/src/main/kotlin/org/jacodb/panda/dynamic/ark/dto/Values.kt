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

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonElement

@Serializable
@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("_")
sealed interface ValueDto {
    val type: String
}

@Serializable
@SerialName("UnknownValue")
data class UnknownValueDto(
    val value: JsonElement?,
) : ValueDto {
    override val type: String
        get() = "unknown"
}

@Serializable
sealed interface ImmediateDto : ValueDto

@Serializable
@SerialName("Local")
data class LocalDto(
    val name: String,
    override val type: String,
) : ImmediateDto {
    override fun toString(): String {
        return name
    }
}

@Serializable
@SerialName("Constant")
data class ConstantDto(
    val value: String,
    override val type: String,
) : ImmediateDto {
    override fun toString(): String {
        return "'$value': $type"
    }
}

// @Serializable
// @SerialName("StringConstant")
// data class StringConstant(
//     val value: String,
// ) : Constant {
//     override val type: Type
//         get() = StringType
// }
//
// @Serializable
// @SerialName("BooleanConstant")
// data class BooleanConstant(
//     val value: Boolean,
// ) : Constant {
//     override val type: Type
//         get() = BooleanType
//
//     companion object {
//         val TRUE = BooleanConstant(true)
//         val FALSE = BooleanConstant(false)
//     }
// }
//
// @Serializable
// @SerialName("NumberConstant")
// data class NumberConstant(
//     val value: Double,
// ) : Constant {
//     override val type: Type
//         get() = NumberType
// }
//
// @Serializable
// @SerialName("NullConstant")
// object NullConstant : Constant {
//     override val type: Type
//         get() = NullType
//
//     override fun toString(): String = javaClass.simpleName
// }
//
// @Serializable
// @SerialName("UndefinedConstant")
// object UndefinedConstant : Constant {
//     override val type: Type
//         get() = UndefinedType
//
//     override fun toString(): String = javaClass.simpleName
// }

// @Serializable
// @SerialName("ArrayLiteral")
// data class ArrayLiteral(
//     val elements: List<Value>,
//     override val type: ArrayType,
// ) : Constant
//
// @Serializable
// @SerialName("ObjectLiteral")
// data class ObjectLiteral(
//     val keys: List<String>,
//     val values: List<Value>,
//     override val type: Type,
// ) : Constant

@Serializable
sealed interface ExprDto : ValueDto

@Serializable
@SerialName("NewExpr")
data class NewExprDto(
    override val type: String,
) : ExprDto {
    override fun toString(): String {
        return "new $type()"
    }
}

@Serializable
@SerialName("NewArrayExpr")
data class NewArrayExprDto(
    override val type: String,
    val size: ValueDto,
) : ExprDto

@Serializable
@SerialName("TypeOfExpr")
data class TypeOfExprDto(
    val arg: ValueDto,
) : ExprDto {
    override val type: String
        get() = "string"
}

@Serializable
@SerialName("InstanceOfExpr")
data class InstanceOfExprDto(
    val arg: ValueDto,
    @SerialName("type") val checkType: String,
) : ExprDto {
    override val type: String
        get() = "boolean"

    override fun toString(): String {
        return "$arg instanceof $checkType"
    }
}

@Serializable
@SerialName("LengthExpr")
data class LengthExprDto(
    val arg: ValueDto,
) : ExprDto {
    override val type: String
        get() = "number"

    override fun toString(): String {
        return "$arg.length"
    }
}

@Serializable
@SerialName("CastExpr")
data class CastExprDto(
    val arg: ValueDto,
    override val type: String,
) : ExprDto {
    override fun toString(): String {
        return "($type)$arg"
    }
}

@Serializable
@SerialName("PhiExpr")
data class PhiExprDto(
    val args: List<ValueDto>,
    val blocks: List<Int>,
    // override val type: String, // TODO
) : ExprDto {
    override val type: String
        get() = args.first().type
}

@Serializable
@SerialName("ArrayLiteralExpr")
data class ArrayLiteralDto(
    val elements: List<ValueDto>,
    override val type: String,
) : ExprDto {
    override fun toString(): String {
        return elements.joinToString(prefix = "[", postfix = "]")
    }
}

@Serializable
@SerialName("ObjectLiteralExpr")
// TODO: keys and values
data class ObjectLiteralDto(
    // val keys: List<String>,
    // val values: List<Value>,
    override val type: String,
) : ExprDto

@Serializable
sealed interface UnaryExprDto : ExprDto {
    val arg: ValueDto

    override val type: String
        get() = arg.type
}

@Serializable
@SerialName("UnopExpr")
data class UnaryOperationDto(
    val op: String,
    override val arg: ValueDto,
) : UnaryExprDto {
    override fun toString(): String {
        return "$op$arg"
    }
}

@Serializable
sealed interface BinaryExprDto : ExprDto {
    val left: ValueDto
    val right: ValueDto

    override val type: String
        get() = "any"
}

@Serializable
@SerialName("BinopExpr")
data class BinaryOperationDto(
    val op: String,
    override val left: ValueDto,
    override val right: ValueDto,
) : BinaryExprDto {
    override fun toString(): String {
        return "$left $op $right"
    }
}

@Serializable
sealed interface ConditionExprDto : BinaryExprDto {
    override val type: String
        get() = "boolean"
}

@Serializable
@SerialName("ConditionExpr")
data class RelationOperationDto(
    val op: String,
    override val left: ValueDto,
    override val right: ValueDto,
) : ConditionExprDto {
    override fun toString(): String {
        return "$left $op $right"
    }
}

@Serializable
sealed interface CallExprDto : ExprDto {
    val method: MethodSignatureDto
    val args: List<ValueDto>

    override val type: String
        get() = method.returnType
}

@Serializable
@SerialName("InstanceCallExpr")
data class InstanceCallExprDto(
    val instance: ValueDto, // Local
    override val method: MethodSignatureDto,
    override val args: List<ValueDto>,
) : CallExprDto {
    override fun toString(): String {
        return "$instance.$method(${args.joinToString()})"
    }
}

@Serializable
@SerialName("StaticCallExpr")
data class StaticCallExprDto(
    override val method: MethodSignatureDto,
    override val args: List<ValueDto>,
) : CallExprDto {
    override fun toString(): String {
        return "$method(${args.joinToString()})"
    }
}

@Serializable
sealed interface RefDto : ValueDto

@Serializable
@SerialName("ThisRef")
data class ThisRefDto(
    override val type: String,
) : RefDto {
    override fun toString(): String = "this"
}

@Serializable
@SerialName("ParameterRef")
data class ParameterRefDto(
    val index: Int,
    override val type: String,
) : RefDto {
    override fun toString(): String {
        return "arg$index"
    }
}

@Serializable
@SerialName("ArrayRef")
data class ArrayRefDto(
    val array: ValueDto, // Local
    val index: ValueDto,
    override val type: String,
) : RefDto {
    override fun toString(): String {
        return "$array[$index]"
    }
}

@Serializable
sealed interface FieldRefDto : RefDto {
    val field: FieldSignatureDto

    override val type: String
        get() = this.field.fieldType
}

@Serializable
@SerialName("InstanceFieldRef")
data class InstanceFieldRefDto(
    val instance: ValueDto, // Local
    override val field: FieldSignatureDto,
) : FieldRefDto {
    override fun toString(): String {
        return "$instance.$field"
    }
}

@Serializable
@SerialName("StaticFieldRef")
data class StaticFieldRefDto(
    override val field: FieldSignatureDto,
) : FieldRefDto {
    override fun toString(): String {
        return field.toString()
    }
}
