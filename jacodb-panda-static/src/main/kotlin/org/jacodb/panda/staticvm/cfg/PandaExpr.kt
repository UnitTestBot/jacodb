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

import org.jacodb.api.common.CommonType
import org.jacodb.api.common.cfg.*
import org.jacodb.panda.staticvm.classpath.*

sealed interface PandaExpr : CommonExpr {
    override fun <T> accept(visitor: CommonExpr.Visitor<T>): T {
        TODO("Not yet implemented")
    }
}

sealed interface PandaValue : PandaExpr, CommonValue {
    override val operands: List<PandaValue> get() = emptyList()
}

interface PandaSimpleValue : PandaValue

class PandaArgument(
    override val index: Int,
    override val name: String,
    override val type: PandaType
) : PandaSimpleValue, CommonArgument {
    override fun toString(): String = "$name(${type.typeName})"
}

sealed interface PandaLocalVar : PandaSimpleValue {
    val name: String
    override val type: PandaType
}

class PandaLocalVarImpl(
    override val name: String,
    override val type: PandaType
) : PandaLocalVar {
    override fun toString(): String = name
}

class PandaThis(
    override val name: String,
    override val type: PandaType
) : PandaLocalVar, CommonThis {
    override fun toString(): String = name
}

class PandaFieldAccess(
    override val instance: PandaValue?,
    override val classField: PandaField
) : PandaValue, CommonFieldRef {
    override fun toString(): String = "${instance ?: classField.enclosingClass}.${classField.name}"

    override val type: CommonType
        get() = classField.type
}

class PandaArrayAccess(
    override val array: PandaValue,
    override val index: PandaValue,
    override val type: PandaType
) : PandaValue, CommonArrayAccess {
    override fun toString(): String = "$array[$index]"
}

sealed interface PandaConstant : PandaSimpleValue

data class PandaNullPtr(override val type: PandaType) : PandaConstant

data class PandaUndefined(override val type: PandaType) : PandaConstant

data class PandaBoolean(
    val value: Boolean
) : PandaConstant {
    override val type: PandaPrimitivePandaType
        get() = PandaPrimitivePandaType.BYTE

    override fun toString(): String = "$value"
}

data class PandaByte(
    val value: Byte
) : PandaConstant {
    override val type: PandaType
        get() = PandaPrimitivePandaType.BYTE
    override fun toString(): String = "$value"
}

data class PandaShort(
    val value: Short
) : PandaConstant {
    override val type: PandaType
        get() = PandaPrimitivePandaType.SHORT
    override fun toString(): String = "$value"
}

data class PandaInt(
    val value: Int
) : PandaConstant {
    override val type: PandaType
        get() = PandaPrimitivePandaType.INT
    override fun toString(): String = "$value"
}

data class PandaLong(
    val value: Long
) : PandaConstant {
    override val type: PandaType
        get() = PandaPrimitivePandaType.LONG
    override fun toString(): String = "$value"
}

data class PandaFloat(
    val value: Float
) : PandaConstant {
    override val type: PandaType
        get() = PandaPrimitivePandaType.FLOAT
    override fun toString(): String = "$value"
}

data class PandaDouble(
    val value: Double
) : PandaConstant {
    override val type: PandaType
        get() = PandaPrimitivePandaType.DOUBLE
    override fun toString(): String = "$value"
}

data class PandaString(
    val value: String,
    override val type: PandaClassType
) : PandaConstant {
    override fun toString(): String = value
}


data class PandaNewExpr(
    override val type: PandaType
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
    override val type: PandaType,
    override val value: PandaValue
) : PandaUnaryExpr {
    override fun toString(): String = "-$value"
}

data class PandaNotExpr(
    override val type: PandaType,
    override val value: PandaValue
) : PandaUnaryExpr {
    override fun toString(): String = "!$value"
}

data class PandaLenArrayExpr(
    override val type: PandaType,
    override val value: PandaValue
) : PandaUnaryExpr {
    override fun toString(): String = "length($value)"
}

data class PandaCastExpr(
    override val type: PandaType,
    override val value: PandaValue
) : PandaUnaryExpr {
    override fun toString(): String = "($type) $value"
}

data class PandaIsInstanceExpr(
    override val type: PandaType,
    override val value: PandaValue,
    val candidateType: PandaType
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
    override val type: PandaType,
    override val lhv: PandaValue,
    override val rhv: PandaValue
) : PandaBinaryExpr {
    override fun toString(): String = "$lhv + $rhv"
}

data class PandaSubExpr(
    override val type: PandaType,
    override val lhv: PandaValue,
    override val rhv: PandaValue
) : PandaBinaryExpr {
    override fun toString(): String = "$lhv - $rhv"
}

data class PandaMulExpr(
    override val type: PandaType,
    override val lhv: PandaValue,
    override val rhv: PandaValue
) : PandaBinaryExpr {
    override fun toString(): String = "$lhv * $rhv"
}

