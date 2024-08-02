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

package org.jacodb.ets.base

import org.jacodb.api.common.cfg.CommonCallExpr
import org.jacodb.ets.model.EtsMethodSignature

interface EtsExpr : EtsEntity {
    interface Visitor<out R> {
        fun visit(expr: EtsNewExpr): R
        fun visit(expr: EtsNewArrayExpr): R
        fun visit(expr: EtsLengthExpr): R
        fun visit(expr: EtsCastExpr): R
        fun visit(expr: EtsInstanceOfExpr): R

        // Unary
        fun visit(expr: EtsDeleteExpr): R
        fun visit(expr: EtsTypeOfExpr): R
        fun visit(expr: EtsVoidExpr): R
        fun visit(expr: EtsNotExpr): R
        fun visit(expr: EtsNegExpr): R
        fun visit(expr: EtsUnaryPlusExpr): R
        fun visit(expr: EtsPreIncExpr): R
        fun visit(expr: EtsPreDecExpr): R
        fun visit(expr: EtsPostIncExpr): R
        fun visit(expr: EtsPostDecExpr): R

        // Relation
        fun visit(expr: EtsEqExpr): R
        fun visit(expr: EtsNotEqExpr): R
        fun visit(expr: EtsStrictEqExpr): R
        fun visit(expr: EtsStrictNotEqExpr): R
        fun visit(expr: EtsLtExpr): R
        fun visit(expr: EtsLtEqExpr): R
        fun visit(expr: EtsGtExpr): R
        fun visit(expr: EtsGtEqExpr): R
        fun visit(expr: EtsInExpr): R

        // Arithmetic
        fun visit(expr: EtsAddExpr): R
        fun visit(expr: EtsSubExpr): R
        fun visit(expr: EtsMulExpr): R
        fun visit(expr: EtsDivExpr): R
        fun visit(expr: EtsRemExpr): R
        fun visit(expr: EtsExpExpr): R

        // Bitwise
        fun visit(expr: EtsBitAndExpr): R
        fun visit(expr: EtsBitOrExpr): R
        fun visit(expr: EtsBitXorExpr): R
        fun visit(expr: EtsLeftShiftExpr): R
        fun visit(expr: EtsRightShiftExpr): R
        fun visit(expr: EtsUnsignedRightShiftExpr): R

        // Logical
        fun visit(expr: EtsAndExpr): R
        fun visit(expr: EtsOrExpr): R
        fun visit(expr: EtsNullishCoalescingExpr): R

        // Call
        fun visit(expr: EtsInstanceCallExpr): R
        fun visit(expr: EtsStaticCallExpr): R

        // Other
        fun visit(expr: EtsCommaExpr): R
        fun visit(expr: EtsTernaryExpr): R

        interface Default<out R> : Visitor<R> {
            override fun visit(expr: EtsNewExpr): R = defaultVisit(expr)
            override fun visit(expr: EtsNewArrayExpr): R = defaultVisit(expr)
            override fun visit(expr: EtsLengthExpr): R = defaultVisit(expr)
            override fun visit(expr: EtsCastExpr): R = defaultVisit(expr)
            override fun visit(expr: EtsInstanceOfExpr): R = defaultVisit(expr)

            override fun visit(expr: EtsDeleteExpr): R = defaultVisit(expr)
            override fun visit(expr: EtsTypeOfExpr): R = defaultVisit(expr)
            override fun visit(expr: EtsVoidExpr): R = defaultVisit(expr)
            override fun visit(expr: EtsNotExpr): R = defaultVisit(expr)
            override fun visit(expr: EtsNegExpr): R = defaultVisit(expr)
            override fun visit(expr: EtsUnaryPlusExpr): R = defaultVisit(expr)
            override fun visit(expr: EtsPreIncExpr): R = defaultVisit(expr)
            override fun visit(expr: EtsPreDecExpr): R = defaultVisit(expr)
            override fun visit(expr: EtsPostIncExpr): R = defaultVisit(expr)
            override fun visit(expr: EtsPostDecExpr): R = defaultVisit(expr)

