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

package org.jacodb.panda.dynamic.api

import org.jacodb.api.core.cfg.CoreExpr
import org.jacodb.api.core.cfg.CoreExprVisitor
import org.jacodb.api.core.cfg.CoreValue


interface PandaExpr : CoreExpr<PandaType, PandaValue>, Mappable {

    fun <T> accept(visitor: PandaExprVisitor<T>): T

    override fun <T> accept(visitor: CoreExprVisitor<T>): T {
        TODO("Not yet implemented")
    }
}

interface PandaValue : PandaExpr, CoreValue<PandaValue, PandaType>

class PandaLocalVar(val id: Int) : PandaValue {

    override val type: PandaType = PandaAnyType()
    override val operands: List<PandaValue> = emptyList()

    override fun toString(): String = "%$id"

    override fun <T> accept(visitor: PandaExprVisitor<T>): T {
        return visitor.visitPandaLocalVar(this)
    }
}

/**
Mock Expr for WIP purposes.

Maps all unknown Panda IR instructions to this.
 */
class TODOExpr(
    val opcode: String,
    override val operands: List<PandaValue>
) : PandaExpr {

    override val type: PandaType = PandaAnyType()

    override fun <T> accept(visitor: PandaExprVisitor<T>): T {
        return visitor.visitTODOExpr(this)
    }

    override fun toString() = "$opcode(${operands.joinToString(separator = ", ")})"
}

interface PandaUnaryExpr : PandaExpr {
    val value: PandaValue
}

interface PandaBinaryExpr : PandaExpr {
    val lhv: PandaValue
    val rhv: PandaValue
}

interface PandaCallExpr : PandaExpr {

    val method: PandaMethod
    val args: List<PandaValue>

    override val type get() = method.returnType

    override val operands: List<PandaValue>
        get() = args
}

interface PandaConditionExpr : PandaBinaryExpr

class PandaArgument(val id: Int) : PandaValue {

    override val type: PandaType = PandaAnyType()

    override val operands: List<PandaValue> = emptyList()

    override fun <T> accept(visitor: PandaExprVisitor<T>): T {
        return visitor.visitPandaArgument(this)
    }


    override fun toString() = "arg $id"
}

interface PandaConstant : PandaValue

enum class PandaCmpOp {
    EQ, NE, LT, LE, GT, GE
    // TODO: expand
}

class PandaCmpExpr(
    val cmpOp: PandaCmpOp,
    override val lhv: PandaValue,
    override val rhv: PandaValue
) : PandaBinaryExpr {

    override val type: PandaType = PandaBoolType()
    override val operands: List<PandaValue> = listOf(lhv, rhv)

    override fun toString(): String = "$lhv ${cmpOp.name} $rhv"

    override fun <T> accept(visitor: PandaExprVisitor<T>): T {
        return visitor.visitPandaCmpExpr(this)
    }
}

class PandaStringConstant(val value: String) : PandaConstant {
    override val type: PandaType = PandaAnyType()
    override val operands: List<PandaValue> = emptyList()

    override fun <T> accept(visitor: PandaExprVisitor<T>): T {
        return visitor.visitPandaStringConstant(this)
    }

    override fun toString() = "\"$value\""
}

class TODOConstant(val value: String?) : PandaConstant {
    override val type: PandaType = PandaAnyType()
    override val operands: List<PandaValue> = emptyList()

    override fun <T> accept(visitor: PandaExprVisitor<T>): T {
        return visitor.visitPandaTODOConstant(this)
    }

    override fun toString() = value?.let { "\"$it\"" } ?: "null"
}

class PandaNumberConstant(val value: Int) : PandaConstant {

    override val type: PandaType = PandaNumberType()
    override val operands: List<PandaValue> = emptyList()

    override fun <T> accept(visitor: PandaExprVisitor<T>): T {
        return visitor.visitPandaNumberConstant(this)
    }

    override fun toString() = value.toString()
}

object PandaUndefinedConstant : PandaConstant {
    override fun <T> accept(visitor: PandaExprVisitor<T>): T {
        return visitor.visitPandaUndefinedConstant(this)
    }

    override val type: PandaType = PandaUndefinedType()
    override val operands: List<PandaValue>
        get() = emptyList()

}

class PandaCastExpr(override val type: PandaType, operand: PandaValue) : PandaExpr {

    override val operands: List<PandaValue> = listOf(operand)

    override fun <T> accept(visitor: PandaExprVisitor<T>): T {
        return visitor.visitPandaCastExpr(this)
    }

    override fun toString() = "($type) $operands"
}

class PandaNeqExpr(
    override val lhv: PandaValue,
    override val rhv: PandaValue
) : PandaConditionExpr {

    override val type: PandaType = PandaAnyType()
    override val operands: List<PandaValue> = listOf(lhv, rhv)

    override fun <T> accept(visitor: PandaExprVisitor<T>): T {
        return visitor.visitPandaNeqExpr(this)
    }

    override fun toString(): String = "$lhv != $rhv"
}

