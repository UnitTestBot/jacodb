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

interface Expr : Value

data class NewExpr(
    override val type: ClassType,
) : Expr {
    override fun toString(): String {
        return "new ${type.typeName}"
    }
}

data class NewArrayExpr(
    override val type: ArrayType,
    val size: Value,
) : Expr {
    override fun toString(): String {
        return "new ${type.typeName}[$size]"
    }
}

data class TypeOfExpr(
    val arg: Value,
) : Expr {
    override val type: Type
        get() = arg.type

    override fun toString(): String {
        return "typeof $arg"
    }
}

data class InstanceOfExpr(
    val arg: Value,
    val checkType: Type,
) : Expr {
    override val type: Type
        get() = BooleanType

    override fun toString(): String {
        return "$arg instanceof $checkType"
    }
}

data class LengthExpr(
    val arg: Value,
) : Expr {
    override val type: Type
        get() = NumberType

    override fun toString(): String {
        return "$arg.length"
    }
}

data class CastExpr(
    val arg: Value,
    override val type: Type,
) : Expr {
    override fun toString(): String {
        return "$arg as $type"
    }
}

// data class TernaryExpr(
//     val condition: Value,
//     val trueBranch: Value,
//     val falseBranch: Value,
// ) : Expr {
//     override val type: Type
//         get() = TypeInference.commonType(trueBranch.type, trueBranch.type)
//
//     override fun toString(): String {
//         return "$condition ? $trueBranch : $falseBranch"
//     }
// }

data class PhiExpr(
    val lhv: Value,
    val args: List<Value>,
    // TODO: blocks
) : Expr {
    override val type: Type
        get() = lhv.type

    override fun toString(): String {
        return "$lhv := phi(${args.joinToString()})"
    }
}

interface UnaryExpr : Expr {
    val arg: Value

    override val type: Type
        get() = arg.type
}

data class UnaryOperation(
    val op: UnaryOp,
    override val arg: Value,
) : UnaryExpr {
    override fun toString(): String {
        return "$op$arg"
    }
}

interface BinaryExpr : Expr {
    val left: Value
    val right: Value

    override val type: Type
        get() = TypeInference.infer(this)
}

data class BinaryOperation(
    val op: BinaryOp,
    override val left: Value,
    override val right: Value,
) : BinaryExpr {
    override fun toString(): String {
        return "$left $op $right"
    }
}

data class ConditionExpr(
    val relop: String,
    override val left: Value,
    override val right: Value,
) : BinaryExpr {
    override fun toString(): String {
        return "$left $relop $right"
    }
}

interface CallExpr : Expr {
    val method: MethodSignature
    val args: List<Value>

    override val type: Type
        get() = method.sub.returnType
}

data class InstanceCallExpr(
    val instance: Local,
    override val method: MethodSignature,
    override val args: List<Value>,
) : CallExpr {
    override fun toString(): String {
        return "$instance.${method.sub.name}(${args.joinToString()})"
    }
}

data class StaticCallExpr(
    override val method: MethodSignature,
    override val args: List<Value>,
) : CallExpr {
    override fun toString(): String {
        return "${method.enclosingClass}::${method.sub.name}(${args.joinToString()})"
    }
}
