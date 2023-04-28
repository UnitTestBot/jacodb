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

package org.jacodb.api.cfg

import org.jacodb.api.JcMethod
import org.jacodb.api.JcType
import org.jacodb.api.JcTypedField
import org.jacodb.api.JcTypedMethod

interface TypedMethodRef {
    val name: String
    val method: JcTypedMethod
}

interface JcInstLocation {
    val method: JcMethod
    val index: Int
    val lineNumber: Int
}

interface JcInst {
    val location: JcInstLocation
    val operands: List<JcExpr>

    val lineNumber get() = location.lineNumber

    fun <T> accept(visitor: JcInstVisitor<T>): T
}

abstract class AbstractJcInst(override val location: JcInstLocation) : JcInst {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AbstractJcInst

        if (location != other.location) return false

        return true
    }

    override fun hashCode(): Int {
        return location.hashCode()
    }
}

data class JcInstRef(
    val index: Int
)

class JcAssignInst(
    location: JcInstLocation,
    val lhv: JcValue,
    val rhv: JcExpr
) : AbstractJcInst(location) {

    override val operands: List<JcExpr>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv = $rhv"

    override fun <T> accept(visitor: JcInstVisitor<T>): T {
        return visitor.visitJcAssignInst(this)
    }
}

class JcEnterMonitorInst(
    location: JcInstLocation,
    val monitor: JcValue
) : AbstractJcInst(location) {
    override val operands: List<JcExpr>
        get() = listOf(monitor)

    override fun toString(): String = "enter monitor $monitor"

    override fun <T> accept(visitor: JcInstVisitor<T>): T {
        return visitor.visitJcEnterMonitorInst(this)
    }
}

class JcExitMonitorInst(
    location: JcInstLocation,
    val monitor: JcValue
) : AbstractJcInst(location) {
    override val operands: List<JcExpr>
        get() = listOf(monitor)

    override fun toString(): String = "exit monitor $monitor"

    override fun <T> accept(visitor: JcInstVisitor<T>): T {
        return visitor.visitJcExitMonitorInst(this)
    }
}

class JcCallInst(
    location: JcInstLocation,
    val callExpr: JcCallExpr
) : AbstractJcInst(location) {
    override val operands: List<JcExpr>
        get() = listOf(callExpr)

    override fun toString(): String = "$callExpr"

    override fun <T> accept(visitor: JcInstVisitor<T>): T {
        return visitor.visitJcCallInst(this)
    }
}

interface JcTerminatingInst : JcInst

class JcReturnInst(
    location: JcInstLocation,
    val returnValue: JcValue?
) : AbstractJcInst(location), JcTerminatingInst {
    override val operands: List<JcExpr>
        get() = listOfNotNull(returnValue)

    override fun toString(): String = "return" + (returnValue?.let { " $it" } ?: "")

    override fun <T> accept(visitor: JcInstVisitor<T>): T {
        return visitor.visitJcReturnInst(this)
    }
}

class JcThrowInst(
    location: JcInstLocation,
    val throwable: JcValue
) : AbstractJcInst(location), JcTerminatingInst {
    override val operands: List<JcExpr>
        get() = listOf(throwable)

    override fun toString(): String = "throw $throwable"

    override fun <T> accept(visitor: JcInstVisitor<T>): T {
        return visitor.visitJcThrowInst(this)
    }
}

class JcCatchInst(
    location: JcInstLocation,
    val throwable: JcValue,
    val throwableTypes: List<JcType>,
    val throwers: List<JcInstRef>
) : AbstractJcInst(location) {
    override val operands: List<JcExpr>
        get() = listOf(throwable)

    override fun toString(): String = "catch ($throwable: ${throwable.type.typeName})"

    override fun <T> accept(visitor: JcInstVisitor<T>): T {
        return visitor.visitJcCatchInst(this)
    }
}

interface JcBranchingInst : JcInst {
    val successors: List<JcInstRef>
}

class JcGotoInst(
    location: JcInstLocation,
    val target: JcInstRef
) : AbstractJcInst(location), JcBranchingInst {
    override val operands: List<JcExpr>
        get() = emptyList()

    override val successors: List<JcInstRef>
        get() = listOf(target)

    override fun toString(): String = "goto $target"

    override fun <T> accept(visitor: JcInstVisitor<T>): T {
        return visitor.visitJcGotoInst(this)
    }
}

