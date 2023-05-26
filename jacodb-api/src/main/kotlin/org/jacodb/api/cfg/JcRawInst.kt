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
import org.jacodb.api.TypeName

sealed interface JcRawInst {
    val owner: JcMethod

    val operands: List<JcRawExpr>

    fun <T> accept(visitor: JcRawInstVisitor<T>): T
}

class JcRawAssignInst(
    override val owner: JcMethod,
    val lhv: JcRawValue,
    val rhv: JcRawExpr
) : JcRawInst {

    override val operands: List<JcRawExpr>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv = $rhv"

    override fun <T> accept(visitor: JcRawInstVisitor<T>): T {
        return visitor.visitJcRawAssignInst(this)
    }
}

class JcRawEnterMonitorInst(
    override val owner: JcMethod,
    val monitor: JcRawSimpleValue
) : JcRawInst {
    override val operands: List<JcRawExpr>
        get() = listOf(monitor)

    override fun toString(): String = "enter monitor $monitor"

    override fun <T> accept(visitor: JcRawInstVisitor<T>): T {
        return visitor.visitJcRawEnterMonitorInst(this)
    }
}

class JcRawExitMonitorInst(
    override val owner: JcMethod,
    val monitor: JcRawSimpleValue
) : JcRawInst {
    override val operands: List<JcRawExpr>
        get() = listOf(monitor)

    override fun toString(): String = "exit monitor $monitor"

    override fun <T> accept(visitor: JcRawInstVisitor<T>): T {
        return visitor.visitJcRawExitMonitorInst(this)
    }
}

class JcRawCallInst(
    override val owner: JcMethod,
    val callExpr: JcRawCallExpr
) : JcRawInst {
    override val operands: List<JcRawExpr>
        get() = listOf(callExpr)

    override fun toString(): String = "$callExpr"

    override fun <T> accept(visitor: JcRawInstVisitor<T>): T {
        return visitor.visitJcRawCallInst(this)
    }
}

data class JcRawLabelRef(val name: String) {
    override fun toString() = name
}

class JcRawLineNumberInst(override val owner: JcMethod, val lineNumber: Int, val start: JcRawLabelRef) : JcRawInst {

    override val operands: List<JcRawExpr>
        get() = emptyList()

    override fun toString(): String = "line number $lineNumber:"

    override fun <T> accept(visitor: JcRawInstVisitor<T>): T {
        return visitor.visitJcRawLineNumberInst(this)
    }
}


class JcRawLabelInst(
    override val owner: JcMethod,
    val name: String
) : JcRawInst {
    override val operands: List<JcRawExpr>
        get() = emptyList()

    val ref get() = JcRawLabelRef(name)

    override fun toString(): String = "label $name:"

    override fun <T> accept(visitor: JcRawInstVisitor<T>): T {
        return visitor.visitJcRawLabelInst(this)
    }
}

class JcRawReturnInst(
    override val owner: JcMethod,
    val returnValue: JcRawValue?
) : JcRawInst {
    override val operands: List<JcRawExpr>
        get() = listOfNotNull(returnValue)

    override fun toString(): String = "return" + (returnValue?.let { " $it" } ?: "")

    override fun <T> accept(visitor: JcRawInstVisitor<T>): T {
        return visitor.visitJcRawReturnInst(this)
    }
}

class JcRawThrowInst(
    override val owner: JcMethod,
    val throwable: JcRawValue
) : JcRawInst {
    override val operands: List<JcRawExpr>
        get() = listOf(throwable)

    override fun toString(): String = "throw $throwable"

    override fun <T> accept(visitor: JcRawInstVisitor<T>): T {
        return visitor.visitJcRawThrowInst(this)
    }
}

data class JcRawCatchEntry(
    val acceptedThrowable: TypeName,
    val startInclusive: JcRawLabelRef,
    val endExclusive: JcRawLabelRef
)

class JcRawCatchInst(
    override val owner: JcMethod,
    val throwable: JcRawValue,
    val handler: JcRawLabelRef,
    val entries: List<JcRawCatchEntry>,
) : JcRawInst {
    override val operands: List<JcRawExpr>
        get() = listOf(throwable)

    override fun toString(): String = "catch ($throwable: ${throwable.typeName})"

    override fun <T> accept(visitor: JcRawInstVisitor<T>): T {
        return visitor.visitJcRawCatchInst(this)
    }
}

sealed interface JcRawBranchingInst : JcRawInst {
    val successors: List<JcRawLabelRef>
}

