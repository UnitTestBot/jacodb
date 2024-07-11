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

package org.jacodb.panda.dynamic.ets.base

import org.jacodb.api.common.cfg.CommonCallExpr
import org.jacodb.panda.dynamic.ets.graph.EtsBasicBlock
import org.jacodb.panda.dynamic.ets.model.EtsMethodSignature

interface EtsExpr : EtsEntity {
    interface Visitor<out R> {
        fun visit(expr: EtsNewExpr): R
        fun visit(expr: EtsNewArrayExpr): R
        fun visit(expr: EtsDeleteExpr): R
        fun visit(expr: EtsTypeOfExpr): R
        fun visit(expr: EtsInstanceOfExpr): R
        fun visit(expr: EtsLengthExpr): R
        fun visit(expr: EtsCastExpr): R
        fun visit(expr: EtsPhiExpr): R
        fun visit(expr: EtsUnaryOperation): R
        fun visit(expr: EtsBinaryOperation): R
        fun visit(expr: EtsRelationOperation): R
        fun visit(expr: EtsInstanceCallExpr): R
        fun visit(expr: EtsStaticCallExpr): R

        interface Default<out R> : Visitor<R> {
            override fun visit(expr: EtsNewExpr): R = defaultVisit(expr)
            override fun visit(expr: EtsNewArrayExpr): R = defaultVisit(expr)
            override fun visit(expr: EtsDeleteExpr): R = defaultVisit(expr)
            override fun visit(expr: EtsTypeOfExpr): R = defaultVisit(expr)
            override fun visit(expr: EtsInstanceOfExpr): R = defaultVisit(expr)
            override fun visit(expr: EtsLengthExpr): R = defaultVisit(expr)
            override fun visit(expr: EtsCastExpr): R = defaultVisit(expr)
            override fun visit(expr: EtsPhiExpr): R = defaultVisit(expr)
            override fun visit(expr: EtsUnaryOperation): R = defaultVisit(expr)
            override fun visit(expr: EtsBinaryOperation): R = defaultVisit(expr)
            override fun visit(expr: EtsRelationOperation): R = defaultVisit(expr)
            override fun visit(expr: EtsInstanceCallExpr): R = defaultVisit(expr)
            override fun visit(expr: EtsStaticCallExpr): R = defaultVisit(expr)

            fun defaultVisit(expr: EtsExpr): R
        }
    }

    override fun <R> accept(visitor: EtsEntity.Visitor<R>): R {
        return accept(visitor as Visitor<R>)
    }

    fun <R> accept(visitor: Visitor<R>): R
}

data class EtsNewExpr(
    override val type: EtsType,
) : EtsExpr {
    override fun toString(): String {
        return "new ${type.typeName}"
    }

    override fun <R> accept(visitor: EtsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class EtsDeleteExpr(
    val arg: EtsEntity,
) : EtsExpr {
    override val type: EtsType
        get() = EtsBooleanType

    override fun toString(): String {
        return "delete $arg"
    }

    override fun <R> accept(visitor: EtsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class EtsNewArrayExpr(
    val elementType: EtsType,
    val size: EtsEntity,
) : EtsExpr {
    // TODO: support multi-dimensional arrays
    override val type: EtsType
        get() = EtsArrayType(elementType, 1)

    override fun toString(): String {
        return "new ${elementType.typeName}[$size]"
    }

    override fun <R> accept(visitor: EtsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class EtsTypeOfExpr(
    val arg: EtsEntity,
) : EtsExpr {
    override val type: EtsType
        get() = EtsStringType

    override fun toString(): String {
        return "typeof $arg"
    }

    override fun <R> accept(visitor: EtsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class EtsInstanceOfExpr(
    val arg: EtsEntity,
    val checkType: EtsType,
) : EtsExpr {
    override val type: EtsType
        get() = EtsBooleanType

    override fun toString(): String {
        return "$arg instanceof $checkType"
    }

    override fun <R> accept(visitor: EtsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class EtsLengthExpr(
    val arg: EtsEntity,
) : EtsExpr {
    override val type: EtsType
        get() = EtsNumberType

    override fun toString(): String {
        return "$arg.length"
    }

    override fun <R> accept(visitor: EtsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class EtsCastExpr(
    val arg: EtsEntity,
    override val type: EtsType,
) : EtsExpr {
    override fun toString(): String {
        return "$arg as $type"
    }

    override fun <R> accept(visitor: EtsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class EtsPhiExpr(
    val args: List<EtsEntity>,
    val argToBlock: Map<EtsEntity, EtsBasicBlock>,
    override val type: EtsType,
) : EtsExpr {
    override fun toString(): String {
        return "phi(${args.joinToString()})"
    }

    override fun <R> accept(visitor: EtsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

interface EtsUnaryExpr : EtsExpr {
    val arg: EtsEntity

    override val type: EtsType
        get() = arg.type
}

data class EtsUnaryOperation(
    val op: UnaryOp,
    override val arg: EtsEntity,
) : EtsUnaryExpr {
    override fun toString(): String {
        return "$op$arg"
    }

    override fun <R> accept(visitor: EtsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

interface EtsBinaryExpr : EtsExpr {
    val left: EtsEntity
    val right: EtsEntity
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

data class EtsBinaryOperation(
    val op: BinaryOp,
    override val left: EtsEntity,
    override val right: EtsEntity,
) : EtsBinaryExpr {
    // TODO: either use a type inference mechanism or add a type field
    override val type: EtsType
        get() = EtsUnknownType

    override fun toString(): String {
        return "$left $op $right"
    }

    override fun <R> accept(visitor: EtsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

interface EtsConditionExpr : EtsBinaryExpr {
    override val type: EtsType
        get() = EtsBooleanType
}

data class EtsRelationOperation(
    val relop: String,
    override val left: EtsEntity,
    override val right: EtsEntity,
) : EtsConditionExpr {
    override fun toString(): String {
        return "$left $relop $right"
    }

    override fun <R> accept(visitor: EtsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

interface EtsCallExpr : EtsExpr, CommonCallExpr {
    val method: EtsMethodSignature
    override val args: List<EtsValue>

    override val type: EtsType
        get() = method.returnType
}

data class EtsInstanceCallExpr(
    val instance: EtsEntity,
    override val method: EtsMethodSignature,
    override val args: List<EtsValue>,
) : EtsCallExpr {
    override fun toString(): String {
        return "$instance.${method.name}(${args.joinToString()})"
    }

    override fun <R> accept(visitor: EtsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class EtsStaticCallExpr(
    override val method: EtsMethodSignature,
    override val args: List<EtsValue>,
) : EtsCallExpr {
    override fun toString(): String {
        return "${method.enclosingClass.name}.${method.name}(${args.joinToString()})"
    }

    override fun <R> accept(visitor: EtsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}