class JcIfInst(
    location: JcInstLocation,
    val condition: JcConditionExpr,
    val trueBranch: JcInstRef,
    val falseBranch: JcInstRef
) : AbstractJcInst(location), JcBranchingInst {
    override val operands: List<JcExpr>
        get() = listOf(condition)

    override val successors: List<JcInstRef>
        get() = listOf(trueBranch, falseBranch)

    override fun toString(): String = "if ($condition)"

    override fun <T> accept(visitor: JcInstVisitor<T>): T {
        return visitor.visitJcIfInst(this)
    }
}

class JcSwitchInst(
    location: JcInstLocation,
    val key: JcValue,
    val branches: Map<JcValue, JcInstRef>,
    val default: JcInstRef
) : AbstractJcInst(location), JcBranchingInst {
    override val operands: List<JcExpr>
        get() = listOf(key) + branches.keys

    override val successors: List<JcInstRef>
        get() = branches.values + default

    override fun toString(): String = "switch ($key)"

    override fun <T> accept(visitor: JcInstVisitor<T>): T {
        return visitor.visitJcSwitchInst(this)
    }
}

interface JcExpr {
    val type: JcType
    val operands: List<JcValue>

    fun <T> accept(visitor: JcExprVisitor<T>): T
}

interface JcBinaryExpr : JcExpr {
    val lhv: JcValue
    val rhv: JcValue
}

data class JcAddExpr(
    override val type: JcType,
    override val lhv: JcValue,
    override val rhv: JcValue
) : JcBinaryExpr {
    override val operands: List<JcValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv + $rhv"

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcAddExpr(this)
    }
}

data class JcAndExpr(
    override val type: JcType,
    override val lhv: JcValue,
    override val rhv: JcValue
) : JcBinaryExpr {
    override val operands: List<JcValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv & $rhv"

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcAndExpr(this)
    }
}

data class JcCmpExpr(
    override val type: JcType,
    override val lhv: JcValue,
    override val rhv: JcValue
) : JcBinaryExpr {
    override val operands: List<JcValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv cmp $rhv"

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcCmpExpr(this)
    }
}

data class JcCmpgExpr(
    override val type: JcType,
    override val lhv: JcValue,
    override val rhv: JcValue
) : JcBinaryExpr {
    override val operands: List<JcValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv cmpg $rhv"

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcCmpgExpr(this)
    }
}

data class JcCmplExpr(
    override val type: JcType,
    override val lhv: JcValue,
    override val rhv: JcValue
) : JcBinaryExpr {
    override val operands: List<JcValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv cmpl $rhv"

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcCmplExpr(this)
    }
}

data class JcDivExpr(
    override val type: JcType,
    override val lhv: JcValue,
    override val rhv: JcValue
) : JcBinaryExpr {
    override val operands: List<JcValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv / $rhv"

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcDivExpr(this)
    }
}

data class JcMulExpr(
    override val type: JcType,
    override val lhv: JcValue,
    override val rhv: JcValue
) : JcBinaryExpr {
    override val operands: List<JcValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv * $rhv"

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcMulExpr(this)
    }
}

interface JcConditionExpr : JcBinaryExpr

data class JcEqExpr(
    override val type: JcType,
    override val lhv: JcValue,
    override val rhv: JcValue
) : JcConditionExpr {
    override val operands: List<JcValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv == $rhv"

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcEqExpr(this)
    }
}

data class JcNeqExpr(
    override val type: JcType,
    override val lhv: JcValue,
    override val rhv: JcValue
) : JcConditionExpr {
    override val operands: List<JcValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv != $rhv"

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcNeqExpr(this)
    }
}

data class JcGeExpr(
    override val type: JcType,
    override val lhv: JcValue,
    override val rhv: JcValue
) : JcConditionExpr {
    override val operands: List<JcValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv >= $rhv"

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcGeExpr(this)
    }
}

data class JcGtExpr(
    override val type: JcType,
    override val lhv: JcValue,
    override val rhv: JcValue
) : JcConditionExpr {
    override val operands: List<JcValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv > $rhv"

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcGtExpr(this)
    }
}

data class JcLeExpr(
    override val type: JcType,
    override val lhv: JcValue,
    override val rhv: JcValue
) : JcConditionExpr {
    override val operands: List<JcValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv <= $rhv"

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcLeExpr(this)
    }
}