data class PandaDivExpr(
    override val type: PandaType,
    override val lhv: PandaValue,
    override val rhv: PandaValue
) : PandaBinaryExpr {
    override fun toString(): String = "$lhv / $rhv"
}

data class PandaModExpr(
    override val type: PandaType,
    override val lhv: PandaValue,
    override val rhv: PandaValue
) : PandaBinaryExpr {
    override fun toString(): String = "$lhv % $rhv"
}

data class PandaAndExpr(
    override val type: PandaType,
    override val lhv: PandaValue,
    override val rhv: PandaValue
) : PandaBinaryExpr {
    override fun toString(): String = "$lhv & $rhv"
}

data class PandaOrExpr(
    override val type: PandaType,
    override val lhv: PandaValue,
    override val rhv: PandaValue
) : PandaBinaryExpr {
    override fun toString(): String = "$lhv | $rhv"
}

data class PandaXorExpr(
    override val type: PandaType,
    override val lhv: PandaValue,
    override val rhv: PandaValue
) : PandaBinaryExpr {
    override fun toString(): String = "$lhv ^ $rhv"
}

data class PandaShlExpr(
    override val type: PandaType,
    override val lhv: PandaValue,
    override val rhv: PandaValue
) : PandaBinaryExpr {
    override fun toString(): String = "$lhv << $rhv"
}

data class PandaShrExpr(
    override val type: PandaType,
    override val lhv: PandaValue,
    override val rhv: PandaValue
) : PandaBinaryExpr {
    override fun toString(): String = "$lhv >> $rhv"
}

data class PandaAshlExpr(
    override val type: PandaType,
    override val lhv: PandaValue,
    override val rhv: PandaValue
) : PandaBinaryExpr {
    override fun toString(): String = "$lhv a<< $rhv"
}

data class PandaAshrExpr(
    override val type: PandaType,
    override val lhv: PandaValue,
    override val rhv: PandaValue
) : PandaBinaryExpr {
    override fun toString(): String = "$lhv a>> $rhv"
}

data class PandaCmpExpr(
    override val type: PandaType,
    override val lhv: PandaValue,
    override val rhv: PandaValue
) : PandaBinaryExpr {
    override fun toString(): String = "$lhv cmp $rhv"
}

interface PandaConditionExpr : PandaBinaryExpr

data class PandaLeExpr(
    override val type: PandaType,
    override val lhv: PandaValue,
    override val rhv: PandaValue
) : PandaConditionExpr {
    override fun toString(): String = "$lhv <= $rhv"
}

data class PandaLtExpr(
    override val type: PandaType,
    override val lhv: PandaValue,
    override val rhv: PandaValue
) : PandaConditionExpr {
    override fun toString(): String = "$lhv < $rhv"
}

data class PandaGeExpr(
    override val type: PandaType,
    override val lhv: PandaValue,
    override val rhv: PandaValue
) : PandaConditionExpr {
    override fun toString(): String = "$lhv >= $rhv"
}

data class PandaGtExpr(
    override val type: PandaType,
    override val lhv: PandaValue,
    override val rhv: PandaValue
) : PandaConditionExpr {
    override fun toString(): String = "$lhv > $rhv"
}

data class PandaEqExpr(
    override val type: PandaType,
    override val lhv: PandaValue,
    override val rhv: PandaValue
) : PandaConditionExpr {
    override fun toString(): String = "$lhv == $rhv"
}

data class PandaNeExpr(
    override val type: PandaType,
    override val lhv: PandaValue,
    override val rhv: PandaValue
) : PandaConditionExpr {
    override fun toString(): String = "$lhv != $rhv"
}

sealed interface PandaCallExpr : PandaExpr, CommonCallExpr {
    val callee: PandaMethod
}

data class PandaStaticCallExpr(
    override val callee: PandaMethod,
    override val operands: List<PandaValue>
) : PandaCallExpr {
    override val type: PandaType = callee.returnType

    override val args: List<CommonValue>
        get() = operands
    override fun toString(): String = "${callee.signature}(${operands.joinToString(",")})"
}

data class PandaVirtualCallExpr(
    override val callee: PandaMethod,
    override val instance: PandaValue,
    override val operands: List<PandaValue>
) : PandaCallExpr, CommonInstanceCallExpr {
    override val type: PandaType = callee.returnType

    override val args: List<CommonValue>
        get() = operands
    override fun toString(): String = "${callee.signature}(${operands.joinToString(",")})"
}

data class PandaPhiExpr(
    override val type: PandaType,
    override val operands: List<PandaValue>
) : PandaExpr {
    override fun toString(): String = "Phi(${operands.joinToString(",")})"
}

data class PandaNewArrayExpr(
    override val type: PandaType,
    val length: PandaValue
) : PandaExpr {
    override val operands: List<PandaValue>
        get() = listOf(length)

    override fun toString(): String = "new $type[$length]"
}
