package org.utbot.jcdb.api

import org.utbot.jcdb.api.cfg.JcRawExprVisitor
import org.utbot.jcdb.api.cfg.JcRawInstVisitor

private const val offset = "  "

class JcRawInstList(
    instructions: List<JcRawInst> = emptyList(),
    tryCatchBlocks: List<JcRawTryCatchBlock> = emptyList()
) {
    val instructions_ = instructions.toMutableList()
    val tryCatchBlocks = tryCatchBlocks.toMutableList()

    override fun toString(): String = instructions_.joinToString(
        prefix = "\n--------------------\n",
        postfix = "\n--------------------\n",
        separator = "\n"
    ) + if (tryCatchBlocks.isNotEmpty()) tryCatchBlocks.joinToString(
        postfix = "\n--------------------\n",
        separator = "\n"
    ) else ""
}

data class JcRawTryCatchBlock(
    val throwable: TypeName,
    val handler: JcRawLabelInst,
    val startInclusive: JcRawLabelInst,
    val endExclusive: JcRawLabelInst
) {
    override fun toString(): String = "${handler.name} catch $throwable: ${startInclusive.name} - ${endExclusive.name}"
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
    val throwable: JcRawValue
) : JcRawInst {
    override val operands: List<JcRawExpr>
        get() = listOf(throwable)

    override fun toString(): String = "${offset}catch $throwable"

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
    val condition: JcRawValue,
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
    val operands: List<JcRawValue>

    fun <T> accept(visitor: JcRawExprVisitor<T>): T
}

data class JcRawAddExpr(
    val lhv: JcRawValue,
    val rhv: JcRawValue
) : JcRawExpr {
    override val operands: List<JcRawValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv + $rhv"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawAddExpr(this)
    }
}

data class JcRawAndExpr(
    val lhv: JcRawValue,
    val rhv: JcRawValue
) : JcRawExpr {
    override val operands: List<JcRawValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv & $rhv"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawAndExpr(this)
    }
}

data class JcRawCmpExpr(
    val lhv: JcRawValue,
    val rhv: JcRawValue
) : JcRawExpr {
    override val operands: List<JcRawValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv cmp $rhv"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawCmpExpr(this)
    }
}

data class JcRawCmpgExpr(
    val lhv: JcRawValue,
    val rhv: JcRawValue
) : JcRawExpr {
    override val operands: List<JcRawValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv cmpg $rhv"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawCmpgExpr(this)
    }
}

data class JcRawCmplExpr(
    val lhv: JcRawValue,
    val rhv: JcRawValue
) : JcRawExpr {
    override val operands: List<JcRawValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv cmpl $rhv"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawCmplExpr(this)
    }
}

data class JcRawDivExpr(
    val lhv: JcRawValue,
    val rhv: JcRawValue
) : JcRawExpr {
    override val operands: List<JcRawValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv / $rhv"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawDivExpr(this)
    }
}

data class JcRawMulExpr(
    val lhv: JcRawValue,
    val rhv: JcRawValue
) : JcRawExpr {
    override val operands: List<JcRawValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv * $rhv"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawMulExpr(this)
    }
}

data class JcRawEqExpr(
    val lhv: JcRawValue,
    val rhv: JcRawValue
) : JcRawExpr {
    override val operands: List<JcRawValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv == $rhv"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawEqExpr(this)
    }
}

data class JcRawNeqExpr(
    val lhv: JcRawValue,
    val rhv: JcRawValue
) : JcRawExpr {
    override val operands: List<JcRawValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv != $rhv"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawNeqExpr(this)
    }
}

data class JcRawGeExpr(
    val lhv: JcRawValue,
    val rhv: JcRawValue
) : JcRawExpr {
    override val operands: List<JcRawValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv >= $rhv"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawGeExpr(this)
    }
}

data class JcRawGtExpr(
    val lhv: JcRawValue,
    val rhv: JcRawValue
) : JcRawExpr {
    override val operands: List<JcRawValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv > $rhv"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawGtExpr(this)
    }
}

data class JcRawLeExpr(
    val lhv: JcRawValue,
    val rhv: JcRawValue
) : JcRawExpr {
    override val operands: List<JcRawValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv <= $rhv"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawLeExpr(this)
    }
}