data class JcLtExpr(
    override val type: JcType,
    override val lhv: JcValue,
    override val rhv: JcValue
) : JcConditionExpr {
    override val operands: List<JcValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv < $rhv"

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcLtExpr(this)
    }
}

data class JcOrExpr(
    override val type: JcType,
    override val lhv: JcValue,
    override val rhv: JcValue
) : JcBinaryExpr {
    override val operands: List<JcValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv | $rhv"

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcOrExpr(this)
    }
}

data class JcRemExpr(
    override val type: JcType,
    override val lhv: JcValue,
    override val rhv: JcValue
) : JcBinaryExpr {
    override val operands: List<JcValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv % $rhv"

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcRemExpr(this)
    }
}

data class JcShlExpr(
    override val type: JcType,
    override val lhv: JcValue,
    override val rhv: JcValue
) : JcBinaryExpr {
    override val operands: List<JcValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv << $rhv"

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcShlExpr(this)
    }
}

data class JcShrExpr(
    override val type: JcType,
    override val lhv: JcValue,
    override val rhv: JcValue
) : JcBinaryExpr {
    override val operands: List<JcValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv >> $rhv"

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcShrExpr(this)
    }
}

data class JcSubExpr(
    override val type: JcType,
    override val lhv: JcValue,
    override val rhv: JcValue
) : JcBinaryExpr {
    override val operands: List<JcValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv - $rhv"

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcSubExpr(this)
    }
}

data class JcUshrExpr(
    override val type: JcType,
    override val lhv: JcValue,
    override val rhv: JcValue
) : JcBinaryExpr {
    override val operands: List<JcValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv u<< $rhv"

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcUshrExpr(this)
    }
}

data class JcXorExpr(
    override val type: JcType,
    override val lhv: JcValue,
    override val rhv: JcValue
) : JcBinaryExpr {
    override val operands: List<JcValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv ^ $rhv"

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcXorExpr(this)
    }
}

data class JcLengthExpr(
    override val type: JcType,
    val array: JcValue
) : JcExpr {
    override val operands: List<JcValue>
        get() = listOf(array)

    override fun toString(): String = "$array.length"

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcLengthExpr(this)
    }
}

data class JcNegExpr(
    override val type: JcType,
    val operand: JcValue
) : JcExpr {
    override val operands: List<JcValue>
        get() = listOf(operand)

    override fun toString(): String = "-$operand"

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcNegExpr(this)
    }
}

data class JcCastExpr(
    override val type: JcType,
    val operand: JcValue
) : JcExpr {
    override val operands: List<JcValue>
        get() = listOf(operand)

    override fun toString(): String = "(${type.typeName}) $operand"

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcCastExpr(this)
    }
}

data class JcNewExpr(
    override val type: JcType
) : JcExpr {
    override val operands: List<JcValue>
        get() = emptyList()

    override fun toString(): String = "new ${type.typeName}"

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcNewExpr(this)
    }
}

data class JcNewArrayExpr(
    override val type: JcType,
    val dimensions: List<JcValue>
) : JcExpr {
    constructor(type: JcType, length: JcValue) : this(type, listOf(length))

    override val operands: List<JcValue>
        get() = dimensions

    override fun toString(): String = "new ${type.typeName}${dimensions.joinToString("") { "[$it]" }}"

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcNewArrayExpr(this)
    }
}

data class JcInstanceOfExpr(
    override val type: JcType,
    val operand: JcValue,
    val targetType: JcType
) : JcExpr {
    override val operands: List<JcValue>
        get() = listOf(operand)

    override fun toString(): String = "$operand instanceof $targetType"

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcInstanceOfExpr(this)
    }
}

interface JcCallExpr : JcExpr {
    val method: JcTypedMethod
    val args: List<JcValue>

    override val type get() = method.returnType

    override val operands: List<JcValue>
        get() = args
}

interface JcInstanceCallExpr : JcCallExpr {
    val instance: JcValue
}

data class JcPhiExpr(
    override val type: JcType,
    val values: List<JcValue>,
    val args: List<JcArgument>
) : JcExpr {

    override val operands: List<JcValue>
        get() = values

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcPhiExpr(this)
    }
}


