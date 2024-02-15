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

package org.jacodb.panda.staticvm

import org.jacodb.api.core.cfg.CoreExpr
import org.jacodb.api.core.cfg.CoreExprVisitor

sealed interface PandaExpr : CoreExpr<PandaType, PandaValue> {
    override fun <T> accept(visitor: CoreExprVisitor<T>): T {
        TODO("Not yet implemented")
    }
}

sealed interface PandaValue : PandaExpr {
    override val operands: List<PandaValue> get() = emptyList()
}

class PandaArgument(
    val index: Int,
    val name: String,
    override val type: PandaType
) : PandaValue {
    override fun toString(): String = "$name(${type.arkName})"
}

class PandaLocalVar(
    val name: String,
    override val type: PandaType
) : PandaValue {
    override fun toString(): String = name
}

class PandaFieldRef(
    val instance: PandaValue?,
    val field: PandaField,
    override val type: PandaType
) : PandaValue {
    override fun toString(): String = "${instance ?: field.declaringClassType}.${field.name}"
}

sealed interface PandaConstant : PandaValue

data class PandaBoolean(
    val value: Boolean,
    override val type: PandaPrimitiveType
) : PandaConstant {
    override fun toString(): String = "$value"
}

data class PandaByte(
    val value: Byte,
    override val type: PandaPrimitiveType
) : PandaConstant {
    override fun toString(): String = "$value"
}

data class PandaShort(
    val value: Short,
    override val type: PandaPrimitiveType
) : PandaConstant {
    override fun toString(): String = "$value"
}

data class PandaInt(
    val value: Int,
    override val type: PandaPrimitiveType
) : PandaConstant {
    override fun toString(): String = "$value"
}

data class PandaLong(
    val value: Long,
    override val type: PandaPrimitiveType
) : PandaConstant {
    override fun toString(): String = "$value"
}

data class PandaFloat(
    val value: Float,
    override val type: PandaPrimitiveType
) : PandaConstant {
    override fun toString(): String = "$value"
}

data class PandaDouble(
    val value: Double,
    override val type: PandaPrimitiveType
) : PandaConstant {
    override fun toString(): String = "$value"
}

data class PandaString(
    val value: String,
    override val type: PandaClassNode
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

data class PandaCastExpr(
    override val type: PandaType,
    override val value: PandaValue
) : PandaUnaryExpr {
    override fun toString(): String = "($type) $value"
}

data class PandaIsInstanceExpr(
    override val type: PandaPrimitiveType,
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

data class PandaStaticCallExpr(
    val method: PandaMethod,
    override val operands: List<PandaValue>
) : PandaExpr {
    override val type: PandaType = method.project.findTypeOrNull(method.returnType.typeName)
        ?: throw AssertionError("Method return type not found")

    override fun toString(): String = "${method.name}(${operands.joinToString(",")})"
}

data class PandaVirtualCallExpr(
    val method: PandaMethod,
    val instance: PandaValue,
    override val operands: List<PandaValue>
) : PandaExpr {
    override val type: PandaType = method.project.findTypeOrNull(method.returnType.typeName)
        ?: throw AssertionError("Method return type not found")

    override fun toString(): String = "${method.name}(${operands.joinToString(",")})"
}

data class PandaPhiExpr(
    override val type: PandaType,
    override val operands: List<PandaValue>
) : PandaExpr {
    override fun toString(): String = "Phi(${operands.joinToString(",")})"
}