data class JcRawLtExpr(
    val lhv: JcRawValue,
    val rhv: JcRawValue
) : JcRawExpr {
    override val operands: List<JcRawValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv < $rhv"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawLtExpr(this)
    }
}

data class JcRawOrExpr(
    val lhv: JcRawValue,
    val rhv: JcRawValue
) : JcRawExpr {
    override val operands: List<JcRawValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv | $rhv"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawOrExpr(this)
    }
}

data class JcRawRemExpr(
    val lhv: JcRawValue,
    val rhv: JcRawValue
) : JcRawExpr {
    override val operands: List<JcRawValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv % $rhv"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawRemExpr(this)
    }
}

data class JcRawShlExpr(
    val lhv: JcRawValue,
    val rhv: JcRawValue
) : JcRawExpr {
    override val operands: List<JcRawValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv << $rhv"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawShlExpr(this)
    }
}

data class JcRawShrExpr(
    val lhv: JcRawValue,
    val rhv: JcRawValue
) : JcRawExpr {
    override val operands: List<JcRawValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv >> $rhv"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawShrExpr(this)
    }
}

data class JcRawSubExpr(
    val lhv: JcRawValue,
    val rhv: JcRawValue
) : JcRawExpr {
    override val operands: List<JcRawValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv - $rhv"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawSubExpr(this)
    }
}

data class JcRawUshrExpr(
    val lhv: JcRawValue,
    val rhv: JcRawValue
) : JcRawExpr {
    override val operands: List<JcRawValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv u<< $rhv"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawUshrExpr(this)
    }
}

data class JcRawXorExpr(
    val lhv: JcRawValue,
    val rhv: JcRawValue
) : JcRawExpr {
    override val operands: List<JcRawValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv ^ $rhv"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawXorExpr(this)
    }
}

data class JcRawLengthExpr(
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
    val operand: JcRawValue,
    val targetType: TypeName
) : JcRawExpr {
    override val operands: List<JcRawValue>
        get() = listOf(operand)

    override fun toString(): String = "($targetType) $operand"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawCastExpr(this)
    }
}

data class JcRawNewExpr(
    val targetType: TypeName
) : JcRawExpr {
    override val operands: List<JcRawValue>
        get() = emptyList()

    override fun toString(): String = "new $targetType"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawNewExpr(this)
    }
}

data class JcRawNewArrayExpr(
    val dimensions: List<JcRawValue>,
    val targetType: TypeName
) : JcRawExpr {
    constructor(length: JcRawValue, targetType: TypeName) : this(listOf(length), targetType)

    override val operands: List<JcRawValue>
        get() = dimensions

    override fun toString(): String = "new $targetType${dimensions.joinToString() { "[$it]" }}"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawNewArrayExpr(this)
    }
}

data class JcRawInstanceOfExpr(
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

data class JcRawDynamicCallExpr(
    override val declaringClass: TypeName,
    override val methodName: String,
    override val methodDesc: String,
    override val args: List<JcRawValue>,
    val bsm: JcRawMethodConstant,
    val bsmArgs: List<Any>
) : JcRawCallExpr {
    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawDynamicCallExpr(this)
    }
}

data class JcRawVirtualCallExpr(
    override val declaringClass: TypeName,
    override val methodName: String,
    override val methodDesc: String,
    val instance: JcRawValue,
    override val args: List<JcRawValue>,
) : JcRawCallExpr {
    override val operands: List<JcRawValue>
        get() = listOf(instance) + args
    override fun toString(): String = "$instance.$methodName${args.joinToString(prefix = "(", postfix = ")", separator = ", ")}"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawVirtualCallExpr(this)
    }
}

data class JcRawInterfaceCallExpr(
    override val declaringClass: TypeName,
    override val methodName: String,
    override val methodDesc: String,
    val instance: JcRawValue,
    override val args: List<JcRawValue>,
) : JcRawCallExpr {
    override val operands: List<JcRawValue>
        get() = listOf(instance) + args
    override fun toString(): String = "$instance.$methodName${args.joinToString(prefix = "(", postfix = ")", separator = ", ")}"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawInterfaceCallExpr(this)
    }
}

data class JcRawStaticCallExpr(
    override val declaringClass: TypeName,
    override val methodName: String,
    override val methodDesc: String,
    override val args: List<JcRawValue>,
) : JcRawCallExpr {
    override fun toString(): String = "$declaringClass.$methodName${args.joinToString(prefix = "(", postfix = ")", separator = ", ")}"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawStaticCallExpr(this)
    }
}