            override fun visit(expr: EtsEqExpr): R = defaultVisit(expr)
            override fun visit(expr: EtsNotEqExpr): R = defaultVisit(expr)
            override fun visit(expr: EtsStrictEqExpr): R = defaultVisit(expr)
            override fun visit(expr: EtsStrictNotEqExpr): R = defaultVisit(expr)
            override fun visit(expr: EtsLtExpr): R = defaultVisit(expr)
            override fun visit(expr: EtsLtEqExpr): R = defaultVisit(expr)
            override fun visit(expr: EtsGtExpr): R = defaultVisit(expr)
            override fun visit(expr: EtsGtEqExpr): R = defaultVisit(expr)
            override fun visit(expr: EtsInExpr): R = defaultVisit(expr)

            override fun visit(expr: EtsAddExpr): R = defaultVisit(expr)
            override fun visit(expr: EtsSubExpr): R = defaultVisit(expr)
            override fun visit(expr: EtsMulExpr): R = defaultVisit(expr)
            override fun visit(expr: EtsDivExpr): R = defaultVisit(expr)
            override fun visit(expr: EtsRemExpr): R = defaultVisit(expr)
            override fun visit(expr: EtsExpExpr): R = defaultVisit(expr)

            override fun visit(expr: EtsBitAndExpr): R = defaultVisit(expr)
            override fun visit(expr: EtsBitOrExpr): R = defaultVisit(expr)
            override fun visit(expr: EtsBitXorExpr): R = defaultVisit(expr)
            override fun visit(expr: EtsLeftShiftExpr): R = defaultVisit(expr)
            override fun visit(expr: EtsRightShiftExpr): R = defaultVisit(expr)
            override fun visit(expr: EtsUnsignedRightShiftExpr): R = defaultVisit(expr)

            override fun visit(expr: EtsAndExpr): R = defaultVisit(expr)
            override fun visit(expr: EtsOrExpr): R = defaultVisit(expr)
            override fun visit(expr: EtsNullishCoalescingExpr): R = defaultVisit(expr)

            override fun visit(expr: EtsInstanceCallExpr): R = defaultVisit(expr)
            override fun visit(expr: EtsStaticCallExpr): R = defaultVisit(expr)

