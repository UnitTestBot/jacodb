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

package org.jacodb.panda.dynamic

import org.jacodb.api.core.cfg.*

interface Mappable

class PandaInstLocation(
    override val method: PandaMethod,
    override val index: Int,
    override val lineNumber: Int
) : CoreInstLocation<PandaMethod> {
    // TODO: expand like JcInstLocation

    override fun toString() = "method.$index"
}

data class PandaInstRef(
    val index: Int
)

interface PandaInst : CoreInst<PandaInstLocation, PandaMethod, PandaExpr>, Mappable {

    override val location: PandaInstLocation
    override val operands: List<PandaExpr>
    override val lineNumber: Int
        get() = location.lineNumber

    override fun <T> accept(visitor: InstVisitor<T>): T {
        TODO("Not yet implemented")
    }

    fun <T> accept(visitor: PandaInstVisitor<T>): T
}

interface PandaUnaryExpr : PandaExpr {
    val value: PandaValue
}

interface PandaBinaryExpr : PandaExpr {
    val lhv: PandaValue
    val rhv: PandaValue
}

interface PandaCallExpr : PandaExpr {

    // TODO: WIP version

    override val type get() = PandaAnyType()

    override val operands: List<PandaValue>
        get() = emptyList()
}

interface PandaConditionExpr : PandaBinaryExpr

/**
Mock Inst for WIP purposes.

Maps all unknown Panda IR instructions to this.
 */
class TODOInst(
    val opcode: String,
    override val location: PandaInstLocation,
    override val operands: List<PandaExpr>
) : PandaInst {

    override fun <T> accept(visitor: PandaInstVisitor<T>): T {
        return visitor.visitTODOInst(this)
    }

    override fun toString() = "$opcode(${operands.joinToString(separator = ", ")})"
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

class PandaArgument(override val operands: List<PandaValue>) : PandaValue {

    override val type: PandaType = PandaAnyType()

    override fun <T> accept(visitor: PandaExprVisitor<T>): T {
        return visitor.visitPandaArgument(this)
    }

    override fun toString() = "PandaArgument(${operands.joinToString(separator = ", ")})"
}

interface PandaConstant : PandaValue

enum class PandaCmpOp(val str: String) {
    EQ("EQ"),
    NE("NE")

    // TODO: expand
}

class PandaCmpExpr(
    val cmpOp: PandaCmpOp,
    override val lhv: PandaValue,
    override val rhv: PandaValue
) : PandaBinaryExpr {

    override val type: PandaType = PandaBoolType()
    override val operands: List<PandaValue> = listOf(lhv, rhv)

    override fun toString(): String = "$lhv $cmpOp $rhv"

    override fun <T> accept(visitor: PandaExprVisitor<T>): T {
        return visitor.visitPandaCmpExpr(this)
    }
}

interface PandaBranchingInst : PandaInst {
    val successors: List<PandaInstRef>
}

class PandaIfInst(
    override val location: PandaInstLocation,
    val condition: PandaConditionExpr,
    private val _trueBranch: Lazy<PandaInstRef>,
    private val _falseBranch: Lazy<PandaInstRef>
) : CoreIfInst<PandaInstLocation, PandaMethod, PandaExpr>, PandaInst, PandaBranchingInst {

    val trueBranch: PandaInstRef
        get() = _trueBranch.value

    val falseBranch: PandaInstRef
        get() = _falseBranch.value

    override val successors: List<PandaInstRef>
        get() = listOf(trueBranch, falseBranch)

    override val operands: List<PandaExpr> = listOf(condition)

    override fun toString(): String = "if ($condition)"

    override fun <T> accept(visitor: PandaInstVisitor<T>): T {
        return visitor.visitPandaIfInst(this)
    }
}

class PandaStringConstant : PandaConstant {
    // TODO: Think
    override val type: PandaType = PandaAnyType()
    override val operands: List<PandaValue> = emptyList()

    override fun <T> accept(visitor: PandaExprVisitor<T>): T {
        return visitor.visitPandaStringConstant(this)
    }

    override fun toString() = ""
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

class PandaThrowInst(override val location: PandaInstLocation, val throwable: PandaValue) : PandaInst {

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

class PandaVirtualCallExpr : PandaCallExpr {

    override fun <T> accept(visitor: PandaExprVisitor<T>): T {
        return visitor.visitPandaVirtualCallExpr(this)
    }

}

class PandaReturnInst(
    override val location: PandaInstLocation,
    returnValue: PandaValue?
) : PandaInst, CoreReturnInst<PandaInstLocation, PandaMethod, PandaExpr> {

    override val operands: List<PandaExpr> = listOfNotNull(returnValue)

    override fun <T> accept(visitor: PandaInstVisitor<T>): T {
        return visitor.visitPandaReturnInst(this)
    }

    override fun toString(): String = "return ${operands.firstOrNull()}"
}

class PandaAssignInst(
    override val location: PandaInstLocation,
    override val lhv: PandaValue,
    override val rhv: PandaExpr
) : PandaInst, CoreAssignInst<PandaInstLocation, PandaMethod, PandaValue, PandaExpr, PandaType> {

    override val operands: List<PandaExpr> = listOf(lhv, rhv)

    override fun toString(): String = "$lhv = $rhv"

    override fun <T> accept(visitor: PandaInstVisitor<T>): T {
        return visitor.visitPandaAssignInst(this)
    }
}

class PandaCallInst(
    override val location: PandaInstLocation,
    val callExpr: PandaExpr
) : PandaInst, CoreCallInst<PandaInstLocation, PandaMethod, PandaExpr> {

    override val operands: List<PandaExpr> = listOf(callExpr)

    override fun <T> accept(visitor: PandaInstVisitor<T>): T {
        return visitor.visitPandaCallInst(this)
    }

    override fun toString(): String = callExpr.toString()
}

class PandaTypeofExpr(override val value: PandaValue) : PandaUnaryExpr {

    override val type: PandaType = PandaAnyType()
    override val operands: List<PandaValue> = listOf(value)

    override fun <T> accept(visitor: PandaExprVisitor<T>): T {
        return visitor.visitPandaTypeofExpr(this)
    }

    override fun toString() = "typeof $value"
}

class PandaLocalVar(val id: Int) : PandaValue {

    override val type: PandaType = PandaAnyType()
    override val operands: List<PandaValue> = emptyList()

    override fun toString(): String = "%$id"

    override fun <T> accept(visitor: PandaExprVisitor<T>): T {
        return visitor.visitPandaLocalVar(this)
    }
}
