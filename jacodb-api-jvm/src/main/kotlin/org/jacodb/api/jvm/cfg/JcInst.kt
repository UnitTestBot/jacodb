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

package org.jacodb.api.jvm.cfg

import org.jacodb.api.common.CommonClassField
import org.jacodb.api.common.CommonMethod
import org.jacodb.api.common.cfg.CommonArgument
import org.jacodb.api.common.cfg.CommonArrayAccess
import org.jacodb.api.common.cfg.CommonAssignInst
import org.jacodb.api.common.cfg.CommonCallExpr
import org.jacodb.api.common.cfg.CommonCallInst
import org.jacodb.api.common.cfg.CommonExpr
import org.jacodb.api.common.cfg.CommonFieldRef
import org.jacodb.api.common.cfg.CommonGotoInst
import org.jacodb.api.common.cfg.CommonIfInst
import org.jacodb.api.common.cfg.CommonInst
import org.jacodb.api.common.cfg.CommonInstLocation
import org.jacodb.api.common.cfg.CommonInstanceCallExpr
import org.jacodb.api.common.cfg.CommonReturnInst
import org.jacodb.api.common.cfg.CommonThis
import org.jacodb.api.common.cfg.CommonValue
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.JcTypedField
import org.jacodb.api.jvm.JcTypedMethod

interface TypedMethodRef {
    val name: String
    val method: JcTypedMethod
}

interface VirtualTypedMethodRef : TypedMethodRef {
    val declaredMethod: JcTypedMethod
}

interface JcInstLocation : CommonInstLocation<JcMethod, JcInst> {
    override val method: JcMethod
    override val index: Int
    override val lineNumber: Int
}

interface JcInst : CommonInst<JcMethod, JcInst> {
    override val location: JcInstLocation
    override val operands: List<JcExpr>

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
    val index: Int,
)

class JcAssignInst(
    location: JcInstLocation,
    override val lhv: JcValue,
    override val rhv: JcExpr,
) : AbstractJcInst(location), CommonAssignInst<JcMethod, JcInst> {
    override val operands: List<JcExpr>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv = $rhv"

    override fun <T> accept(visitor: JcInstVisitor<T>): T {
        return visitor.visitJcAssignInst(this)
    }
}

class JcEnterMonitorInst(
    location: JcInstLocation,
    val monitor: JcValue,
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
    val monitor: JcValue,
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
    val callExpr: JcCallExpr,
) : AbstractJcInst(location), CommonCallInst<JcMethod, JcInst> {
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
    override val returnValue: JcValue?,
) : AbstractJcInst(location), JcTerminatingInst, CommonReturnInst<JcMethod, JcInst> {
    override val operands: List<JcExpr>
        get() = listOfNotNull(returnValue)

    override fun toString(): String = "return" + (returnValue?.let { " $it" } ?: "")

    override fun <T> accept(visitor: JcInstVisitor<T>): T {
        return visitor.visitJcReturnInst(this)
    }
}

class JcThrowInst(
    location: JcInstLocation,
    val throwable: JcValue,
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
    val throwers: List<JcInstRef>,
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
    val target: JcInstRef,
) : AbstractJcInst(location), JcBranchingInst, CommonGotoInst<JcMethod, JcInst> {
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
    val falseBranch: JcInstRef,
) : AbstractJcInst(location), JcBranchingInst, CommonIfInst<JcMethod, JcInst> {
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
    val default: JcInstRef,
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

interface JcExpr : CommonExpr {
    override val type: JcType
    override val operands: List<JcValue>

    fun <T> accept(visitor: JcExprVisitor<T>): T
}

interface JcBinaryExpr : JcExpr {
    val lhv: JcValue
    val rhv: JcValue
}

data class JcAddExpr(
    override val type: JcType,
    override val lhv: JcValue,
    override val rhv: JcValue,
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
    override val rhv: JcValue,
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
    override val rhv: JcValue,
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
    override val rhv: JcValue,
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
    override val rhv: JcValue,
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
    override val rhv: JcValue,
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
    override val rhv: JcValue,
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
    override val rhv: JcValue,
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
    override val rhv: JcValue,
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
    override val rhv: JcValue,
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
    override val rhv: JcValue,
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
    override val rhv: JcValue,
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
    override val rhv: JcValue,
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
    override val rhv: JcValue,
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
    override val rhv: JcValue,
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
    override val rhv: JcValue,
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
    override val rhv: JcValue,
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
    override val rhv: JcValue,
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
    override val rhv: JcValue,
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
    override val rhv: JcValue,
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
    val array: JcValue,
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
    val operand: JcValue,
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
    val operand: JcValue,
) : JcExpr {
    override val operands: List<JcValue>
        get() = listOf(operand)

    override fun toString(): String = "(${type.typeName}) $operand"

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcCastExpr(this)
    }
}

