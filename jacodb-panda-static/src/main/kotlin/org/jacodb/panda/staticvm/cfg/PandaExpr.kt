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

import org.jacodb.api.common.cfg.CommonArgument
import org.jacodb.api.common.cfg.CommonArrayAccess
import org.jacodb.api.common.cfg.CommonCallExpr
import org.jacodb.api.common.cfg.CommonExpr
import org.jacodb.api.common.cfg.CommonFieldRef
import org.jacodb.api.common.cfg.CommonInstanceCallExpr
import org.jacodb.api.common.cfg.CommonThis
import org.jacodb.api.common.cfg.CommonValue
import org.jacodb.panda.staticvm.classpath.PandaClassType
import org.jacodb.panda.staticvm.classpath.PandaField
import org.jacodb.panda.staticvm.classpath.PandaMethod
import org.jacodb.panda.staticvm.classpath.PandaPrimitiveType
import org.jacodb.panda.staticvm.classpath.PandaType

sealed interface PandaExpr : CommonExpr {
    val type: PandaType

    override val typeName: String
        get() = type.typeName

    override fun <T> accept(visitor: CommonExpr.Visitor<T>): T {
        TODO("Not yet implemented")
    }
}

sealed interface PandaValue : PandaExpr, CommonValue

interface PandaSimpleValue : PandaValue {
    override val operands: List<PandaValue>
        get() = emptyList()
}

class PandaArgument(
    override val index: Int,
    override val name: String,
    override val type: PandaType,
) : PandaSimpleValue, CommonArgument {
    override fun toString(): String = "$name(${type.typeName})"
}

sealed interface PandaLocalVar : PandaSimpleValue {
    val name: String
    override val type: PandaType
}

class PandaLocalVarImpl(
    override val name: String,
    override val type: PandaType,
) : PandaLocalVar {
    override fun toString(): String = name
}

class PandaThis(
    override val name: String,
    override val type: PandaType,
) : PandaLocalVar, CommonThis {
    override fun toString(): String = name
}

class PandaFieldRef(
    override val instance: PandaValue?,
    val field: PandaField,
) : PandaValue, CommonFieldRef {

    override val type: PandaType
        get() = this.field.type

    override val classField: PandaField
        get() = this.field

    override val operands: List<CommonValue>
        get() = listOfNotNull(instance)

    override fun toString(): String = "${instance ?: field.enclosingClass.name}.${classField.name}"
}

class PandaArrayAccess(
    override val array: PandaValue,
    override val index: PandaValue,
    override val type: PandaType,
) : PandaValue, CommonArrayAccess {
    override val operands: List<CommonValue>
        get() = listOf(array, index)

    override fun toString(): String = "$array[$index]"
}

sealed interface PandaConstant : PandaSimpleValue

object PandaNull : PandaConstant {
    override val type: PandaType
        get() = PandaPrimitiveType.NULL

    override fun toString(): String = "null"
}

data class PandaUndefined(override val type: PandaType) : PandaConstant {
    override fun toString(): String = "undefined"
}

data class PandaBoolean(
    val value: Boolean,
) : PandaConstant {
    override val type: PandaPrimitiveType
        get() = PandaPrimitiveType.BYTE

    override fun toString(): String = "$value"
}

data class PandaByte(
    val value: Byte,
) : PandaConstant {
    override val type: PandaType
        get() = PandaPrimitiveType.BYTE

    override fun toString(): String = "$value"
}

data class PandaShort(
    val value: Short,
) : PandaConstant {
    override val type: PandaType
        get() = PandaPrimitiveType.SHORT

    override fun toString(): String = "$value"
}

data class PandaInt(
    val value: Int,
) : PandaConstant {
    override val type: PandaType
        get() = PandaPrimitiveType.INT

    override fun toString(): String = "$value"
}

data class PandaLong(
    val value: Long,
) : PandaConstant {
    override val type: PandaType
        get() = PandaPrimitiveType.LONG

    override fun toString(): String = "$value"
}

data class PandaUByte(
    val value: UByte,
) : PandaConstant {
    override val type: PandaType
        get() = PandaPrimitiveType.UBYTE

    override fun toString(): String = "$value"
}

