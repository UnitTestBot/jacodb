package org.utbot.jcdb.api

import org.utbot.jcdb.api.cfg.JcRawExprVisitor
import org.utbot.jcdb.api.cfg.JcRawInstVisitor

private const val offset = "  "

class JcRawInstList(
    val instructions: List<JcRawInst>
) {
    override fun toString(): String = instructions.joinToString(
        prefix = "\n--------------------\n",
        postfix = "\n--------------------\n",
        separator = "\n"
    )
}

sealed interface JcRawInst {
    val operands: List<JcRawExpr>

    abstract fun <T> accept(visitor: JcRawInstVisitor<T>): T
}

data class JcRawAssignInst(
    val lhv: JcRawValue,
    val rhv: JcRawExpr
) : JcRawInst {

    override val operands: List<JcRawExpr>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$offset$lhv = $rhv"

    override fun <T> accept(visitor: JcRawInstVisitor<T>): T {
        return visitor.visitJcRawAssignInst(this)
    }
}

data class JcRawEnterMonitorInst(
    val monitor: JcRawValue
) : JcRawInst {
    override val operands: List<JcRawExpr>
        get() = listOf(monitor)

    override fun toString(): String = "${offset}enter monitor $monitor"

    override fun <T> accept(visitor: JcRawInstVisitor<T>): T {
        return visitor.visitJcRawEnterMonitorInst(this)
    }
}

data class JcRawExitMonitorInst(
    val monitor: JcRawValue
) : JcRawInst {
    override val operands: List<JcRawExpr>
        get() = listOf(monitor)

    override fun toString(): String = "${offset}exit monitor $monitor"

    override fun <T> accept(visitor: JcRawInstVisitor<T>): T {
        return visitor.visitJcRawExitMonitorInst(this)
    }
}

data class JcRawCallInst(
    val callExpr: JcRawCallExpr
) : JcRawInst {
    override val operands: List<JcRawExpr>
        get() = listOf(callExpr)

    override fun toString(): String = "$offset$callExpr"

    override fun <T> accept(visitor: JcRawInstVisitor<T>): T {
        return visitor.visitJcRawCallInst(this)
    }
}

data class JcRawLabelInst(
    val name: String
) : JcRawInst {
    override val operands: List<JcRawExpr>
        get() = emptyList()

    override fun toString(): String = "label $name:"

    override fun <T> accept(visitor: JcRawInstVisitor<T>): T {
        return visitor.visitJcRawLabelInst(this)
    }
}

data class JcRawReturnInst(
    val returnValue: JcRawValue?
) : JcRawInst {
    override val operands: List<JcRawExpr>
        get() = listOfNotNull(returnValue)

    override fun toString(): String = "${offset}return" + (returnValue?.let { " $it" } ?: "")

    override fun <T> accept(visitor: JcRawInstVisitor<T>): T {
        return visitor.visitJcRawReturnInst(this)
    }
}

data class JcRawThrowInst(
    val throwable: JcRawValue
) : JcRawInst {
    override val operands: List<JcRawExpr>
        get() = listOf(throwable)

    override fun toString(): String = "${offset}throw $throwable"

    override fun <T> accept(visitor: JcRawInstVisitor<T>): T {
        return visitor.visitJcRawThrowInst(this)
    }
}

data class JcRawCatchInst(
    val throwable: JcRawValue,
    val handler: JcRawLabelInst,
    val startInclusive: JcRawLabelInst,
    val endExclusive: JcRawLabelInst
) : JcRawInst {
    override val operands: List<JcRawExpr>
        get() = listOf(throwable)

    override fun toString(): String = "${offset}catch ($throwable: ${throwable.typeName}) ${startInclusive.name} - ${endExclusive.name}"

    override fun <T> accept(visitor: JcRawInstVisitor<T>): T {
        return visitor.visitJcRawCatchInst(this)
    }
}

sealed interface JcRawBranchingInst : JcRawInst {
    val successors: List<JcRawLabelInst>
}

data class JcRawGotoInst(
    val target: JcRawLabelInst
) : JcRawBranchingInst {
    override val operands: List<JcRawExpr>
        get() = emptyList()

    override val successors: List<JcRawLabelInst>
        get() = listOf(target)

    override fun toString(): String = "${offset}goto ${target.name}"

    override fun <T> accept(visitor: JcRawInstVisitor<T>): T {
        return visitor.visitJcRawGotoInst(this)
    }
}

