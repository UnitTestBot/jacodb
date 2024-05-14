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
import org.jacodb.api.common.cfg.CommonInstanceCallExpr

interface PandaExpr : CommonExpr, Mappable {
    val type: PandaType

    override val typeName: String
        get() = type.typeName

    override val operands: List<PandaValue>

    fun <T> accept(visitor: PandaExprVisitor<T>): T

    override fun <T> accept(visitor: CommonExpr.Visitor<T>): T {
        TODO("Not yet implemented")
    }
}

/**
 * Mocks PandaExpr for WIP purposes.
 *
 * Map all unknown Panda IR instructions to this.
 */
class TODOExpr(
    val opcode: String,
    override val operands: List<PandaValue>,
) : PandaExpr {
    override val type: PandaType
        get() = PandaAnyType

    override fun toString(): String = "$opcode(${operands.joinToString(separator = ", ")})"

    override fun <T> accept(visitor: PandaExprVisitor<T>): T {
        return visitor.visitTODOExpr(this)
    }
}

interface PandaUnaryExpr : PandaExpr {
    val arg: PandaValue
}

interface PandaBinaryExpr : PandaExpr {
    val lhv: PandaValue
    val rhv: PandaValue
}

interface PandaCallExpr : PandaExpr, CommonCallExpr {
    val method: PandaMethod

    override val args: List<PandaValue>

    override val type: PandaType
        get() = method.type

    override val operands: List<PandaValue>
        get() = args
}

interface PandaInstanceCallExpr : PandaCallExpr, CommonInstanceCallExpr {
    override val instance: PandaValue

    override val operands: List<PandaValue>
        get() = listOf(instance) + args
}

interface PandaConditionExpr : PandaBinaryExpr {
    override val type: PandaType
        get() = PandaBoolType
}