data class PandaUShort(
    val value: UShort,
) : PandaConstant {
    override val type: PandaType
        get() = PandaPrimitiveType.USHORT

    override fun toString(): String = "$value"
}

data class PandaUInt(
    val value: UInt,
) : PandaConstant {
    override val type: PandaType
        get() = PandaPrimitiveType.UINT

    override fun toString(): String = "$value"
}

data class PandaULong(
    val value: ULong,
) : PandaConstant {
    override val type: PandaType
        get() = PandaPrimitiveType.ULONG

    override fun toString(): String = "$value"
}

data class PandaFloat(
    val value: Float,
) : PandaConstant {
    override val type: PandaType
        get() = PandaPrimitiveType.FLOAT

    override fun toString(): String = "$value"
}

data class PandaDouble(
    val value: Double,
) : PandaConstant {
    override val type: PandaType
        get() = PandaPrimitiveType.DOUBLE

    override fun toString(): String = "$value"
}

data class PandaString(
    val value: String,
    override val type: PandaClassType,
) : PandaConstant {
    override fun toString(): String = "\"$value\""
}

data class PandaTypeConstant(
    val value: PandaType,
    override val type: PandaType,
) : PandaConstant {
    override fun toString(): String = "${value}.class"
}

data class PandaNewExpr(
    override val type: PandaType,
) : PandaExpr {
    override val operands: List<PandaValue>
        get() = emptyList()

    override fun toString(): String = "new ${type.typeName}"
}

interface PandaUnaryExpr : PandaExpr {
    val arg: PandaValue

    override val operands: List<PandaValue>
        get() = listOf(arg)
}

data class PandaNegExpr(
    override val type: PandaType,
    override val arg: PandaValue,
) : PandaUnaryExpr {
    override fun toString(): String = "-$arg"
}

data class PandaNotExpr(
    override val type: PandaType,
    override val arg: PandaValue,
) : PandaUnaryExpr {
    override fun toString(): String = "!$arg"
}

data class PandaLenArrayExpr(
    override val type: PandaType,
    override val arg: PandaValue,
) : PandaUnaryExpr {
    override fun toString(): String = "length($arg)"
}

data class PandaCastExpr(
    override val type: PandaType,
    override val arg: PandaValue,
) : PandaUnaryExpr {
    override fun toString(): String = "($type) $arg"
}

data class PandaIsInstanceExpr(
    override val type: PandaType,
    override val arg: PandaValue,
    val candidateType: PandaType,
) : PandaUnaryExpr {
    override fun toString(): String = "$arg is $candidateType"
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
    override val rhv: PandaValue,
) : PandaBinaryExpr {
    override fun toString(): String = "$lhv + $rhv"
}

data class PandaSubExpr(
    override val type: PandaType,
    override val lhv: PandaValue,
    override val rhv: PandaValue,
) : PandaBinaryExpr {
    override fun toString(): String = "$lhv - $rhv"
}

data class PandaMulExpr(
    override val type: PandaType,
    override val lhv: PandaValue,
    override val rhv: PandaValue,
) : PandaBinaryExpr {
    override fun toString(): String = "$lhv * $rhv"
}

data class PandaDivExpr(
    override val type: PandaType,
    override val lhv: PandaValue,
    override val rhv: PandaValue,
) : PandaBinaryExpr {
    override fun toString(): String = "$lhv / $rhv"
}

data class PandaModExpr(
    override val type: PandaType,
    override val lhv: PandaValue,
    override val rhv: PandaValue,
) : PandaBinaryExpr {
    override fun toString(): String = "$lhv % $rhv"
}

data class PandaAndExpr(
    override val type: PandaType,
    override val lhv: PandaValue,
    override val rhv: PandaValue,
) : PandaBinaryExpr {
    override fun toString(): String = "$lhv & $rhv"
}

data class PandaOrExpr(
    override val type: PandaType,
    override val lhv: PandaValue,
    override val rhv: PandaValue,
) : PandaBinaryExpr {
    override fun toString(): String = "$lhv | $rhv"
}

data class PandaXorExpr(
    override val type: PandaType,
    override val lhv: PandaValue,
    override val rhv: PandaValue,
) : PandaBinaryExpr {
    override fun toString(): String = "$lhv ^ $rhv"
}

