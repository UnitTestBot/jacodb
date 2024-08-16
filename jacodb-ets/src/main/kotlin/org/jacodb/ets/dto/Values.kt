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
import kotlinx.serialization.json.JsonElement

@Serializable
@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("_")
sealed interface ValueDto {
    val type: TypeDto
}

@Serializable
@SerialName("UNKNOWN_VALUE")
data class UnknownValueDto(
    val value: JsonElement,
) : ValueDto {
    override val type: TypeDto
        get() = UnknownTypeDto

    override fun toString(): String {
        return "UNKNOWN($value)"
    }
}

@Serializable
sealed interface ImmediateDto : ValueDto

@Serializable
@SerialName("Local")
data class LocalDto(
    val name: String,
    override val type: TypeDto,
) : ImmediateDto {
    override fun toString(): String {
        return name
    }
}

@Serializable
@SerialName("Constant")
data class ConstantDto(
    val value: String,
    override val type: TypeDto,
) : ImmediateDto {
    override fun toString(): String {
        return "'$value': $type"
    }
}

// TODO: uncomment and adapt the following code for different constants
//       Currently, ArkIR stores all constants as strings (see ConstantDto above),
//       but some day this is going to be fixed.
//       I expect that the solution on the arkanalyzer's side to be similar to the one below,
//       so I'm keeping it here for future reference.
//
// @Serializable
// sealed interface ConstantDto: ImmediateDto
//
// @Serializable
// @SerialName("StringConstant")
// data class StringConstantDto(
//     val value: String,
// ) : ConstantDto {
//     override val type: Type
//         get() = StringType
//
//     override fun toString(): String = "\"$value\""
// }
//
// @Serializable
// @SerialName("BooleanConstant")
// data class BooleanConstantDto(
//     val value: Boolean,
// ) : ConstantDto {
//     override val type: Type
//         get() = BooleanType
//
//     override fun toString(): String = value.toString()
//
//     companion object {
//         val TRUE = BooleanConstant(true)
//         val FALSE = BooleanConstant(false)
//     }
// }
//
// @Serializable
// @SerialName("NumberConstant")
// data class NumberConstantDto(
//     val value: Double,
// ) : ConstantDto {
//     override val type: Type
//         get() = NumberType
//
//     override fun toString(): String = value.toString()
// }
//
// @Serializable
// @SerialName("NullConstant")
// object NullConstantDto : ConstantDto {
//     override val type: Type
//         get() = NullType
//
//     override fun toString(): String = "null"
// }
//
// @Serializable
// @SerialName("UndefinedConstant")
// object UndefinedConstantDto : ConstantDto {
//     override val type: Type
//         get() = UndefinedType
//
//     override fun toString(): String = "undefined"
// }

@Serializable
sealed interface ExprDto : ValueDto

@Serializable
@SerialName("NewExpr")
data class NewExprDto(
    val classType: TypeDto, // ClassType
) : ExprDto {
    override val type: TypeDto
        get() = classType as ClassTypeDto // safe cast

    override fun toString(): String {
        return "new $type()"
    }
}

@Serializable
@SerialName("NewArrayExpr")
data class NewArrayExprDto(
    val elementType: TypeDto,
    val size: ValueDto,
) : ExprDto {
    override val type: TypeDto
        get() = ArrayTypeDto(elementType, 1)

    override fun toString(): String {
        return "new Array<$elementType>($size)"
    }
}

@Serializable
@SerialName("DeleteExpr")
data class DeleteExprDto(
    val arg: ValueDto,
) : ExprDto {
    override val type: TypeDto
        get() = BooleanTypeDto

    override fun toString(): String {
        return "delete $arg"
    }
}

@Serializable
@SerialName("AwaitExpr")
data class AwaitExprDto(
    val arg: ValueDto,
) : ExprDto {
    override val type: TypeDto
        get() = arg.type

    override fun toString(): String {
        return "await $arg"
    }
}

@Serializable
@SerialName("YieldExpr")
data class YieldExprDto(
    val arg: ValueDto,
) : ExprDto {
    override val type: TypeDto
        get() = arg.type

    override fun toString(): String {
        return "yield $arg"
    }
}

@Serializable
@SerialName("TypeOfExpr")
data class TypeOfExprDto(
    val arg: ValueDto,
) : ExprDto {
    override val type: TypeDto
        get() = StringTypeDto

    override fun toString(): String {
        return "typeof $arg"
    }
}