enum class PandaCmpOp {
    EQ, NE, LT, LE, GT, GE
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

class PandaNeqExpr(
    override val lhv: PandaValue,
    override val rhv: PandaValue,
) : PandaConditionExpr {
    override val operands: List<PandaValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv != $rhv"

    override fun <T> accept(visitor: PandaExprVisitor<T>): T {
        return visitor.visitPandaNeqExpr(this)
    }
}

class PandaStrictNeqExpr(
    override val lhv: PandaValue,
    override val rhv: PandaValue,
) : PandaConditionExpr {
    override val operands: List<PandaValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv !== $rhv"

    override fun <T> accept(visitor: PandaExprVisitor<T>): T {
        return visitor.visitPandaStrictNeqExpr(this)
    }
}

class PandaEqExpr(
    override val lhv: PandaValue,
    override val rhv: PandaValue,
) : PandaConditionExpr {
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
    override val operands: List<PandaValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv === $rhv"

    override fun <T> accept(visitor: PandaExprVisitor<T>): T {
        return visitor.visitPandaStrictEqExpr(this)
    }
}

class PandaCastExpr(
    override val type: PandaType,
    val operand: PandaValue,
) : PandaExpr {
    override val operands: List<PandaValue>
        get() = listOf(operand)

    override fun toString(): String = "($type) $operand"

    override fun <T> accept(visitor: PandaExprVisitor<T>): T {
        return visitor.visitPandaCastExpr(this)
    }
}

class PandaNewExpr(
    override val typeName: String,
    val params: List<PandaValue>,
) : PandaExpr {
    override val type: PandaType
        get() = PandaClassTypeImpl(typeName)

    override val operands: List<PandaValue>
        get() = params

    override fun toString(): String = "new ${typeName}(${params.joinToString(separator = ", ")})"

    override fun <T> accept(visitor: PandaExprVisitor<T>): T {
        return visitor.visitPandaNewExpr(this)
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

class PandaSubExpr(
    override val lhv: PandaValue,
    override val rhv: PandaValue,
) : PandaBinaryExpr {
    override val type: PandaType
        get() = PandaAnyType

    override val operands: List<PandaValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv - $rhv"

    override fun <T> accept(visitor: PandaExprVisitor<T>): T {
        return visitor.visitPandaSubExpr(this)
    }
}

class PandaMulExpr(
    override val lhv: PandaValue,
    override val rhv: PandaValue,
) : PandaBinaryExpr {
    override val type: PandaType
        get() = PandaAnyType

    override val operands: List<PandaValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv * $rhv"

    override fun <T> accept(visitor: PandaExprVisitor<T>): T {
        return visitor.visitPandaMulExpr(this)
    }
}

class PandaDivExpr(
    override val lhv: PandaValue,
    override val rhv: PandaValue,
) : PandaBinaryExpr {
    override val type: PandaType
        get() = PandaAnyType

    override val operands: List<PandaValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv / $rhv"

    override fun <T> accept(visitor: PandaExprVisitor<T>): T {
        return visitor.visitPandaDivExpr(this)
    }
}

class PandaModExpr(
    override val lhv: PandaValue,
    override val rhv: PandaValue,
) : PandaBinaryExpr {
    override val type: PandaType
        get() = PandaAnyType

    override val operands: List<PandaValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv % $rhv"

    override fun <T> accept(visitor: PandaExprVisitor<T>): T {
        return visitor.visitPandaModExpr(this)
    }
}

class PandaExpExpr(
    override val lhv: PandaValue,
    override val rhv: PandaValue,
) : PandaBinaryExpr {
    override val type: PandaType
        get() = PandaAnyType

    override val operands: List<PandaValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv ** $rhv"

    override fun <T> accept(visitor: PandaExprVisitor<T>): T {
        return visitor.visitPandaExpExpr(this)
    }
}

class PandaStaticCallExpr(
    private val lazyMethod: Lazy<PandaMethod>,
    override val args: List<PandaValue>,
) : PandaCallExpr {
    override val method: PandaMethod
        get() = lazyMethod.value

    override fun toString(): String = "${method.name}(${args.joinToString(", ")})"

    override fun <T> accept(visitor: PandaExprVisitor<T>): T {
        return visitor.visitPandaStaticCallExpr(this)
    }
}

class PandaVirtualCallExpr(
    private val lazyMethod: Lazy<PandaMethod>,
    override val args: List<PandaValue>,
) : PandaCallExpr {
    override val method: PandaMethod
        get() = lazyMethod.value

    override fun toString(): String = "${method.name}(${args.joinToString(", ")})"

    override fun <T> accept(visitor: PandaExprVisitor<T>): T {
        return visitor.visitPandaVirtualCallExpr(this)
    }
}

class PandaInstanceVirtualCallExpr(
    private val lazyMethod: Lazy<PandaMethod>,
    override val args: List<PandaValue>,
    override val instance: PandaValue,
) : PandaInstanceCallExpr {
    override val method: PandaMethod
        get() = lazyMethod.value

    override fun toString(): String = "$instance.${method.name}(${args.joinToString(", ")})"

    override fun <T> accept(visitor: PandaExprVisitor<T>): T {
        return visitor.visitPandaInstanceVirtualCallExpr(this)
    }
}

class PandaNegExpr(
    override val arg: PandaValue,
) : PandaUnaryExpr {
    override val type: PandaType
        get() = PandaAnyType

    override val operands: List<PandaValue>
        get() = listOf(arg)

    override fun toString(): String = "-$arg"

    override fun <T> accept(visitor: PandaExprVisitor<T>): T {
        return visitor.visitPandaNegExpr(this)
    }
}

class PandaTypeofExpr(
    override val arg: PandaValue,
) : PandaUnaryExpr {
    override val type: PandaType
        get() = PandaAnyType

    override val operands: List<PandaValue>
        get() = listOf(arg)

    override fun toString(): String = "typeof $arg"

    override fun <T> accept(visitor: PandaExprVisitor<T>): T {
        return visitor.visitPandaTypeofExpr(this)
    }
}

class PandaToNumericExpr(
    override val arg: PandaValue,
) : PandaUnaryExpr {
    override val type: PandaType
        get() = PandaNumberType

    override val operands: List<PandaValue>
        get() = listOf(arg)

    override fun toString(): String = "numeric($arg)"

    override fun <T> accept(visitor: PandaExprVisitor<T>): T {
        return visitor.visitPandaToNumericExpr(this)
    }
}

class PandaLengthExpr(
    val array: PandaValue,
) : PandaExpr {
    override val type: PandaType
        get() = PandaNumberType
    override val operands: List<PandaValue>
        get() = listOf(array)

    override fun <T> accept(visitor: PandaExprVisitor<T>): T {
        return visitor.visitPandaLengthExpr(this)
    }

}

class PandaCreateEmptyArrayExpr : PandaExpr {
    override val type: PandaType
        get() = PandaAnyType

    override val operands: List<PandaValue>
        get() = listOf()

    override fun toString(): String = "[]"

    override fun <T> accept(visitor: PandaExprVisitor<T>): T {
        return visitor.visitPandaCreateEmptyArrayExpr(this)
    }
}
