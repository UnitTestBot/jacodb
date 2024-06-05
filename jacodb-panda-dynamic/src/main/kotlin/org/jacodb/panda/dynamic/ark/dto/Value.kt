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
sealed interface Value {
    val type: String
}

@Serializable
@SerialName("UnknownValue")
data class UnknownValue(
    val value: JsonElement?,
) : Value {
    override val type: String
        get() = "unknown"
}

@Serializable
@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("_")
sealed interface Immediate : Value

@Serializable
@SerialName("Local")
data class Local(
    val name: String,
    override val type: String,
) : Immediate

@Serializable
@SerialName("Constant")
data class Constant(
    val value: String,
    override val type: String,
) : Immediate

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
@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("_")
sealed interface Expr : Value

@Serializable
@SerialName("NewExpr")
data class NewExpr(
    override val type: String,
) : Expr

@Serializable
@SerialName("NewArrayExpr")
data class NewArrayExpr(
    override val type: String,
    val size: Value,
) : Expr

@Serializable
@SerialName("TypeOfExpr")
data class TypeOfExpr(
    val arg: Value,
) : Expr {
    override val type: String
        get() = "string"
}

@Serializable
@SerialName("InstanceOfExpr")
data class InstanceOfExpr(
    val arg: Value,
    @SerialName("type") val checkType: String,
) : Expr {
    override val type: String
        get() = "boolean"
}

@Serializable
@SerialName("LengthExpr")
data class LengthExpr(
    val arg: Value,
) : Expr {
    override val type: String
        get() = "number"
}

@Serializable
@SerialName("CastExpr")
data class CastExpr(
    val arg: Value,
    override val type: String,
) : Expr

@Serializable
@SerialName("PhiExpr")
data class PhiExpr(
    val args: List<Value>,
    // val argToBlock: Map<Value, BasicBlock>, // TODO
    override val type: String,
) : Expr

@Serializable
@SerialName("ArrayLiteralExpr")
data class ArrayLiteralExpr(
    val elements: List<Value>,
    override val type: String,
) : Expr

// @Serializable
// @SerialName("ObjectLiteralExpr")
// data class ObjectLiteralExpr(
//     val keys: List<String>,
//     val values: List<Value>,
//     override val type: Type,
// ) : Expr

@Serializable
@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("_")
sealed interface UnaryExpr : Expr {
    val arg: Value

    override val type: String
        get() = arg.type
}

@Serializable
@SerialName("UnopExpr")
data class UnaryOperation(
    val op: String,
    override val arg: Value,
) : UnaryExpr

@Serializable
@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("_")
sealed interface BinaryExpr : Expr {
    val left: Value
    val right: Value

    override val type: String
        get() = "any"
}

@Serializable
@SerialName("BinaryOperation")
data class BinaryOperation(
    val op: String,
    override val left: Value,
    override val right: Value,
) : BinaryExpr {
    override fun toString(): String {
        return "$left $op $right"
    }
}

@Serializable
@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("_")
sealed interface ConditionExpr : BinaryExpr {
    override val type: String
        get() = "boolean"
}

@Serializable
@SerialName("ConditionExpr")
data class RelationOperation(
    val op: String,
    override val left: Value,
    override val right: Value,
) : ConditionExpr {
    override fun toString(): String {
        return "$left $op $right"
    }
}

@Serializable
@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("_")
sealed interface CallExpr : Expr {
    val method: MethodSignature
    val args: List<Value>

    override val type: String
        get() = method.returnType
}

@Serializable
@SerialName("InstanceCallExpr")
data class InstanceCallExpr(
    val instance: Value, // Local
    override val method: MethodSignature,
    override val args: List<Value>,
) : CallExpr

@Serializable
@SerialName("StaticInvokeExpr")
data class StaticCallExpr(
    override val method: MethodSignature,
    override val args: List<Value>,
) : CallExpr

@Serializable
@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("_")
sealed interface Ref : Value

@Serializable
@SerialName("ThisRef")
data class ThisRef(
    override val type: String,
) : Ref {
    override fun toString(): String = "this"
}

@Serializable
@SerialName("ParameterRef")
data class ParameterRef(
    val index: Int,
    override val type: String,
) : Ref {
    override fun toString(): String {
        return "arg$index"
    }
}

@Serializable
@SerialName("ArrayAccess")
data class ArrayAccess(
    val array: Value,
    val index: Value,
    override val type: String,
) : Ref {
    override fun toString(): String {
        return "$array[$index]"
    }
}

@Serializable
@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("_")
sealed interface FieldRef : Ref {
    val field: FieldSignature

    override val type: String
        get() = this.field.fieldType
}

@Serializable
@SerialName("InstanceFieldRef")
data class InstanceFieldRef(
    val instance: Value, // Local
    override val field: FieldSignature,
) : FieldRef

@Serializable
@SerialName("StaticFieldRef")
data class StaticFieldRef(
    override val field: FieldSignature,
) : FieldRef