            override fun visit(expr: EtsCommaExpr): R = defaultVisit(expr)
            override fun visit(expr: EtsTernaryExpr): R = defaultVisit(expr)

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

data class EtsNewArrayExpr(
    val elementType: EtsType,
    val size: EtsEntity,
) : EtsExpr {
    override val type: EtsType
        get() = EtsArrayType(elementType, 1)

    override fun toString(): String {
        return "new Array<${elementType.typeName}>($size)"
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
        return "${arg}.length"
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

data class EtsInstanceOfExpr(
    val arg: EtsEntity,
    val checkType: String, // TODO: what should it be?
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

interface EtsUnaryExpr : EtsExpr {
    val arg: EtsEntity
}

data class EtsDeleteExpr(
    override val arg: EtsEntity,
) : EtsUnaryExpr {
    override val type: EtsType
        get() = EtsBooleanType

    override fun toString(): String {
        return "delete $arg"
    }

    override fun <R> accept(visitor: EtsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class EtsTypeOfExpr(
    override val arg: EtsEntity,
) : EtsUnaryExpr {
    override val type: EtsType
        get() = EtsStringType

    override fun toString(): String {
        return "typeof $arg"
    }

    override fun <R> accept(visitor: EtsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class EtsVoidExpr(
    override val arg: EtsEntity,
) : EtsUnaryExpr {
    override val type: EtsType
        get() = EtsUndefinedType

    override fun toString(): String {
        return "void $arg"
    }

    override fun <R> accept(visitor: EtsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class EtsNotExpr(
    override val arg: EtsEntity,
) : EtsUnaryExpr {
    override val type: EtsType
        get() = EtsBooleanType

    override fun toString(): String {
        return "!$arg"
    }

    override fun <R> accept(visitor: EtsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class EtsNegExpr(
    override val arg: EtsEntity,
    override val type: EtsType,
) : EtsUnaryExpr {
    override fun toString(): String {
        return "-$arg"
    }

    override fun <R> accept(visitor: EtsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class EtsUnaryPlusExpr(
    override val arg: EtsEntity,
) : EtsUnaryExpr {
    override val type: EtsType
        get() = EtsNumberType

    override fun toString(): String {
        return "+$arg"
    }

    override fun <R> accept(visitor: EtsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class EtsPreIncExpr(
    override val type: EtsType,
    override val arg: EtsEntity,
) : EtsUnaryExpr {
    override fun toString(): String {
        return "++$arg"
    }

    override fun <R> accept(visitor: EtsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class EtsPreDecExpr(
    override val type: EtsType,
    override val arg: EtsEntity,
) : EtsUnaryExpr {
    override fun toString(): String {
        return "--$arg"
    }

    override fun <R> accept(visitor: EtsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class EtsPostIncExpr(
    override val type: EtsType,
    override val arg: EtsEntity,
) : EtsUnaryExpr {
    override fun toString(): String {
        return "$arg++"
    }

    override fun <R> accept(visitor: EtsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class EtsPostDecExpr(
    override val type: EtsType,
    override val arg: EtsEntity,
) : EtsUnaryExpr {
    override fun toString(): String {
        return "$arg--"
    }

    override fun <R> accept(visitor: EtsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

interface EtsBinaryExpr : EtsExpr {
    val left: EtsEntity
    val right: EtsEntity
}

interface EtsRelationExpr : EtsBinaryExpr {
    override val type: EtsType
        get() = EtsBooleanType
}

data class EtsEqExpr(
    override val left: EtsEntity,
    override val right: EtsEntity,
) : EtsRelationExpr {
    override fun toString(): String {
        return "$left == $right"
    }

    override fun <R> accept(visitor: EtsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class EtsNotEqExpr(
    override val left: EtsEntity,
    override val right: EtsEntity,
) : EtsRelationExpr {
    override fun toString(): String {
        return "$left != $right"
    }

    override fun <R> accept(visitor: EtsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class EtsStrictEqExpr(
    override val left: EtsEntity,
    override val right: EtsEntity,
) : EtsRelationExpr {
    override fun toString(): String {
        return "$left === $right"
    }

    override fun <R> accept(visitor: EtsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class EtsStrictNotEqExpr(
    override val left: EtsEntity,
    override val right: EtsEntity,
) : EtsRelationExpr {
    override fun toString(): String {
        return "$left !== $right"
    }

    override fun <R> accept(visitor: EtsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class EtsLtExpr(
    override val left: EtsEntity,
    override val right: EtsEntity,
) : EtsRelationExpr {
    override fun toString(): String {
        return "$left < $right"
    }

    override fun <R> accept(visitor: EtsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class EtsLtEqExpr(
    override val left: EtsEntity,
    override val right: EtsEntity,
) : EtsRelationExpr {
    override fun toString(): String {
        return "$left <= $right"
    }

    override fun <R> accept(visitor: EtsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class EtsGtExpr(
    override val left: EtsEntity,
    override val right: EtsEntity,
) : EtsRelationExpr {
    override fun toString(): String {
        return "$left > $right"
    }

    override fun <R> accept(visitor: EtsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class EtsGtEqExpr(
    override val left: EtsEntity,
    override val right: EtsEntity,
) : EtsRelationExpr {
    override fun toString(): String {
        return "$left >= $right"
    }

    override fun <R> accept(visitor: EtsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class EtsInExpr(
    override val left: EtsEntity,
    override val right: EtsEntity,
) : EtsRelationExpr {
    override val type: EtsType
        get() = EtsBooleanType

    override fun toString(): String {
        return "$left in $right"
    }

    override fun <R> accept(visitor: EtsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

interface EtsArithmeticExpr : EtsBinaryExpr

data class EtsAddExpr(
    override val type: EtsType,
    override val left: EtsEntity,
    override val right: EtsEntity,
) : EtsArithmeticExpr {
    override fun toString(): String {
        return "$left + $right"
    }

    override fun <R> accept(visitor: EtsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class EtsSubExpr(
    override val type: EtsType,
    override val left: EtsEntity,
    override val right: EtsEntity,
) : EtsArithmeticExpr {
    override fun toString(): String {
        return "$left - $right"
    }

    override fun <R> accept(visitor: EtsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class EtsMulExpr(
    override val type: EtsType,
    override val left: EtsEntity,
    override val right: EtsEntity,
) : EtsArithmeticExpr {
    override fun toString(): String {
        return "$left * $right"
    }

    override fun <R> accept(visitor: EtsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class EtsDivExpr(
    override val type: EtsType, // EtsNumberType
    override val left: EtsEntity,
    override val right: EtsEntity,
) : EtsArithmeticExpr {
    override fun toString(): String {
        return "$left / $right"
    }

    override fun <R> accept(visitor: EtsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class EtsRemExpr(
    override val type: EtsType,
    override val left: EtsEntity,
    override val right: EtsEntity,
) : EtsArithmeticExpr {
    override fun toString(): String {
        return "$left % $right"
    }

    override fun <R> accept(visitor: EtsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class EtsExpExpr(
    override val type: EtsType,
    override val left: EtsEntity,
    override val right: EtsEntity,
) : EtsArithmeticExpr {
    override fun toString(): String {
        return "$left ** $right"
    }

    override fun <R> accept(visitor: EtsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

interface EtsBitwiseExpr : EtsBinaryExpr

data class EtsBitAndExpr(
    override val type: EtsType,
    override val left: EtsEntity,
    override val right: EtsEntity,
) : EtsBitwiseExpr {
    override fun toString(): String {
        return "$left & $right"
    }

    override fun <R> accept(visitor: EtsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class EtsBitOrExpr(
    override val type: EtsType,
    override val left: EtsEntity,
    override val right: EtsEntity,
) : EtsBitwiseExpr {
    override fun toString(): String {
        return "$left | $right"
    }

    override fun <R> accept(visitor: EtsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class EtsBitXorExpr(
    override val type: EtsType,
    override val left: EtsEntity,
    override val right: EtsEntity,
) : EtsBitwiseExpr {
    override fun toString(): String {
        return "$left ^ $right"
    }

    override fun <R> accept(visitor: EtsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class EtsLeftShiftExpr(
    override val type: EtsType,
    override val left: EtsEntity,
    override val right: EtsEntity,
) : EtsBitwiseExpr {
    override fun toString(): String {
        return "$left << $right"
    }

    override fun <R> accept(visitor: EtsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

// Sign-propagating right shift
data class EtsRightShiftExpr(
    override val type: EtsType,
    override val left: EtsEntity,
    override val right: EtsEntity,
) : EtsBitwiseExpr {
    override fun toString(): String {
        return "$left >> $right"
    }

    override fun <R> accept(visitor: EtsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

// Zero-fill right shift
data class EtsUnsignedRightShiftExpr(
    override val type: EtsType,
    override val left: EtsEntity,
    override val right: EtsEntity,
) : EtsBitwiseExpr {
    override fun toString(): String {
        return "$left >>> $right"
    }

    override fun <R> accept(visitor: EtsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

interface EtsLogicalExpr : EtsBinaryExpr

data class EtsAndExpr(
    override val type: EtsType,
    override val left: EtsEntity,
    override val right: EtsEntity,
) : EtsLogicalExpr {
    override fun toString(): String {
        return "$left && $right"
    }

    override fun <R> accept(visitor: EtsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class EtsOrExpr(
    override val type: EtsType,
    override val left: EtsEntity,
    override val right: EtsEntity,
) : EtsLogicalExpr {
    override fun toString(): String {
        return "$left || $right"
    }

    override fun <R> accept(visitor: EtsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class EtsNullishCoalescingExpr(
    override val type: EtsType,
    override val left: EtsEntity,
    override val right: EtsEntity,
) : EtsLogicalExpr {
    override fun toString(): String {
        return "$left ?? $right"
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

data class EtsCommaExpr(
    override val left: EtsEntity,
    override val right: EtsEntity,
) : EtsBinaryExpr {
    override val type: EtsType
        get() = right.type

    override fun toString(): String {
        return "$left, $right"
    }

    override fun <R> accept(visitor: EtsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class EtsTernaryExpr(
    override val type: EtsType,
    val condition: EtsEntity,
    val thenExpr: EtsEntity,
    val elseExpr: EtsEntity,
) : EtsExpr {
    override fun toString(): String {
        return "$condition ? $thenExpr : $elseExpr"
    }

    override fun <R> accept(visitor: EtsExpr.Visitor<R>): R {
        return visitor.visit(this)
    }
}