data class JcRawSpecialCallExpr(
    override val declaringClass: TypeName,
    override val methodName: String,
    override val methodDesc: String,
    val instance: JcRawValue,
    override val args: List<JcRawValue>,
) : JcRawCallExpr {
    override fun toString(): String = "$instance.$methodName${args.joinToString(prefix = "(", postfix = ")", separator = ", ")}"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawSpecialCallExpr(this)
    }
}


sealed class JcRawValue(open val typeName: TypeName) : JcRawExpr {
    override val operands: List<JcRawValue>
        get() = emptyList()
}

data class JcRawThis(override val typeName: TypeName) : JcRawValue(typeName) {
    override fun toString(): String = "this"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawThis(this)
    }
}
data class JcRawArgument(val index: Int, val name: String?, override val typeName: TypeName) : JcRawValue(typeName) {
    override fun toString(): String = name ?: "arg$$index"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawArgument(this)
    }
}
data class JcRawLocal(val name: String, override val typeName: TypeName) : JcRawValue(typeName) {
    override fun toString(): String = name

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawLocal(this)
    }
}

data class JcRawRegister(val index: Int, override val typeName: TypeName) : JcRawValue(typeName) {
    override fun toString(): String = "%$index"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawRegister(this)
    }
}

data class JcRawFieldRef(
    val instance: JcRawValue?,
    val declaringClass: TypeName,
    val fieldName: String,
    override val typeName: TypeName
) : JcRawValue(typeName) {
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
) : JcRawValue(typeName) {
    override fun toString(): String = "$array[$index]"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawArrayAccess(this)
    }
}

sealed class JcRawConstant(typeName: TypeName) : JcRawValue(typeName)

data class JcRawBool(val value: Boolean, override val typeName: TypeName) : JcRawConstant(typeName) {
    override fun toString(): String = "$value"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawBool(this)
    }
}
data class JcRawByte(val value: Byte, override val typeName: TypeName) : JcRawConstant(typeName) {
    override fun toString(): String = "$value"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawByte(this)
    }
}
data class JcRawChar(val value: Char, override val typeName: TypeName) : JcRawConstant(typeName) {
    override fun toString(): String = "$value"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawChar(this)
    }
}
data class JcRawShort(val value: Short, override val typeName: TypeName) : JcRawConstant(typeName) {
    override fun toString(): String = "$value"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawShort(this)
    }
}
data class JcRawInt(val value: Int, override val typeName: TypeName) : JcRawConstant(typeName) {
    override fun toString(): String = "$value"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawInt(this)
    }
}
data class JcRawLong(val value: Long, override val typeName: TypeName) : JcRawConstant(typeName) {
    override fun toString(): String = "$value"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawLong(this)
    }
}
data class JcRawFloat(val value: Float, override val typeName: TypeName) : JcRawConstant(typeName) {
    override fun toString(): String = "$value"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawFloat(this)
    }
}
data class JcRawDouble(val value: Double, override val typeName: TypeName) : JcRawConstant(typeName) {
    override fun toString(): String = "$value"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawDouble(this)
    }
}

data class JcRawNullConstant(override val typeName: TypeName) : JcRawConstant(typeName) {
    override fun toString(): String = "null"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawNullConstant(this)
    }
}
data class JcRawStringConstant(val value: String, override val typeName: TypeName) : JcRawConstant(typeName) {
    override fun toString(): String = "\"$value\""

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawStringConstant(this)
    }
}
data class JcRawClassConstant(val className: TypeName, override val typeName: TypeName) : JcRawConstant(typeName) {
    override fun toString(): String = "$className.class"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawClassConstant(this)
    }
}
data class JcRawMethodConstant(
    val tag: Int,
    val declaringClass: TypeName,
    val name: String,
    val argumentTypes: List<TypeName>,
    val returnType: TypeName,
    val isInterface: Boolean,
    override val typeName: TypeName
) : JcRawConstant(typeName) {
    override fun toString(): String = "$declaringClass.$name${argumentTypes.joinToString(prefix = "(", postfix = ")")}:$returnType"

    override fun <T> accept(visitor: JcRawExprVisitor<T>): T {
        return visitor.visitJcRawMethodConstant(this)
    }
}