/**
 * JcLambdaExpr is created when we can resolve the `invokedynamic` instruction.
 * When Java or Kotlin compiles a code with the lambda call, it generates
 * an `invokedynamic` instruction which returns a call cite object. When we can
 * resolve the lambda call, we create `JcLambdaExpr` that returns a similar call cite
 * object, but stores a reference to the actual method
 */
data class JcLambdaExpr(
    private val methodRef: TypedMethodRef,
    override val args: List<JcValue>,
) : JcCallExpr {

    override val method: JcTypedMethod get() = methodRef.method

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcLambdaExpr(this)
    }
}

data class JcDynamicCallExpr(
    private val bsmRef: TypedMethodRef,
    val bsmArgs: List<BsmArg>,
    val callCiteMethodName: String,
    val callCiteArgTypes: List<JcType>,
    val callCiteReturnType: JcType,
    val callCiteArgs: List<JcValue>
) : JcCallExpr {

    override val method get() = bsmRef.method
    override val args get() = callCiteArgs

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcDynamicCallExpr(this)
    }
}

/**
 * `invokevirtual` and `invokeinterface` instructions of the bytecode
 * are both represented with `JcVirtualCallExpr` for simplicity
 */
data class JcVirtualCallExpr(
    private val methodRef: TypedMethodRef,
    override val instance: JcValue,
    override val args: List<JcValue>,
) : JcInstanceCallExpr {

    override val method: JcTypedMethod get() = methodRef.method

    override val operands: List<JcValue>
        get() = listOf(instance) + args

    override fun toString(): String =
        "$instance.${methodRef.name}${args.joinToString(prefix = "(", postfix = ")", separator = ", ")}"

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcVirtualCallExpr(this)
    }
}


data class JcStaticCallExpr(
    private val methodRef: TypedMethodRef,
    override val args: List<JcValue>,
) : JcCallExpr {

    override val method: JcTypedMethod get() = methodRef.method

    override fun toString(): String =
        "${method.method.enclosingClass.name}.${methodRef.name}${
            args.joinToString(
                prefix = "(",
                postfix = ")",
                separator = ", "
            )
        }"

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcStaticCallExpr(this)
    }
}

data class JcSpecialCallExpr(
    private val methodRef: TypedMethodRef,
    override val instance: JcValue,
    override val args: List<JcValue>,
) : JcInstanceCallExpr {

    override val method: JcTypedMethod get() = methodRef.method

    override fun toString(): String =
        "$instance.${methodRef.name}${args.joinToString(prefix = "(", postfix = ")", separator = ", ")}"

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcSpecialCallExpr(this)
    }
}


interface JcValue : JcExpr {
    override val operands: List<JcValue>
        get() = emptyList()
}

interface JcSimpleValue : JcValue

data class JcThis(override val type: JcType) : JcLocal {

    override val name: String
        get() = "this"

    override fun toString(): String = "this"

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcThis(this)
    }
}

interface JcLocal : JcSimpleValue {
    val name: String
}

data class JcArgument(val index: Int, override val name: String, override val type: JcType) : JcLocal {

    companion object {
        @JvmStatic
        fun of(index: Int, name: String?, type: JcType): JcArgument {
            return JcArgument(index, name ?: "arg$$index", type)
        }
    }

    override fun toString(): String = name

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcArgument(this)
    }
}

data class JcLocalVar(override val name: String, override val type: JcType) : JcLocal {
    override fun toString(): String = name

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcLocalVar(this)
    }
}

interface JcComplexValue : JcValue

data class JcFieldRef(
    val instance: JcValue?,
    val field: JcTypedField
) : JcComplexValue {
    override val type: JcType get() = this.field.fieldType

    override fun toString(): String = "${instance ?: field.enclosingType.typeName}.${field.name}"

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcFieldRef(this)
    }
}

data class JcArrayAccess(
    val array: JcValue,
    val index: JcValue,
    override val type: JcType
) : JcComplexValue {
    override fun toString(): String = "$array[$index]"

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcArrayAccess(this)
    }
}

interface JcConstant : JcSimpleValue

interface JcNumericConstant : JcConstant {

    val value: Number

    fun isEqual(c: JcNumericConstant): Boolean = c.value == value

    fun isNotEqual(c: JcNumericConstant): Boolean = !isEqual(c)

    fun isLessThan(c: JcNumericConstant): Boolean

    fun isLessThanOrEqual(c: JcNumericConstant): Boolean = isLessThan(c) || isEqual(c)