data class JcNewExpr(
    override val type: JcType,
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
    val dimensions: List<JcValue>,
) : JcExpr {

    override val operands: List<JcValue>
        get() = dimensions

    override fun toString(): String = "new ${arrayTypeToStringWithDimensions(type.typeName, dimensions)}"

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcNewArrayExpr(this)
    }

    companion object {
        private val regexToProcessDimensions = Regex("\\[(.*?)]")

        private fun arrayTypeToStringWithDimensions(typeName: String, dimensions: List<JcValue>): String {
            var curDim = 0
            return regexToProcessDimensions.replace(typeName) {
                "[${dimensions.getOrNull(curDim++) ?: ""}]"
            }
        }
    }
}

data class JcInstanceOfExpr(
    override val type: JcType,
    val operand: JcValue,
    val targetType: JcType,
) : JcExpr {
    override val operands: List<JcValue>
        get() = listOf(operand)

    override fun toString(): String = "$operand instanceof $targetType"

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcInstanceOfExpr(this)
    }
}

interface JcCallExpr : JcExpr, CommonCallExpr {
    val method: JcTypedMethod
    override val args: List<JcValue>

    override val callee: JcMethod
        get() = method.method

    override val type: JcType
        get() = method.returnType

    override val operands: List<JcValue>
        get() = args
}

interface JcInstanceCallExpr : JcCallExpr, CommonInstanceCallExpr {
    override val instance: JcValue
    val declaredMethod: JcTypedMethod

    override val operands: List<JcValue>
        get() = listOf(instance) + args
}

data class JcPhiExpr(
    override val type: JcType,
    val values: List<JcValue>,
    val args: List<JcArgument>,
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
    private val bsmRef: TypedMethodRef,
    val actualMethod: TypedMethodRef,
    val interfaceMethodType: BsmMethodTypeArg,
    val dynamicMethodType: BsmMethodTypeArg,
    val callSiteMethodName: String,
    val callSiteArgTypes: List<JcType>,
    val callSiteReturnType: JcType,
    val callSiteArgs: List<JcValue>,
) : JcCallExpr {

    override val method get() = bsmRef.method
    override val args get() = callSiteArgs

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcLambdaExpr(this)
    }
}

data class JcDynamicCallExpr(
    private val bsmRef: TypedMethodRef,
    val bsmArgs: List<BsmArg>,
    val callSiteMethodName: String,
    val callSiteArgTypes: List<JcType>,
    val callSiteReturnType: JcType,
    val callSiteArgs: List<JcValue>,
) : JcCallExpr {

    override val method get() = bsmRef.method
    override val args get() = callSiteArgs

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcDynamicCallExpr(this)
    }
}

/**
 * `invokevirtual` and `invokeinterface` instructions of the bytecode
 * are both represented with `JcVirtualCallExpr` for simplicity
 */
data class JcVirtualCallExpr(
    private val methodRef: VirtualTypedMethodRef,
    override val instance: JcValue,
    override val args: List<JcValue>,
) : JcInstanceCallExpr {

    override val method: JcTypedMethod
        get() {
            return methodRef.method
        }

    override val declaredMethod: JcTypedMethod
        get() {
            return methodRef.declaredMethod
        }

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

    override val declaredMethod: JcTypedMethod
        get() = method

    override fun toString(): String =
        "$instance.${methodRef.name}${args.joinToString(prefix = "(", postfix = ")", separator = ", ")}"

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcSpecialCallExpr(this)
    }
}

interface JcValue : JcExpr, CommonValue

interface JcSimpleValue : JcValue {
    override val operands: List<JcValue>
        get() = emptyList()
}

data class JcThis(override val type: JcType) : JcLocal, CommonThis {
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

/**
 * @param name isn't considered in `equals` and `hashcode`
 */
data class JcArgument(
    override val index: Int,
    override val name: String,
    override val type: JcType,
) : JcLocal, CommonArgument {

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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as JcArgument

        if (index != other.index) return false
        if (type != other.type) return false

        return true
    }

