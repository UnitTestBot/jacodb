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

package org.jacodb.panda.staticvm.cfg

import org.jacodb.api.core.cfg.CoreExpr
import org.jacodb.api.core.cfg.CoreExprVisitor
import org.jacodb.api.core.cfg.CoreValue
import org.jacodb.panda.staticvm.classpath.*

sealed interface PandaExpr : CoreExpr<TypeNode, PandaValue> {
    override fun <T> accept(visitor: CoreExprVisitor<T>): T {
        TODO("Not yet implemented")
    }
}

sealed interface PandaValue : PandaExpr, CoreValue<PandaValue, TypeNode> {
    override val operands: List<PandaValue> get() = emptyList()
}

class PandaArgument(
    val index: Int,
    val name: String,
    override val type: TypeNode
) : PandaValue {
    override fun toString(): String = "$name(${type.typeName})"
}

class PandaLocalVar(
    val name: String,
    override val type: TypeNode
) : PandaValue {
    override fun toString(): String = name
}

class PandaFieldRef(
    val instance: PandaValue?,
    val field: FieldNode,
    override val type: TypeNode
) : PandaValue {
    override fun toString(): String = "${instance ?: field.enclosingClass}.${field.name}"
}

class PandaArrayRef(
    val array: PandaValue,
    val index: PandaValue,
    override val type: TypeNode
) : PandaValue {
    override fun toString(): String = "$array[$index]"
}

sealed interface PandaConstant : PandaValue

data class PandaNullPtr(override val type: TypeNode) : PandaConstant

data class PandaUndefined(override val type: TypeNode) : PandaConstant

data class PandaBoolean(
    val value: Boolean
) : PandaConstant {
    override val type: PandaPrimitiveTypeNode
        get() = PandaPrimitiveTypeNode.BYTE

    override fun toString(): String = "$value"
}

data class PandaByte(
    val value: Byte
) : PandaConstant {
    override val type: TypeNode
        get() = PandaPrimitiveTypeNode.BYTE
    override fun toString(): String = "$value"
}

data class PandaShort(
    val value: Short
) : PandaConstant {
    override val type: TypeNode
        get() = PandaPrimitiveTypeNode.SHORT
    override fun toString(): String = "$value"
}

data class PandaInt(
    val value: Int
) : PandaConstant {
    override val type: TypeNode
        get() = PandaPrimitiveTypeNode.INT
    override fun toString(): String = "$value"
}

data class PandaLong(
    val value: Long
) : PandaConstant {
    override val type: TypeNode
        get() = PandaPrimitiveTypeNode.LONG
    override fun toString(): String = "$value"
}

data class PandaFloat(
    val value: Float
) : PandaConstant {
    override val type: TypeNode
        get() = PandaPrimitiveTypeNode.FLOAT
    override fun toString(): String = "$value"
}

data class PandaDouble(
    val value: Double
) : PandaConstant {
    override val type: TypeNode
        get() = PandaPrimitiveTypeNode.DOUBLE
    override fun toString(): String = "$value"
}

data class PandaString(
    val value: String,
    override val type: ClassTypeNode
) : PandaConstant {
    override fun toString(): String = value
}


data class PandaNewExpr(
    override val type: TypeNode
) : PandaExpr {
    override val operands: List<PandaValue>
        get() = emptyList()

    override fun toString(): String = "new $type()"
}

interface PandaUnaryExpr : PandaExpr {
    val value: PandaValue

    override val operands: List<PandaValue>
        get() = listOf(value)
}

data class PandaNegExpr(
    override val type: TypeNode,
    override val value: PandaValue
) : PandaUnaryExpr {
    override fun toString(): String = "-$value"
}

data class PandaNotExpr(
    override val type: TypeNode,
    override val value: PandaValue
) : PandaUnaryExpr {
    override fun toString(): String = "!$value"
}

data class PandaLenArrayExpr(
    override val type: TypeNode,
    override val value: PandaValue
) : PandaUnaryExpr {
    override fun toString(): String = "length($value)"
}

data class PandaCastExpr(
    override val type: TypeNode,
    override val value: PandaValue
) : PandaUnaryExpr {
    override fun toString(): String = "($type) $value"
}

data class PandaIsInstanceExpr(
    override val type: TypeNode,
    override val value: PandaValue,
    val candidateType: TypeNode
) : PandaUnaryExpr {
    override fun toString(): String = "$value is $candidateType"
}

sealed interface PandaBinaryExpr : PandaExpr {
    val lhv: PandaValue
    val rhv: PandaValue

    override val operands: List<PandaValue>
        get() = listOf(lhv, rhv)
}

data class PandaAddExpr(
    override val type: TypeNode,
    override val lhv: PandaValue,
    override val rhv: PandaValue
) : PandaBinaryExpr {
    override fun toString(): String = "$lhv + $rhv"
}

data class PandaSubExpr(
    override val type: TypeNode,
    override val lhv: PandaValue,
    override val rhv: PandaValue
) : PandaBinaryExpr {
    override fun toString(): String = "$lhv - $rhv"
}