class JcRawGotoInst(
    override val owner: JcMethod,
    val target: JcRawLabelRef
) : JcRawBranchingInst {
    override val operands: List<JcRawExpr>
        get() = emptyList()

    override val successors: List<JcRawLabelRef>
        get() = listOf(target)

    override fun toString(): String = "goto $target"

    override fun <T> accept(visitor: JcRawInstVisitor<T>): T {
        return visitor.visitJcRawGotoInst(this)
    }
}

data class JcRawIfInst(
    override val owner: JcMethod,
    val condition: JcRawConditionExpr,
    val trueBranch: JcRawLabelRef,
    val falseBranch: JcRawLabelRef
) : JcRawBranchingInst {
    override val operands: List<JcRawExpr>
        get() = listOf(condition)

    override val successors: List<JcRawLabelRef>
        get() = listOf(trueBranch, falseBranch)

    override fun toString(): String = "if ($condition) goto $trueBranch else $falseBranch"

    override fun <T> accept(visitor: JcRawInstVisitor<T>): T {
        return visitor.visitJcRawIfInst(this)
    }
}

data class JcRawSwitchInst(
    override val owner: JcMethod,
    val key: JcRawValue,
    val branches: Map<JcRawValue, JcRawLabelRef>,
    val default: JcRawLabelRef
) : JcRawBranchingInst {
    override val operands: List<JcRawExpr>
        get() = listOf(key) + branches.keys

    override val successors: List<JcRawLabelRef>
        get() = branches.values + default

    override fun toString(): String = buildString {
        append("switch ($key) { ")
        branches.forEach { (option, label) -> append("$option -> $label") }
        append("else -> ${default.name} }")
    }

    override fun <T> accept(visitor: JcRawInstVisitor<T>): T {
        return visitor.visitJcRawSwitchInst(this)
    }
}

sealed interface JcRawExpr {
    val typeName: TypeName
    val operands: List<JcRawValue>

    fun <T> accept(visitor: JcRawExprVisitor<T>): T
}

interface JcRawBinaryExpr : JcRawExpr {
    val lhv: JcRawValue
    val rhv: JcRawValue
}

data class JcRawAddExpr(
    override val typeName: TypeName,
    override val lhv: JcRawValue,
    override val rhv: JcRawValue
) : JcRawBinaryExpr {
    override val operands: List<JcRawValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv + $rhv"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawAddExpr(this)
    }
}

data class JcRawAndExpr(
    override val typeName: TypeName,
    override val lhv: JcRawValue,
    override val rhv: JcRawValue
) : JcRawBinaryExpr {
    override val operands: List<JcRawValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv & $rhv"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawAndExpr(this)
    }
}

data class JcRawCmpExpr(
    override val typeName: TypeName,
    override val lhv: JcRawValue,
    override val rhv: JcRawValue
) : JcRawBinaryExpr {
    override val operands: List<JcRawValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv cmp $rhv"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawCmpExpr(this)
    }
}

data class JcRawCmpgExpr(
    override val typeName: TypeName,
    override val lhv: JcRawValue,
    override val rhv: JcRawValue
) : JcRawBinaryExpr {
    override val operands: List<JcRawValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv cmpg $rhv"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawCmpgExpr(this)
    }
}

data class JcRawCmplExpr(
    override val typeName: TypeName,
    override val lhv: JcRawValue,
    override val rhv: JcRawValue
) : JcRawBinaryExpr {
    override val operands: List<JcRawValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv cmpl $rhv"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawCmplExpr(this)
    }
}

data class JcRawDivExpr(
    override val typeName: TypeName,
    override val lhv: JcRawValue,
    override val rhv: JcRawValue
) : JcRawBinaryExpr {
    override val operands: List<JcRawValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv / $rhv"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawDivExpr(this)
    }
}

data class JcRawMulExpr(
    override val typeName: TypeName,
    override val lhv: JcRawValue,
    override val rhv: JcRawValue
) : JcRawBinaryExpr {
    override val operands: List<JcRawValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv * $rhv"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawMulExpr(this)
    }
}

sealed interface JcRawConditionExpr : JcRawBinaryExpr

data class JcRawEqExpr(
    override val typeName: TypeName,
    override val lhv: JcRawValue,
    override val rhv: JcRawValue
) : JcRawConditionExpr {
    override val operands: List<JcRawValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv == $rhv"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawEqExpr(this)
    }
}

data class JcRawNeqExpr(
    override val typeName: TypeName,
    override val lhv: JcRawValue,
    override val rhv: JcRawValue
) : JcRawConditionExpr {
    override val operands: List<JcRawValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv != $rhv"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawNeqExpr(this)
    }
}