data class JcRawIfInst(
    val condition: JcRawConditionExpr,
    val trueBranch: JcRawLabelInst,
    val falseBranch: JcRawLabelInst
) : JcRawBranchingInst {
    override val operands: List<JcRawExpr>
        get() = listOf(condition)

    override val successors: List<JcRawLabelInst>
        get() = listOf(trueBranch, falseBranch)

    override fun toString(): String = "${offset}if ($condition) goto ${trueBranch.name} else ${falseBranch.name}"

    override fun <T> accept(visitor: JcRawInstVisitor<T>): T {
        return visitor.visitJcRawIfInst(this)
    }
}

data class JcRawSwitchInst(
    val key: JcRawValue,
    val branches: Map<JcRawValue, JcRawLabelInst>,
    val default: JcRawLabelInst
) : JcRawBranchingInst {
    override val operands: List<JcRawExpr>
        get() = listOf(key) + branches.keys

    override val successors: List<JcRawLabelInst>
        get() = branches.values + default

    override fun toString(): String = buildString {
        appendLine("${offset}switch ($key) {")
        branches.forEach { (option, label) -> appendLine("$offset  $option -> ${label.name}") }
        appendLine("$offset  else -> ${default.name}")
        append("${offset}}")
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

    override fun toString(): String = "new $typeName${dimensions.joinToString() { "[$it]" }}"

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

    override fun toString(): String = "$operand instanceof $targetType"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawInstanceOfExpr(this)
    }
}

sealed interface JcRawCallExpr : JcRawExpr {
    val declaringClass: TypeName
    val methodName: String
    val methodDesc: String
    val args: List<JcRawValue>

    override val operands: List<JcRawValue>
        get() = args
}


data class JcRawHandle(
    val tag: Int,
    val declaringClass: TypeName,
    val name: String,
    val desc: String,
    val isInterface: Boolean,
)

data class JcRawDynamicCallExpr(
    override val typeName: TypeName,
    override val declaringClass: TypeName,
    override val methodName: String,
    override val methodDesc: String,
    override val args: List<JcRawValue>,
    val bsm: JcRawHandle,
    val bsmArgs: List<Any>
) : JcRawCallExpr {
    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawDynamicCallExpr(this)
    }
}

data class JcRawVirtualCallExpr(
    override val typeName: TypeName,
    override val declaringClass: TypeName,
    override val methodName: String,
    override val methodDesc: String,
    val instance: JcRawValue,
    override val args: List<JcRawValue>,
) : JcRawCallExpr {
    override val operands: List<JcRawValue>
        get() = listOf(instance) + args

    override fun toString(): String =
        "$instance.$methodName${args.joinToString(prefix = "(", postfix = ")", separator = ", ")}"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawVirtualCallExpr(this)
    }
}

data class JcRawInterfaceCallExpr(
    override val typeName: TypeName,
    override val declaringClass: TypeName,
    override val methodName: String,
    override val methodDesc: String,
    val instance: JcRawValue,
    override val args: List<JcRawValue>,
) : JcRawCallExpr {
    override val operands: List<JcRawValue>
        get() = listOf(instance) + args

    override fun toString(): String =
        "$instance.$methodName${args.joinToString(prefix = "(", postfix = ")", separator = ", ")}"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawInterfaceCallExpr(this)
    }
}

data class JcRawStaticCallExpr(
    override val typeName: TypeName,
    override val declaringClass: TypeName,
    override val methodName: String,
    override val methodDesc: String,
    override val args: List<JcRawValue>,
) : JcRawCallExpr {
    override fun toString(): String =
        "$declaringClass.$methodName${args.joinToString(prefix = "(", postfix = ")", separator = ", ")}"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawStaticCallExpr(this)
    }
}

data class JcRawSpecialCallExpr(
    override val typeName: TypeName,
    override val declaringClass: TypeName,
    override val methodName: String,
    override val methodDesc: String,
    val instance: JcRawValue,
    override val args: List<JcRawValue>,
) : JcRawCallExpr {
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

data class JcRawThis(override val typeName: TypeName) : JcRawSimpleValue {
    override fun toString(): String = "this"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawThis(this)
    }
}

data class JcRawArgument(val index: Int, val name: String?, override val typeName: TypeName) : JcRawSimpleValue {
    override fun toString(): String = name ?: "arg$$index"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawArgument(this)
    }
}

data class JcRawLocal(val name: String, override val typeName: TypeName) : JcRawSimpleValue {
    override fun toString(): String = name

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawLocal(this)
    }
}

data class JcRawRegister(val index: Int, override val typeName: TypeName) : JcRawSimpleValue {
    override fun toString(): String = "%$index"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawRegister(this)
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
