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

import org.jacodb.api.common.cfg.CommonCallExpr
import org.jacodb.api.common.cfg.CommonExpr
import org.jacodb.api.common.cfg.CommonValue
import org.jacodb.api.common.ext.name

interface PandaExpr : CommonExpr, Mappable {
    override val type: PandaType
    override val operands: List<PandaValue>
        get() = TODO("Not yet implemented")

    fun <T> accept(visitor: PandaExprVisitor<T>): T

    override fun <T> accept(visitor: CommonExpr.Visitor<T>): T {
        TODO("Not yet implemented")
    }
}

interface PandaValue : PandaExpr, CommonValue

class PandaLocalVar(val id: Int) : PandaValue {
    override val type: PandaType = PandaAnyType
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
    override val operands: List<PandaValue>,
) : PandaExpr {
    override val type: PandaType = PandaAnyType

    override fun toString(): String = "$opcode(${operands.joinToString(separator = ", ")})"

    override fun <T> accept(visitor: PandaExprVisitor<T>): T {
        return visitor.visitTODOExpr(this)
    }
}

interface PandaUnaryExpr : PandaExpr {
    val value: PandaValue
}

interface PandaBinaryExpr : PandaExpr {
    val lhv: PandaValue
    val rhv: PandaValue
}

interface PandaCallExpr : PandaExpr, CommonCallExpr {
    override val method: PandaTypedMethod
    override val args: List<PandaValue>

    override val type: PandaType
        get() = method.returnType

    override val operands: List<PandaValue>
        get() = args
}

interface PandaConditionExpr : PandaBinaryExpr

class PandaArgument(val id: Int) : PandaValue {
    override val type: PandaType
        get() = PandaAnyType

    override val operands: List<PandaValue>
        get() = emptyList()

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
    override val rhv: PandaValue,
) : PandaBinaryExpr {
    override val type: PandaType
        get() = PandaBoolType

    override val operands: List<PandaValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv ${cmpOp.name} $rhv"

    override fun <T> accept(visitor: PandaExprVisitor<T>): T {
        return visitor.visitPandaCmpExpr(this)
    }
}

class PandaStringConstant(val value: String) : PandaConstant {
    override val type: PandaType
        get() = PandaAnyType
    override val operands: List<PandaValue>
        get() = emptyList()

    override fun toString() = "\"$value\""

    override fun <T> accept(visitor: PandaExprVisitor<T>): T {
        return visitor.visitPandaStringConstant(this)
    }
}

class TODOConstant(val value: String?) : PandaConstant {
    override val type: PandaType
        get() = PandaAnyType

    override val operands: List<PandaValue>
        get() = emptyList()

    override fun toString() = value?.let { "\"$it\"" } ?: "null"

    override fun <T> accept(visitor: PandaExprVisitor<T>): T {
        return visitor.visitPandaTODOConstant(this)
    }
}

class PandaNumberConstant(val value: Int) : PandaConstant {
    override val type: PandaType
        get() = PandaNumberType

    override val operands: List<PandaValue>
        get() = emptyList()

    override fun toString() = value.toString()

    override fun <T> accept(visitor: PandaExprVisitor<T>): T {
        return visitor.visitPandaNumberConstant(this)
    }
}

object PandaUndefinedConstant : PandaConstant {
    override val type: PandaType
        get() = PandaUndefinedType

    override val operands: List<PandaValue>
        get() = emptyList()

    override fun toString(): String = "undefined"

    override fun <T> accept(visitor: PandaExprVisitor<T>): T {
        return visitor.visitPandaUndefinedConstant(this)
    }
}

class PandaCastExpr(
    override val type: PandaType,
    val operand: PandaValue,
) : PandaExpr {
    override val operands: List<PandaValue>
        get() = listOf(operand)

    override fun toString() = "($type) $operands"

    override fun <T> accept(visitor: PandaExprVisitor<T>): T {
        return visitor.visitPandaCastExpr(this)
    }
}

class PandaNeqExpr(
    override val lhv: PandaValue,
    override val rhv: PandaValue,
) : PandaConditionExpr {
    override val type: PandaType
        get() = PandaAnyType

    override val operands: List<PandaValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv != $rhv"

    override fun <T> accept(visitor: PandaExprVisitor<T>): T {
        return visitor.visitPandaNeqExpr(this)
    }
}

class PandaEqExpr(
    override val lhv: PandaValue,
    override val rhv: PandaValue,
) : PandaConditionExpr {
    override val type: PandaType
        get() = PandaAnyType

    override val operands: List<PandaValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv == $rhv"

    override fun <T> accept(visitor: PandaExprVisitor<T>): T {
        return visitor.visitPandaEqExpr(this)
    }
}