data class JcRawGeExpr(
    override val typeName: TypeName,
    override val lhv: JcRawValue,
    override val rhv: JcRawValue
) : JcRawConditionExpr {
    override val operands: List<JcRawValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv >= $rhv"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawGeExpr(this)
    }
}

data class JcRawGtExpr(
    override val typeName: TypeName,
    override val lhv: JcRawValue,
    override val rhv: JcRawValue
) : JcRawConditionExpr {
    override val operands: List<JcRawValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv > $rhv"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawGtExpr(this)
    }
}

data class JcRawLeExpr(
    override val typeName: TypeName,
    override val lhv: JcRawValue,
    override val rhv: JcRawValue
) : JcRawConditionExpr {
    override val operands: List<JcRawValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv <= $rhv"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawLeExpr(this)
    }
}

data class JcRawLtExpr(
    override val typeName: TypeName,
    override val lhv: JcRawValue,
    override val rhv: JcRawValue
) : JcRawConditionExpr {
    override val operands: List<JcRawValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv < $rhv"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawLtExpr(this)
    }
}

data class JcRawOrExpr(
    override val typeName: TypeName,
    override val lhv: JcRawValue,
    override val rhv: JcRawValue
) : JcRawBinaryExpr {
    override val operands: List<JcRawValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv | $rhv"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawOrExpr(this)
    }
}

data class JcRawRemExpr(
    override val typeName: TypeName,
    override val lhv: JcRawValue,
    override val rhv: JcRawValue
) : JcRawBinaryExpr {
    override val operands: List<JcRawValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv % $rhv"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawRemExpr(this)
    }
}

data class JcRawShlExpr(
    override val typeName: TypeName,
    override val lhv: JcRawValue,
    override val rhv: JcRawValue
) : JcRawBinaryExpr {
    override val operands: List<JcRawValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv << $rhv"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawShlExpr(this)
    }
}

data class JcRawShrExpr(
    override val typeName: TypeName,
    override val lhv: JcRawValue,
    override val rhv: JcRawValue
) : JcRawBinaryExpr {
    override val operands: List<JcRawValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv >> $rhv"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawShrExpr(this)
    }
}

data class JcRawSubExpr(
    override val typeName: TypeName,
    override val lhv: JcRawValue,
    override val rhv: JcRawValue
) : JcRawBinaryExpr {
    override val operands: List<JcRawValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv - $rhv"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawSubExpr(this)
    }
}

data class JcRawUshrExpr(
    override val typeName: TypeName,
    override val lhv: JcRawValue,
    override val rhv: JcRawValue
) : JcRawBinaryExpr {
    override val operands: List<JcRawValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv u<< $rhv"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawUshrExpr(this)
    }
}

data class JcRawXorExpr(
    override val typeName: TypeName,
    override val lhv: JcRawValue,
    override val rhv: JcRawValue
) : JcRawBinaryExpr {
    override val operands: List<JcRawValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv ^ $rhv"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawXorExpr(this)
    }
}

data class JcRawLengthExpr(
    override val typeName: TypeName,
    val array: JcRawValue
) : JcRawExpr {
    override val operands: List<JcRawValue>
        get() = listOf(array)

    override fun toString(): String = "$array.length"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawLengthExpr(this)
    }
}

data class JcRawNegExpr(
    override val typeName: TypeName,
    val operand: JcRawValue
) : JcRawExpr {
    override val operands: List<JcRawValue>
        get() = listOf(operand)

    override fun toString(): String = "-$operand"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawNegExpr(this)
    }
}

data class JcRawCastExpr(
    override val typeName: TypeName,
    val operand: JcRawValue
) : JcRawExpr {
    override val operands: List<JcRawValue>
        get() = listOf(operand)

    override fun toString(): String = "($typeName) $operand"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawCastExpr(this)
    }
}

data class JcRawNewExpr(
    override val typeName: TypeName
) : JcRawExpr {
    override val operands: List<JcRawValue>
        get() = emptyList()

    override fun toString(): String = "new $typeName"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawNewExpr(this)
    }
}

data class JcRawNewArrayExpr(
    override val typeName: TypeName,
    val dimensions: List<JcRawValue>
) : JcRawExpr {
    constructor(typeName: TypeName, length: JcRawValue) : this(typeName, listOf(length))

    override val operands: List<JcRawValue>
        get() = dimensions

    override fun toString(): String = "new $typeName${dimensions.joinToString("") { "[$it]" }}"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawNewArrayExpr(this)
    }
}

