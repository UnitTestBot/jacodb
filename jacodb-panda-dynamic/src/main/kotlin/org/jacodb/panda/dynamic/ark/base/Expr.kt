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

package org.jacodb.panda.dynamic.ark.base

import org.jacodb.panda.dynamic.ark.graph.BasicBlock
import org.jacodb.panda.dynamic.ark.model.MethodSignature

interface Expr : Value {
    interface Visitor<out R> {
        fun visit(expr: NewExpr): R
        fun visit(expr: NewArrayExpr): R
        fun visit(expr: TypeOfExpr): R
        fun visit(expr: InstanceOfExpr): R
        fun visit(expr: LengthExpr): R
        fun visit(expr: CastExpr): R
        fun visit(expr: PhiExpr): R
        fun visit(expr: UnaryOperation): R
        fun visit(expr: BinaryOperation): R
        fun visit(expr: RelationOperation): R
        fun visit(expr: InstanceCallExpr): R
        fun visit(expr: StaticCallExpr): R

        interface Default<out R> : Visitor<R> {
            override fun visit(expr: NewExpr): R = defaultVisit(expr)
            override fun visit(expr: NewArrayExpr): R = defaultVisit(expr)
            override fun visit(expr: TypeOfExpr): R = defaultVisit(expr)
            override fun visit(expr: InstanceOfExpr): R = defaultVisit(expr)
            override fun visit(expr: LengthExpr): R = defaultVisit(expr)
            override fun visit(expr: CastExpr): R = defaultVisit(expr)
            override fun visit(expr: PhiExpr): R = defaultVisit(expr)
            override fun visit(expr: UnaryOperation): R = defaultVisit(expr)
            override fun visit(expr: BinaryOperation): R = defaultVisit(expr)
            override fun visit(expr: RelationOperation): R = defaultVisit(expr)
            override fun visit(expr: InstanceCallExpr): R = defaultVisit(expr)
            override fun visit(expr: StaticCallExpr): R = defaultVisit(expr)

            fun defaultVisit(expr: Expr): R
        }
    }

    override fun <R> accept(visitor: Value.Visitor<R>): R {
        return accept(visitor as Visitor<R>)
    }

    fun <R> accept(visitor: Visitor<R>): R
}

data class NewExpr(
    override val type: Type, // ClassType
) : Expr {
    override fun toString(): String {
        return "new ${type.typeName}"
    }

    override fun <R> accept(visitor: Expr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class NewArrayExpr(
    val elementType: Type,
    val size: Value,
) : Expr {
    // TODO: support multi-dimensional arrays
    override val type: Type
        get() = ArrayType(elementType, 1)

    override fun toString(): String {
        return "new ${elementType.typeName}[$size]"
    }

    override fun <R> accept(visitor: Expr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class TypeOfExpr(
    val arg: Value,
) : Expr {
    override val type: Type
        get() = StringType

    override fun toString(): String {
        return "typeof $arg"
    }

    override fun <R> accept(visitor: Expr.Visitor<R>): R {
        return visitor.visit(this)
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

    override fun <R> accept(visitor: Expr.Visitor<R>): R {
        return visitor.visit(this)
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

    override fun <R> accept(visitor: Expr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class CastExpr(
    val arg: Value,
    override val type: Type,
) : Expr {
    override fun toString(): String {
        return "$arg as $type"
    }

    override fun <R> accept(visitor: Expr.Visitor<R>): R {
        return visitor.visit(this)
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
    val args: List<Value>,
    val argToBlock: Map<Value, BasicBlock>,
    override val type: Type,
) : Expr {
    override fun toString(): String {
        return "phi(${args.joinToString()})"
    }

    override fun <R> accept(visitor: Expr.Visitor<R>): R {
        return visitor.visit(this)
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

    override fun <R> accept(visitor: Expr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

interface BinaryExpr : Expr {
    val left: Value
    val right: Value

    override val type: Type
        get() = TypeInference.infer(this)
}

// TODO: AddExpr and many others
// data class AddExpr(
//     override val left: Value,
//     override val right: Value,
// ) : BinaryExpr {
//     override fun toString(): String {
//         return "$left + $right"
//     }
// }

data class BinaryOperation(
    val op: BinaryOp,
    override val left: Value,
    override val right: Value,
) : BinaryExpr {
    override fun toString(): String {
        return "$left $op $right"
    }

    override fun <R> accept(visitor: Expr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

// data class ConditionExpr(
//     val relop: String,
//     override val left: Value,
//     override val right: Value,
// ) : BinaryExpr {
//     override fun toString(): String {
//         return "$left $relop $right"
//     }
// }

interface ConditionExpr : BinaryExpr {
    override val type: Type
        get() = BooleanType
}

data class RelationOperation(
    val relop: String,
    override val left: Value,
    override val right: Value,
) : ConditionExpr {
    override fun toString(): String {
        return "$left $relop $right"
    }

    override fun <R> accept(visitor: Expr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

interface CallExpr : Expr {
    val method: MethodSignature
    val args: List<Value>

    override val type: Type
        get() = method.returnType
}

data class InstanceCallExpr(
    val instance: Local,
    override val method: MethodSignature,
    override val args: List<Value>,
) : CallExpr {
    override fun toString(): String {
        return "$instance.${method.name}(${args.joinToString()})"
    }

    override fun <R> accept(visitor: Expr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class StaticCallExpr(
    override val method: MethodSignature,
    override val args: List<Value>,
) : CallExpr {
    override fun toString(): String {
        return "${method.enclosingClass.name}.${method.name}(${args.joinToString()})"
    }

    override fun <R> accept(visitor: Expr.Visitor<R>): R {
        return visitor.visit(this)
    }
}