    override fun hashCode(): Int {
        var result = index
        result = 31 * result + type.hashCode()
        return result
    }
}

/**
 * @param name isn't considered in `equals` and `hashcode`
 */
data class JcLocalVar(
    val index: Int,
    override val name: String,
    override val type: JcType,
) : JcLocal {

    override fun toString(): String = name

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcLocalVar(this)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as JcLocalVar

        if (index != other.index) return false
        if (type != other.type) return false

        return true
    }

    override fun hashCode(): Int {
        var result = index
        result = 31 * result + type.hashCode()
        return result
    }
}

interface JcComplexValue : JcValue

data class JcFieldRef(
    override val instance: JcValue?,
    val field: JcTypedField,
) : JcComplexValue, CommonFieldRef {

    override val type: JcType
        get() = this.field.type

    override val operands: List<JcValue>
        get() = instance?.let { listOf(it) }.orEmpty()

    override val classField: CommonClassField
        get() = this.field.field

    override fun toString(): String = "${instance ?: field.enclosingType.typeName}.${field.name}"

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcFieldRef(this)
    }
}

data class JcArrayAccess(
    override val array: JcValue,
    override val index: JcValue,
    override val type: JcType,
) : JcComplexValue, CommonArrayAccess {

    override val operands: List<JcValue>
        get() = listOf(array, index)

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
        return JcInt(value - c.value.toByte(), type)
    }

    override fun times(c: JcNumericConstant): JcNumericConstant {
        return JcInt(value * c.value.toByte(), type)
    }

    override fun div(c: JcNumericConstant): JcNumericConstant {
        return JcInt(value / c.value.toByte(), type)
    }

    override fun rem(c: JcNumericConstant): JcNumericConstant {
        return JcInt(value % c.value.toByte(), type)
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
        return JcInt(value - c.value.toShort(), type)
    }

    override fun times(c: JcNumericConstant): JcNumericConstant {
        return JcInt(value * c.value.toInt(), type)
    }

    override fun div(c: JcNumericConstant): JcNumericConstant {
        return JcInt(value / c.value.toShort(), type)
    }

    override fun rem(c: JcNumericConstant): JcNumericConstant {
        return JcInt(value % c.value.toShort(), type)
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
        return JcInt(value - c.value.toInt(), type)
    }

    override fun times(c: JcNumericConstant): JcNumericConstant {
        return JcInt(value * c.value.toInt(), type)
    }

    override fun div(c: JcNumericConstant): JcNumericConstant {
        return JcInt(value / c.value.toInt(), type)
    }

    override fun rem(c: JcNumericConstant): JcNumericConstant {
        return JcInt(value % c.value.toInt(), type)
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
        return JcLong(value - c.value.toLong(), type)
    }

    override fun times(c: JcNumericConstant): JcNumericConstant {
        return JcLong(value * c.value.toLong(), type)
    }

    override fun div(c: JcNumericConstant): JcNumericConstant {
        return JcLong(value / c.value.toLong(), type)
    }

    override fun rem(c: JcNumericConstant): JcNumericConstant {
        return JcLong(value % c.value.toLong(), type)
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
        return JcFloat(value - c.value.toFloat(), type)
    }

    override fun times(c: JcNumericConstant): JcNumericConstant {
        return JcFloat(value * c.value.toFloat(), type)
    }

    override fun div(c: JcNumericConstant): JcNumericConstant {
        return JcFloat(value / c.value.toFloat(), type)
    }

    override fun rem(c: JcNumericConstant): JcNumericConstant {
        return JcFloat(value % c.value.toFloat(), type)
    }

    override fun unaryMinus(): JcNumericConstant {
        return JcFloat(-value, type)
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
    override val type: JcType,
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

data class JcMethodType(
    val argumentTypes: List<JcType>,
    val returnType: JcType,
    override val type: JcType,
) : JcConstant {
    override fun toString(): String = "${
        argumentTypes.joinToString(
            prefix = "(",
            postfix = ")",
            separator = ", "
        )
    }:${returnType}"

    override fun <T> accept(visitor: JcExprVisitor<T>): T {
        return visitor.visitJcMethodType(this)
    }
}