data class PandaShlExpr(
    override val type: PandaType,
    override val lhv: PandaValue,
    override val rhv: PandaValue,
) : PandaBinaryExpr {
    override fun toString(): String = "$lhv << $rhv"
}

data class PandaShrExpr(
    override val type: PandaType,
    override val lhv: PandaValue,
    override val rhv: PandaValue,
) : PandaBinaryExpr {
    override fun toString(): String = "$lhv >> $rhv"
}

data class PandaAshlExpr(
    override val type: PandaType,
    override val lhv: PandaValue,
    override val rhv: PandaValue,
) : PandaBinaryExpr {
    override fun toString(): String = "$lhv a<< $rhv"
}

data class PandaAshrExpr(
    override val type: PandaType,
    override val lhv: PandaValue,
    override val rhv: PandaValue,
) : PandaBinaryExpr {
    override fun toString(): String = "$lhv a>> $rhv"
}

data class PandaCmpExpr(
    override val type: PandaType,
    override val lhv: PandaValue,
    override val rhv: PandaValue,
) : PandaBinaryExpr {
    override fun toString(): String = "$lhv cmp $rhv"
}

interface PandaConditionExpr : PandaBinaryExpr

data class PandaLeExpr(
    override val type: PandaType,
    override val lhv: PandaValue,
    override val rhv: PandaValue,
) : PandaConditionExpr {
    override fun toString(): String = "$lhv <= $rhv"
}

data class PandaLtExpr(
    override val type: PandaType,
    override val lhv: PandaValue,
    override val rhv: PandaValue,
) : PandaConditionExpr {
    override fun toString(): String = "$lhv < $rhv"
}

data class PandaGeExpr(
    override val type: PandaType,
    override val lhv: PandaValue,
    override val rhv: PandaValue,
) : PandaConditionExpr {
    override fun toString(): String = "$lhv >= $rhv"
}

data class PandaGtExpr(
    override val type: PandaType,
    override val lhv: PandaValue,
    override val rhv: PandaValue,
) : PandaConditionExpr {
    override fun toString(): String = "$lhv > $rhv"
}

data class PandaEqExpr(
    override val type: PandaType,
    override val lhv: PandaValue,
    override val rhv: PandaValue,
) : PandaConditionExpr {
    override fun toString(): String = "$lhv == $rhv"
}

data class PandaNeExpr(
    override val type: PandaType,
    override val lhv: PandaValue,
    override val rhv: PandaValue,
) : PandaConditionExpr {
    override fun toString(): String = "$lhv != $rhv"
}

sealed interface PandaCallExpr : PandaExpr, CommonCallExpr {
    val method: PandaMethod
    override val operands: List<CommonValue>
        get() = args
}

data class PandaStaticCallExpr(
    override val method: PandaMethod,
    override val operands: List<PandaValue>,
) : PandaCallExpr {
    override val type: PandaType = method.returnType

    override val args: List<PandaValue>
        get() = operands

    override fun toString(): String = "${method.enclosingClass.name}::${method.name}(${operands.joinToString(", ")})"
}

data class PandaIntrinsicCallExpr(
    val intrinsic: String,
    override val type: PandaType,
    override val operands: List<PandaValue>,
) : PandaExpr, CommonCallExpr {
    override val args: List<PandaValue>
        get() = operands

    override fun toString(): String = "intrinsic<${intrinsic}>(${operands.joinToString(", ")})"
}

data class PandaVirtualCallExpr(
    override val method: PandaMethod,
    override val instance: PandaValue,
    override val operands: List<PandaValue>,
) : PandaCallExpr, CommonInstanceCallExpr {
    override val type: PandaType = method.returnType

    override val args: List<PandaValue>
        get() = operands

    override fun toString(): String = "${instance}.${method.name}(${operands.joinToString(", ")})"
}

data class PandaPhiExpr(
    override val type: PandaType,
    override val operands: List<PandaValue>,
    val predecessors: List<PandaInstRef>,
) : PandaExpr {
    override fun toString(): String = "Phi(${operands.joinToString(", ")})"
}

data class PandaNewArrayExpr(
    override val type: PandaType,
    val length: PandaValue,
) : PandaExpr {
    override val operands: List<PandaValue>
        get() = listOf(length)

    override fun toString(): String = "new $type[$length]"
}