class PandaEqExpr(
    override val lhv: PandaValue,
    override val rhv: PandaValue
) : PandaConditionExpr {

    override val type: PandaType = PandaAnyType()
    override val operands: List<PandaValue> = listOf(lhv, rhv)

    override fun <T> accept(visitor: PandaExprVisitor<T>): T {
        return visitor.visitPandaEqExpr(this)
    }

    override fun toString(): String = "$lhv == $rhv"
}

class PandaLtExpr(
    override val lhv: PandaValue,
    override val rhv: PandaValue
) : PandaConditionExpr {

    override val type: PandaType = PandaAnyType()
    override val operands: List<PandaValue> = listOf(lhv, rhv)

    override fun <T> accept(visitor: PandaExprVisitor<T>): T {
        return visitor.visitPandaLtExpr(this)
    }

    override fun toString(): String = "$lhv < $rhv"
}

class PandaLeExpr(
    override val lhv: PandaValue,
    override val rhv: PandaValue
) : PandaConditionExpr {

    override val type: PandaType = PandaAnyType()
    override val operands: List<PandaValue> = listOf(lhv, rhv)

    override fun <T> accept(visitor: PandaExprVisitor<T>): T {
        return visitor.visitPandaLeExpr(this)
    }

    override fun toString(): String = "$lhv <= $rhv"
}

class PandaGtExpr(
    override val lhv: PandaValue,
    override val rhv: PandaValue
) : PandaConditionExpr {

    override val type: PandaType = PandaAnyType()
    override val operands: List<PandaValue> = listOf(lhv, rhv)

    override fun <T> accept(visitor: PandaExprVisitor<T>): T {
        return visitor.visitPandaGtExpr(this)
    }

    override fun toString(): String = "$lhv > $rhv"
}

class PandaGeExpr(
    override val lhv: PandaValue,
    override val rhv: PandaValue
) : PandaConditionExpr {

    override val type: PandaType = PandaAnyType()
    override val operands: List<PandaValue> = listOf(lhv, rhv)

    override fun <T> accept(visitor: PandaExprVisitor<T>): T {
        return visitor.visitPandaGeExpr(this)
    }

    override fun toString(): String = "$lhv >= $rhv"
}

class PandaStrictEqExpr(
    override val lhv: PandaValue,
    override val rhv: PandaValue
) : PandaConditionExpr {

    override val type: PandaType = PandaAnyType()
    override val operands: List<PandaValue> = listOf(lhv, rhv)

    override fun <T> accept(visitor: PandaExprVisitor<T>): T {
        return visitor.visitPandaStrictEqExpr(this)
    }

    override fun toString(): String = "$lhv >= $rhv"
}

class PandaNewExpr(
    val clazz: PandaValue,
    val params: List<PandaValue>
) : PandaExpr {

    override val type: PandaType = PandaAnyType()
    override val operands: List<PandaValue> = listOf(clazz, *params.toTypedArray())

    override fun <T> accept(visitor: PandaExprVisitor<T>): T {
        return visitor.visitPandaNewExpr(this)
    }

    override fun toString() = "new $clazz(${params.joinToString(separator = ", ")})"
}

class PandaThrowInst(
    override val location: PandaInstLocation,
    val throwable: PandaValue
) : PandaInst, PandaTerminatingInst {

    override val operands: List<PandaExpr> = listOf(throwable)

    override fun <T> accept(visitor: PandaInstVisitor<T>): T {
        return visitor.visitPandaThrowInst(this)
    }

    override fun toString(): String = "throw $throwable"
}

class PandaAddExpr(
    override val lhv: PandaValue,
    override val rhv: PandaValue
) : PandaBinaryExpr {

    override val type: PandaType = PandaAnyType()
    override val operands: List<PandaValue> = listOf(lhv, rhv)

    override fun <T> accept(visitor: PandaExprVisitor<T>): T {
        return visitor.visitPandaAddExpr(this)
    }

    override fun toString(): String = "$lhv + $rhv"
}

class PandaVirtualCallExpr(
    private val lazyMethod: Lazy<PandaMethod>,
    override val args: List<PandaValue>
) : PandaCallExpr {

    override val method: PandaMethod get() = lazyMethod.value

    override fun <T> accept(visitor: PandaExprVisitor<T>): T {
        return visitor.visitPandaVirtualCallExpr(this)
    }

}


class PandaTypeofExpr(override val value: PandaValue) : PandaUnaryExpr {

    override val type: PandaType = PandaAnyType()
    override val operands: List<PandaValue> = listOf(value)

    override fun <T> accept(visitor: PandaExprVisitor<T>): T {
        return visitor.visitPandaTypeofExpr(this)
    }

    override fun toString() = "typeof $value"
}

class PandaToNumericExpr(override val value: PandaValue) : PandaUnaryExpr {

    override val type: PandaType = PandaNumberType()
    override val operands: List<PandaValue> = listOf(value)

    override fun <T> accept(visitor: PandaExprVisitor<T>): T {
        return visitor.visitPandaToNumericExpr(this)
    }

    override fun toString() = "typeof $value"
}