data class PandaMulExpr(
    override val type: TypeNode,
    override val lhv: PandaValue,
    override val rhv: PandaValue
) : PandaBinaryExpr {
    override fun toString(): String = "$lhv * $rhv"
}

data class PandaDivExpr(
    override val type: TypeNode,
    override val lhv: PandaValue,
    override val rhv: PandaValue
) : PandaBinaryExpr {
    override fun toString(): String = "$lhv / $rhv"
}

data class PandaModExpr(
    override val type: TypeNode,
    override val lhv: PandaValue,
    override val rhv: PandaValue
) : PandaBinaryExpr {
    override fun toString(): String = "$lhv % $rhv"
}

data class PandaAndExpr(
    override val type: TypeNode,
    override val lhv: PandaValue,
    override val rhv: PandaValue
) : PandaBinaryExpr {
    override fun toString(): String = "$lhv & $rhv"
}

data class PandaOrExpr(
    override val type: TypeNode,
    override val lhv: PandaValue,
    override val rhv: PandaValue
) : PandaBinaryExpr {
    override fun toString(): String = "$lhv | $rhv"
}

data class PandaXorExpr(
    override val type: TypeNode,
    override val lhv: PandaValue,
    override val rhv: PandaValue
) : PandaBinaryExpr {
    override fun toString(): String = "$lhv ^ $rhv"
}

data class PandaShlExpr(
    override val type: TypeNode,
    override val lhv: PandaValue,
    override val rhv: PandaValue
) : PandaBinaryExpr {
    override fun toString(): String = "$lhv << $rhv"
}

data class PandaShrExpr(
    override val type: TypeNode,
    override val lhv: PandaValue,
    override val rhv: PandaValue
) : PandaBinaryExpr {
    override fun toString(): String = "$lhv >> $rhv"
}

data class PandaAshlExpr(
    override val type: TypeNode,
    override val lhv: PandaValue,
    override val rhv: PandaValue
) : PandaBinaryExpr {
    override fun toString(): String = "$lhv a<< $rhv"
}

data class PandaAshrExpr(
    override val type: TypeNode,
    override val lhv: PandaValue,
    override val rhv: PandaValue
) : PandaBinaryExpr {
    override fun toString(): String = "$lhv a>> $rhv"
}

data class PandaCmpExpr(
    override val type: TypeNode,
    override val lhv: PandaValue,
    override val rhv: PandaValue
) : PandaBinaryExpr {
    override fun toString(): String = "$lhv cmp $rhv"
}

interface PandaConditionExpr : PandaBinaryExpr

data class PandaLeExpr(
    override val type: TypeNode,
    override val lhv: PandaValue,
    override val rhv: PandaValue
) : PandaConditionExpr {
    override fun toString(): String = "$lhv <= $rhv"
}

data class PandaLtExpr(
    override val type: TypeNode,
    override val lhv: PandaValue,
    override val rhv: PandaValue
) : PandaConditionExpr {
    override fun toString(): String = "$lhv < $rhv"
}

data class PandaGeExpr(
    override val type: TypeNode,
    override val lhv: PandaValue,
    override val rhv: PandaValue
) : PandaConditionExpr {
    override fun toString(): String = "$lhv >= $rhv"
}

data class PandaGtExpr(
    override val type: TypeNode,
    override val lhv: PandaValue,
    override val rhv: PandaValue
) : PandaConditionExpr {
    override fun toString(): String = "$lhv > $rhv"
}

data class PandaEqExpr(
    override val type: TypeNode,
    override val lhv: PandaValue,
    override val rhv: PandaValue
) : PandaConditionExpr {
    override fun toString(): String = "$lhv == $rhv"
}

data class PandaNeExpr(
    override val type: TypeNode,
    override val lhv: PandaValue,
    override val rhv: PandaValue
) : PandaConditionExpr {
    override fun toString(): String = "$lhv != $rhv"
}

data class PandaStaticCallExpr(
    val method: MethodNode,
    override val operands: List<PandaValue>
) : PandaExpr {
    override val type: TypeNode = method.returnType

    override fun toString(): String = "${method.signature}(${operands.joinToString(",")})"
}

data class PandaVirtualCallExpr(
    val method: MethodNode,
    val instance: PandaValue,
    override val operands: List<PandaValue>
) : PandaExpr {
    override val type: TypeNode = method.returnType

    override fun toString(): String = "${method.signature}(${operands.joinToString(",")})"
}

data class PandaPhiExpr(
    override val type: TypeNode,
    override val operands: List<PandaValue>
) : PandaExpr {
    override fun toString(): String = "Phi(${operands.joinToString(",")})"
}

data class PandaNewArrayExpr(
    override val type: TypeNode,
    val length: PandaValue
) : PandaExpr {
    override val operands: List<PandaValue>
        get() = listOf(length)

    override fun toString(): String = "new $type[$length]"
}