    fun isGreaterThan(c: JcNumericConstant): Boolean

    fun isGreaterThanOrEqual(c: JcNumericConstant): Boolean = isGreaterThan(c) || isEqual(c)

    operator fun plus(c: JcNumericConstant): JcNumericConstant

    operator fun minus(c: JcNumericConstant): JcNumericConstant

    operator fun times(c: JcNumericConstant): JcNumericConstant

    operator fun div(c: JcNumericConstant): JcNumericConstant

    operator fun rem(c: JcNumericConstant): JcNumericConstant

    operator fun unaryMinus(): JcNumericConstant

}


data class JcBool(val value: Boolean, override val type: JcType) : JcConstant {
    override fun toString(): String = "$value"

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcBool(this)
    }
}

data class JcByte(override val value: Byte, override val type: JcType) : JcNumericConstant {
    override fun toString(): String = "$value"

    override fun plus(c: JcNumericConstant): JcNumericConstant {
        return JcInt(value + c.value.toByte(), type)
    }

    override fun minus(c: JcNumericConstant): JcNumericConstant {
        return JcInt(value.div(c.value.toByte()), type)
    }

    override fun times(c: JcNumericConstant): JcNumericConstant {
        return JcInt(value.times(c.value.toByte()), type)
    }

    override fun div(c: JcNumericConstant): JcNumericConstant {
        return JcInt(value.div(c.value.toByte()), type)
    }

    override fun rem(c: JcNumericConstant): JcNumericConstant {
        return JcInt(value.rem(c.value.toByte()), type)
    }

    override fun unaryMinus(): JcNumericConstant {
        return JcInt(-value, type)
    }

    override fun isLessThan(c: JcNumericConstant): Boolean {
        return value < c.value.toByte()
    }

    override fun isGreaterThan(c: JcNumericConstant): Boolean {
        return value > c.value.toByte()
    }

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcByte(this)
    }
}

data class JcChar(val value: Char, override val type: JcType) : JcConstant {
    override fun toString(): String = "$value"

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcChar(this)
    }
}

data class JcShort(override val value: Short, override val type: JcType) : JcNumericConstant {
    override fun toString(): String = "$value"

    override fun plus(c: JcNumericConstant): JcNumericConstant {
        return JcInt(value + c.value.toShort(), type)
    }

    override fun minus(c: JcNumericConstant): JcNumericConstant {
        return JcInt(value.div(c.value.toShort()), type)
    }

    override fun times(c: JcNumericConstant): JcNumericConstant {
        return JcInt(value * c.value.toInt(), type)
    }

    override fun div(c: JcNumericConstant): JcNumericConstant {
        return JcInt(value / c.value.toShort(), type)
    }

    override fun rem(c: JcNumericConstant): JcNumericConstant {
        return JcInt(value.rem(c.value.toShort()), type)
    }

    override fun unaryMinus(): JcNumericConstant {
        return JcInt(-value, type)
    }

    override fun isLessThan(c: JcNumericConstant): Boolean {
        return value < c.value.toShort()
    }

    override fun isGreaterThan(c: JcNumericConstant): Boolean {
        return value > c.value.toShort()
    }

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcShort(this)
    }
}

data class JcInt(override val value: Int, override val type: JcType) : JcNumericConstant {
    override fun toString(): String = "$value"

    override fun plus(c: JcNumericConstant): JcNumericConstant {
        return JcInt(value + c.value.toInt(), type)
    }

    override fun minus(c: JcNumericConstant): JcNumericConstant {
        return JcInt(value.div(c.value.toInt()), type)
    }

    override fun times(c: JcNumericConstant): JcNumericConstant {
        return JcInt(value * c.value.toInt(), type)
    }

    override fun div(c: JcNumericConstant): JcNumericConstant {
        return JcInt(value / c.value.toInt(), type)
    }

    override fun rem(c: JcNumericConstant): JcNumericConstant {
        return JcInt(value.rem(c.value.toInt()), type)
    }

    override fun unaryMinus(): JcNumericConstant {
        return JcInt(-value, type)
    }

    override fun isLessThan(c: JcNumericConstant): Boolean {
        return value < c.value.toInt()
    }

    override fun isGreaterThan(c: JcNumericConstant): Boolean {
        return value > c.value.toInt()
    }

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcInt(this)
    }
}

data class JcLong(override val value: Long, override val type: JcType) : JcNumericConstant {
    override fun toString(): String = "$value"