data class JcRawInstanceOfExpr(
    override val typeName: TypeName,
    val operand: JcRawValue,
    val targetType: TypeName
) : JcRawExpr {
    override val operands: List<JcRawValue>
        get() = listOf(operand)

    override fun toString(): String = "$operand instanceof ${targetType.typeName}"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawInstanceOfExpr(this)
    }
}

sealed interface JcRawCallExpr : JcRawExpr {
    val declaringClass: TypeName
    val methodName: String
    val argumentTypes: List<TypeName>
    val returnType: TypeName
    val args: List<JcRawValue>

    override val typeName get() = returnType

    override val operands: List<JcRawValue>
        get() = args
}

sealed interface JcRawInstanceExpr: JcRawCallExpr {
    val instance: JcRawValue
}

sealed interface BsmArg

data class BsmIntArg(val value: Int) : BsmArg {
    override fun toString(): String = value.toString()
}

data class BsmFloatArg(val value: Float) : BsmArg {
    override fun toString(): String = value.toString()
}

data class BsmLongArg(val value: Long) : BsmArg {
    override fun toString(): String = value.toString()
}

data class BsmDoubleArg(val value: Double) : BsmArg {
    override fun toString(): String = value.toString()
}

data class BsmStringArg(val value: String) : BsmArg {
    override fun toString(): String = "\"$value\""
}

data class BsmTypeArg(val typeName: TypeName) : BsmArg {
    override fun toString(): String = typeName.typeName
}

data class BsmMethodTypeArg(val argumentTypes: List<TypeName>, val returnType: TypeName) : BsmArg {
    override fun toString(): String = "(${argumentTypes.joinToString { it.typeName }}:${returnType.typeName})"
}

data class BsmHandle(
    val tag: Int,
    val declaringClass: TypeName,
    val name: String,
    val argTypes: List<TypeName>,
    val returnType: TypeName,
    val isInterface: Boolean,
) : BsmArg

data class JcRawDynamicCallExpr(
    val bsm: BsmHandle,
    val bsmArgs: List<BsmArg>,
    val callSiteMethodName: String,
    val callSiteArgTypes: List<TypeName>,
    val callSiteReturnType: TypeName,
    val callSiteArgs: List<JcRawValue>
) : JcRawCallExpr {
    override val declaringClass get() = bsm.declaringClass
    override val methodName get() = bsm.name
    override val argumentTypes get() = bsm.argTypes
    override val returnType get() = bsm.returnType
    override val typeName get() = returnType
    override val args get() = callSiteArgs

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawDynamicCallExpr(this)
    }
}

data class JcRawVirtualCallExpr(
    override val declaringClass: TypeName,
    override val methodName: String,
    override val argumentTypes: List<TypeName>,
    override val returnType: TypeName,
    override val instance: JcRawValue,
    override val args: List<JcRawValue>,
) : JcRawInstanceExpr {
    override val operands: List<JcRawValue>
        get() = listOf(instance) + args

    override fun toString(): String =
        "$instance.$methodName${args.joinToString(prefix = "(", postfix = ")", separator = ", ")}"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawVirtualCallExpr(this)
    }
}

data class JcRawInterfaceCallExpr(
    override val declaringClass: TypeName,
    override val methodName: String,
    override val argumentTypes: List<TypeName>,
    override val returnType: TypeName,
    override val instance: JcRawValue,
    override val args: List<JcRawValue>,
) : JcRawInstanceExpr {
    override val operands: List<JcRawValue>
        get() = listOf(instance) + args

    override fun toString(): String =
        "$instance.$methodName${args.joinToString(prefix = "(", postfix = ")", separator = ", ")}"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawInterfaceCallExpr(this)
    }
}

data class JcRawStaticCallExpr(
    override val declaringClass: TypeName,
    override val methodName: String,
    override val argumentTypes: List<TypeName>,
    override val returnType: TypeName,
    override val args: List<JcRawValue>,
) : JcRawCallExpr {
    override fun toString(): String =
        "$declaringClass.$methodName${args.joinToString(prefix = "(", postfix = ")", separator = ", ")}"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawStaticCallExpr(this)
    }
}

data class JcRawSpecialCallExpr(
    override val declaringClass: TypeName,
    override val methodName: String,
    override val argumentTypes: List<TypeName>,
    override val returnType: TypeName,
    override val instance: JcRawValue,
    override val args: List<JcRawValue>,
) : JcRawInstanceExpr {
    override fun toString(): String =
        "$instance.$methodName${args.joinToString(prefix = "(", postfix = ")", separator = ", ")}"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawSpecialCallExpr(this)
    }
}