class PandaLtExpr(
    override val lhv: PandaValue,
    override val rhv: PandaValue,
) : PandaConditionExpr {
    override val type: PandaType
        get() = PandaAnyType

    override val operands: List<PandaValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv < $rhv"

    override fun <T> accept(visitor: PandaExprVisitor<T>): T {
        return visitor.visitPandaLtExpr(this)
    }
}

class PandaLeExpr(
    override val lhv: PandaValue,
    override val rhv: PandaValue,
) : PandaConditionExpr {
    override val type: PandaType
        get() = PandaAnyType

    override val operands: List<PandaValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv <= $rhv"

    override fun <T> accept(visitor: PandaExprVisitor<T>): T {
        return visitor.visitPandaLeExpr(this)
    }
}

class PandaGtExpr(
    override val lhv: PandaValue,
    override val rhv: PandaValue,
) : PandaConditionExpr {
    override val type: PandaType
        get() = PandaAnyType

    override val operands: List<PandaValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv > $rhv"

    override fun <T> accept(visitor: PandaExprVisitor<T>): T {
        return visitor.visitPandaGtExpr(this)
    }
}

class PandaGeExpr(
    override val lhv: PandaValue,
    override val rhv: PandaValue,
) : PandaConditionExpr {
    override val type: PandaType
        get() = PandaAnyType

    override val operands: List<PandaValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv >= $rhv"

    override fun <T> accept(visitor: PandaExprVisitor<T>): T {
        return visitor.visitPandaGeExpr(this)
    }
}

class PandaStrictEqExpr(
    override val lhv: PandaValue,
    override val rhv: PandaValue,
) : PandaConditionExpr {
    override val type: PandaType
        get() = PandaAnyType

    override val operands: List<PandaValue>
        get() = listOf(lhv, rhv)

    // TODO: why '>=' ?
    override fun toString(): String = "$lhv >= $rhv"

    override fun <T> accept(visitor: PandaExprVisitor<T>): T {
        return visitor.visitPandaStrictEqExpr(this)
    }
}

class PandaNewExpr(
    val clazz: PandaValue,
    val params: List<PandaValue>,
) : PandaExpr {
    override val type: PandaType
        get() = PandaAnyType

    override val operands: List<PandaValue>
        get() = listOf(clazz, *params.toTypedArray())

    override fun toString() = "new $clazz(${params.joinToString(separator = ", ")})"

    override fun <T> accept(visitor: PandaExprVisitor<T>): T {
        return visitor.visitPandaNewExpr(this)
    }
}

class PandaThrowInst(
    override val location: PandaInstLocation,
    val throwable: PandaValue,
) : PandaTerminatingInst {
    override val operands: List<PandaExpr>
        get() = listOf(throwable)

    override fun toString(): String = "throw $throwable"

    override fun <T> accept(visitor: PandaInstVisitor<T>): T {
        return visitor.visitPandaThrowInst(this)
    }
}

class PandaAddExpr(
    override val lhv: PandaValue,
    override val rhv: PandaValue,
) : PandaBinaryExpr {
    override val type: PandaType
        get() = PandaAnyType

    override val operands: List<PandaValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv + $rhv"

    override fun <T> accept(visitor: PandaExprVisitor<T>): T {
        return visitor.visitPandaAddExpr(this)
    }
}

class PandaVirtualCallExpr(
    private val lazyMethod: Lazy<PandaMethod>,
    override val args: List<PandaValue>,
    val instance: PandaValue? = null,
) : PandaCallExpr {

    override val method: PandaTypedMethod
        get() = TODO("lazyMethod.value")

    override fun <T> accept(visitor: PandaExprVisitor<T>): T {
        return visitor.visitPandaVirtualCallExpr(this)
    }

    override fun toString(): String = buildString {
        if (instance != null) append("$instance.")
        append(method.name + "(${args.joinToString(separator = ", ") { it.toString() }})")
    }
}

class PandaTypeofExpr(
    override val value: PandaValue,
) : PandaUnaryExpr {
    override val type: PandaType = PandaAnyType
    override val operands: List<PandaValue> = listOf(value)

    override fun <T> accept(visitor: PandaExprVisitor<T>): T {
        return visitor.visitPandaTypeofExpr(this)
    }

    override fun toString() = "typeof $value"
}

class PandaToNumericExpr(
    override val value: PandaValue,
) : PandaUnaryExpr {
    override val type: PandaType = PandaNumberType
    override val operands: List<PandaValue> = listOf(value)

    override fun <T> accept(visitor: PandaExprVisitor<T>): T {
        return visitor.visitPandaToNumericExpr(this)
    }

    override fun toString() = "typeof $value"
}