    override fun plus(c: JcNumericConstant): JcNumericConstant {
        return JcLong(value + c.value.toLong(), type)
    }

    override fun minus(c: JcNumericConstant): JcNumericConstant {
        return JcLong(value.div(c.value.toLong()), type)
    }

    override fun times(c: JcNumericConstant): JcNumericConstant {
        return JcLong(value * c.value.toLong(), type)
    }

    override fun div(c: JcNumericConstant): JcNumericConstant {
        return JcLong(value.div(c.value.toLong()), type)
    }

    override fun rem(c: JcNumericConstant): JcNumericConstant {
        return JcLong(value.rem(c.value.toLong()), type)
    }

    override fun unaryMinus(): JcNumericConstant {
        return JcLong(-value, type)
    }

    override fun isLessThan(c: JcNumericConstant): Boolean {
        return value < c.value.toLong()
    }

    override fun isGreaterThan(c: JcNumericConstant): Boolean {
        return value > c.value.toLong()
    }

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcLong(this)
    }
}

data class JcFloat(override val value: Float, override val type: JcType) : JcNumericConstant {
    override fun toString(): String = "$value"

    override fun plus(c: JcNumericConstant): JcNumericConstant {
        return JcFloat(value + c.value.toFloat(), type)
    }

    override fun minus(c: JcNumericConstant): JcNumericConstant {
        return JcFloat(value.div(c.value.toFloat()), type)
    }

    override fun times(c: JcNumericConstant): JcNumericConstant {
        return JcFloat(value * c.value.toFloat(), type)
    }

    override fun div(c: JcNumericConstant): JcNumericConstant {
        return JcFloat(value.div(c.value.toFloat()), type)
    }

    override fun rem(c: JcNumericConstant): JcNumericConstant {
        return JcFloat(value.rem(c.value.toFloat()), type)
    }

    override fun unaryMinus(): JcNumericConstant {
        return JcFloat(value.times(-1), type)
    }

    override fun isLessThan(c: JcNumericConstant): Boolean {
        return value < c.value.toFloat()
    }

    override fun isGreaterThan(c: JcNumericConstant): Boolean {
        return value > c.value.toFloat()
    }

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcFloat(this)
    }
}

data class JcDouble(override val value: Double, override val type: JcType) : JcNumericConstant {
    override fun toString(): String = "$value"

    override fun plus(c: JcNumericConstant): JcNumericConstant {
        return JcDouble(value + c.value.toDouble(), type)
    }

    override fun minus(c: JcNumericConstant): JcNumericConstant {
        return JcDouble(value.div(c.value.toDouble()), type)
    }

    override fun times(c: JcNumericConstant): JcNumericConstant {
        return JcDouble(value * c.value.toDouble(), type)
    }

    override fun div(c: JcNumericConstant): JcNumericConstant {
        return JcDouble(value.div(c.value.toDouble()), type)
    }

    override fun rem(c: JcNumericConstant): JcNumericConstant {
        return JcDouble(value.rem(c.value.toDouble()), type)
    }

    override fun unaryMinus(): JcNumericConstant {
        return JcDouble(-value, type)
    }

    override fun isLessThan(c: JcNumericConstant): Boolean {
        return value < c.value.toDouble()
    }

    override fun isGreaterThan(c: JcNumericConstant): Boolean {
        return value > c.value.toDouble()
    }

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcDouble(this)
    }
}

data class JcNullConstant(override val type: JcType) : JcConstant {
    override fun toString(): String = "null"

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcNullConstant(this)
    }
}

data class JcStringConstant(val value: String, override val type: JcType) : JcConstant {
    override fun toString(): String = "\"$value\""

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcStringConstant(this)
    }
}

/**
 * klass may be JcClassType or JcArrayType for constructions like byte[].class
 */
data class JcClassConstant(val klass: JcType, override val type: JcType) : JcConstant {

    override fun toString(): String = "${klass.typeName}.class"

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcClassConstant(this)
    }
}

data class JcMethodConstant(
    val method: JcTypedMethod,
    override val type: JcType
) : JcConstant {
    override fun toString(): String = "${method.method.enclosingClass.name}::${method.name}${
        method.parameters.joinToString(
            prefix = "(",
            postfix = ")",
            separator = ", "
        )
    }:${method.returnType}"

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcMethodConstant(this)
    }
}