@Serializable
@SerialName("InstanceOfExpr")
data class InstanceOfExprDto(
    val arg: ValueDto,
    val checkType: TypeDto,
) : ExprDto {
    override val type: TypeDto
        get() = BooleanTypeDto

    override fun toString(): String {
        return "$arg instanceof $checkType"
    }
}

@Serializable
@SerialName("LengthExpr")
data class LengthExprDto(
    val arg: ValueDto,
) : ExprDto {
    override val type: TypeDto
        get() = NumberTypeDto

    override fun toString(): String {
        return "$arg.length"
    }
}

@Serializable
@SerialName("CastExpr")
data class CastExprDto(
    val arg: ValueDto,
    override val type: TypeDto,
) : ExprDto {
    override fun toString(): String {
        return "$arg as $type"
    }
}

@Serializable
@SerialName("PhiExpr")
data class PhiExprDto(
    val args: List<ValueDto>,
    val blocks: List<Int>,
    override val type: TypeDto,
) : ExprDto {
    override fun toString(): String {
        return "phi(${args.joinToString()})"
    }
}

@Serializable
@SerialName("ArrayLiteralExpr")
data class ArrayLiteralDto(
    val elements: List<ValueDto>,
    override val type: TypeDto,
) : ExprDto {
    override fun toString(): String {
        return "[" + elements.joinToString() + "]"
    }
}

@Serializable
@SerialName("ObjectLiteralExpr")
data class ObjectLiteralDto(
    val anonymousClass: ClassSignatureDto,
    override val type: TypeDto,
) : ExprDto

@Serializable
sealed interface UnaryExprDto : ExprDto {
    val arg: ValueDto

    override val type: TypeDto
        get() = arg.type
}

@Serializable
@SerialName("UnopExpr")
data class UnaryOperationDto(
    val op: String,
    override val arg: ValueDto,
) : UnaryExprDto {
    override fun toString(): String {
        return "$op $arg"
    }
}

@Serializable
sealed interface BinaryExprDto : ExprDto {
    val left: ValueDto
    val right: ValueDto
}

@Serializable
@SerialName("BinopExpr")
data class BinaryOperationDto(
    val op: String,
    override val left: ValueDto,
    override val right: ValueDto,
    override val type: TypeDto = UnknownTypeDto,
) : BinaryExprDto {
    override fun toString(): String {
        return "$left $op $right"
    }
}

@Serializable
sealed interface ConditionExprDto : BinaryExprDto

@Serializable
@SerialName("ConditionExpr")
data class RelationOperationDto(
    val op: String,
    override val left: ValueDto,
    override val right: ValueDto,
    override val type: TypeDto = UnknownTypeDto,
) : ConditionExprDto {
    override fun toString(): String {
        return "$left $op $right"
    }
}

@Serializable
sealed interface CallExprDto : ExprDto {
    val method: MethodSignatureDto
    val args: List<ValueDto>

    override val type: TypeDto
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
        return "$instance.${method.name}(${args.joinToString()})"
    }
}

@Serializable
@SerialName("StaticCallExpr")
data class StaticCallExprDto(
    override val method: MethodSignatureDto,
    override val args: List<ValueDto>,
) : CallExprDto {
    override fun toString(): String {
        return "${method.name}(${args.joinToString()})"
    }
}

@Serializable
sealed interface RefDto : ValueDto

@Serializable
@SerialName("ThisRef")
data class ThisRefDto(
    override val type: TypeDto, // ClassType
) : RefDto {
    override fun toString(): String {
        return "this"
    }
}

@Serializable
@SerialName("ParameterRef")
data class ParameterRefDto(
    val index: Int,
    override val type: TypeDto,
) : RefDto {
    override fun toString(): String {
        return "arg$index"
    }
}

@Serializable
@SerialName("ArrayRef")
data class ArrayRefDto(
    val array: ValueDto,
    val index: ValueDto,
    override val type: TypeDto,
) : RefDto {
    override fun toString(): String {
        return "$array[$index]"
    }
}

@Serializable
sealed interface FieldRefDto : RefDto {
    val field: FieldSignatureDto

    override val type: TypeDto
        get() = this.field.type
}

@Serializable
@SerialName("InstanceFieldRef")
data class InstanceFieldRefDto(
    val instance: ValueDto, // Local
    override val field: FieldSignatureDto,
) : FieldRefDto {
    override fun toString(): String {
        return "$instance.${field.name}"
    }
}

@Serializable
@SerialName("StaticFieldRef")
data class StaticFieldRefDto(
    override val field: FieldSignatureDto,
) : FieldRefDto {
    override fun toString(): String {
        return "${field.declaringClass.name}.${field.name}"
    }
}