sealed interface JcRawValue : JcRawExpr {
    override val operands: List<JcRawValue>
        get() = emptyList()
}

sealed interface JcRawSimpleValue : JcRawValue

sealed interface JcRawLocal : JcRawSimpleValue {
    val name: String
}

data class JcRawThis(override val typeName: TypeName) : JcRawSimpleValue {
    override fun toString(): String = "this"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawThis(this)
    }
}

data class JcRawArgument(val index: Int, override val name: String, override val typeName: TypeName) : JcRawLocal {
    companion object {
        @JvmStatic
        fun of(index: Int, name: String?, typeName: TypeName): JcRawArgument {
            return JcRawArgument(index, name ?: "arg$$index", typeName)
        }
    }


    override fun toString(): String = name

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawArgument(this)
    }
}

data class JcRawLocalVar(override val name: String, override val typeName: TypeName) : JcRawLocal {
    override fun toString(): String = name

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawLocalVar(this)
    }
}

sealed interface JcRawComplexValue : JcRawValue

data class JcRawFieldRef(
    val instance: JcRawValue?,
    val declaringClass: TypeName,
    val fieldName: String,
    override val typeName: TypeName
) : JcRawComplexValue {
    constructor(declaringClass: TypeName, fieldName: String, typeName: TypeName) : this(
        null,
        declaringClass,
        fieldName,
        typeName
    )

    override fun toString(): String = "${instance ?: declaringClass}.$fieldName"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawFieldRef(this)
    }
}

data class JcRawArrayAccess(
    val array: JcRawValue,
    val index: JcRawValue,
    override val typeName: TypeName
) : JcRawComplexValue {
    override fun toString(): String = "$array[$index]"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawArrayAccess(this)
    }
}

sealed interface JcRawConstant : JcRawSimpleValue

data class JcRawBool(val value: Boolean, override val typeName: TypeName) : JcRawConstant {
    override fun toString(): String = "$value"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawBool(this)
    }
}

data class JcRawByte(val value: Byte, override val typeName: TypeName) : JcRawConstant {
    override fun toString(): String = "$value"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawByte(this)
    }
}

data class JcRawChar(val value: Char, override val typeName: TypeName) : JcRawConstant {
    override fun toString(): String = "$value"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawChar(this)
    }
}

data class JcRawShort(val value: Short, override val typeName: TypeName) : JcRawConstant {
    override fun toString(): String = "$value"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawShort(this)
    }
}

data class JcRawInt(val value: Int, override val typeName: TypeName) : JcRawConstant {
    override fun toString(): String = "$value"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawInt(this)
    }
}

data class JcRawLong(val value: Long, override val typeName: TypeName) : JcRawConstant {
    override fun toString(): String = "$value"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawLong(this)
    }
}

data class JcRawFloat(val value: Float, override val typeName: TypeName) : JcRawConstant {
    override fun toString(): String = "$value"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawFloat(this)
    }
}

data class JcRawDouble(val value: Double, override val typeName: TypeName) : JcRawConstant {
    override fun toString(): String = "$value"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawDouble(this)
    }
}

data class JcRawNullConstant(override val typeName: TypeName) : JcRawConstant {
    override fun toString(): String = "null"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawNullConstant(this)
    }
}

data class JcRawStringConstant(val value: String, override val typeName: TypeName) : JcRawConstant {
    override fun toString(): String = "\"$value\""

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawStringConstant(this)
    }
}

data class JcRawClassConstant(val className: TypeName, override val typeName: TypeName) : JcRawConstant {
    override fun toString(): String = "$className.class"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawClassConstant(this)
    }
}

data class JcRawMethodConstant(
    val declaringClass: TypeName,
    val name: String,
    val argumentTypes: List<TypeName>,
    val returnType: TypeName,
    override val typeName: TypeName
) : JcRawConstant {
    override fun toString(): String =
        "$declaringClass.$name${argumentTypes.joinToString(prefix = "(", postfix = ")")}:$returnType"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawMethodConstant(this)
    }
}

//
//fun JcRawInstList.lineNumberOf(inst: JcRawInst): Int? {
//    val idx: Int = instructions.indexOf(inst)
//    assert(idx != -1)
//
//    // Get index of labels and insnNode within method
//    val insnIt: ListIterator<AbstractInsnNode> = insnList.iterator(idx)
//    while (insnIt.hasPrevious()) {
//        val node: AbstractInsnNode = insnIt.previous()
//        if (node is LineNumberNode) {
//            return node as LineNumberNode
//        }
//    }
//    return null
//}