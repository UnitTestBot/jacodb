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

package org.utbot.jcdb.impl.cfg.util

import org.utbot.jcdb.api.JcRawAddExpr
import org.utbot.jcdb.api.JcRawAndExpr
import org.utbot.jcdb.api.JcRawArgument
import org.utbot.jcdb.api.JcRawArrayAccess
import org.utbot.jcdb.api.JcRawAssignInst
import org.utbot.jcdb.api.JcRawBinaryExpr
import org.utbot.jcdb.api.JcRawBool
import org.utbot.jcdb.api.JcRawByte
import org.utbot.jcdb.api.JcRawCallExpr
import org.utbot.jcdb.api.JcRawCallInst
import org.utbot.jcdb.api.JcRawCastExpr
import org.utbot.jcdb.api.JcRawCatchInst
import org.utbot.jcdb.api.JcRawChar
import org.utbot.jcdb.api.JcRawClassConstant
import org.utbot.jcdb.api.JcRawCmpExpr
import org.utbot.jcdb.api.JcRawCmpgExpr
import org.utbot.jcdb.api.JcRawCmplExpr
import org.utbot.jcdb.api.JcRawConditionExpr
import org.utbot.jcdb.api.JcRawDivExpr
import org.utbot.jcdb.api.JcRawDouble
import org.utbot.jcdb.api.JcRawDynamicCallExpr
import org.utbot.jcdb.api.JcRawEnterMonitorInst
import org.utbot.jcdb.api.JcRawEqExpr
import org.utbot.jcdb.api.JcRawExitMonitorInst
import org.utbot.jcdb.api.JcRawExpr
import org.utbot.jcdb.api.JcRawFieldRef
import org.utbot.jcdb.api.JcRawFloat
import org.utbot.jcdb.api.JcRawGeExpr
import org.utbot.jcdb.api.JcRawGotoInst
import org.utbot.jcdb.api.JcRawGtExpr
import org.utbot.jcdb.api.JcRawIfInst
import org.utbot.jcdb.api.JcRawInst
import org.utbot.jcdb.api.JcRawInstanceOfExpr
import org.utbot.jcdb.api.JcRawInt
import org.utbot.jcdb.api.JcRawInterfaceCallExpr
import org.utbot.jcdb.api.JcRawLabelInst
import org.utbot.jcdb.api.JcRawLeExpr
import org.utbot.jcdb.api.JcRawLengthExpr
import org.utbot.jcdb.api.JcRawLocal
import org.utbot.jcdb.api.JcRawLong
import org.utbot.jcdb.api.JcRawLtExpr
import org.utbot.jcdb.api.JcRawMethodConstant
import org.utbot.jcdb.api.JcRawMulExpr
import org.utbot.jcdb.api.JcRawNegExpr
import org.utbot.jcdb.api.JcRawNeqExpr
import org.utbot.jcdb.api.JcRawNewArrayExpr
import org.utbot.jcdb.api.JcRawNewExpr
import org.utbot.jcdb.api.JcRawNullConstant
import org.utbot.jcdb.api.JcRawOrExpr
import org.utbot.jcdb.api.JcRawRemExpr
import org.utbot.jcdb.api.JcRawReturnInst
import org.utbot.jcdb.api.JcRawShlExpr
import org.utbot.jcdb.api.JcRawShort
import org.utbot.jcdb.api.JcRawShrExpr
import org.utbot.jcdb.api.JcRawSimpleValue
import org.utbot.jcdb.api.JcRawSpecialCallExpr
import org.utbot.jcdb.api.JcRawStaticCallExpr
import org.utbot.jcdb.api.JcRawStringConstant
import org.utbot.jcdb.api.JcRawSubExpr
import org.utbot.jcdb.api.JcRawSwitchInst
import org.utbot.jcdb.api.JcRawThis
import org.utbot.jcdb.api.JcRawThrowInst
import org.utbot.jcdb.api.JcRawUshrExpr
import org.utbot.jcdb.api.JcRawValue
import org.utbot.jcdb.api.JcRawVirtualCallExpr
import org.utbot.jcdb.api.JcRawXorExpr
import org.utbot.jcdb.api.TypeName
import org.utbot.jcdb.api.cfg.JcRawExprVisitor
import org.utbot.jcdb.api.cfg.JcRawInstVisitor

class ExprMapper(val mapping: Map<JcRawExpr, JcRawExpr>) : JcRawInstVisitor<JcRawInst>, JcRawExprVisitor<JcRawExpr> {
    override fun visitJcRawAssignInst(inst: JcRawAssignInst): JcRawInst {
        val newLhv = inst.lhv.accept(this) as JcRawValue
        val newRhv = inst.rhv.accept(this)
        return when {
            inst.lhv == newLhv && inst.rhv == newRhv -> inst
            else -> JcRawAssignInst(newLhv, newRhv)
        }
    }

    override fun visitJcRawEnterMonitorInst(inst: JcRawEnterMonitorInst): JcRawInst {
        val newMonitor = inst.monitor.accept(this) as JcRawSimpleValue
        return when (inst.monitor) {
            newMonitor -> inst
            else -> JcRawEnterMonitorInst(newMonitor)
        }
    }

    override fun visitJcRawExitMonitorInst(inst: JcRawExitMonitorInst): JcRawInst {
        val newMonitor = inst.monitor.accept(this) as JcRawSimpleValue
        return when (inst.monitor) {
            newMonitor -> inst
            else -> JcRawExitMonitorInst(newMonitor)
        }
    }

    override fun visitJcRawCallInst(inst: JcRawCallInst): JcRawInst {
        val newCall = inst.callExpr.accept(this) as JcRawCallExpr
        return when (inst.callExpr) {
            newCall -> inst
            else -> JcRawCallInst(newCall)
        }
    }

    override fun visitJcRawLabelInst(inst: JcRawLabelInst): JcRawInst {
        return inst
    }

    override fun visitJcRawReturnInst(inst: JcRawReturnInst): JcRawInst {
        val newReturn = inst.returnValue?.accept(this) as? JcRawValue
        return when (inst.returnValue) {
            newReturn -> inst
            else -> JcRawReturnInst(newReturn)
        }
    }

    override fun visitJcRawThrowInst(inst: JcRawThrowInst): JcRawInst {
        val newThrowable = inst.throwable.accept(this) as JcRawValue
        return when (inst.throwable) {
            newThrowable -> inst
            else -> JcRawThrowInst(newThrowable)
        }
    }

    override fun visitJcRawCatchInst(inst: JcRawCatchInst): JcRawInst {
        val newThrowable = inst.throwable.accept(this) as JcRawValue
        return when (inst.throwable) {
            newThrowable -> inst
            else -> JcRawCatchInst(newThrowable, inst.handler, inst.startInclusive, inst.endExclusive)
        }
    }

    override fun visitJcRawGotoInst(inst: JcRawGotoInst): JcRawInst {
        return inst
    }

    override fun visitJcRawIfInst(inst: JcRawIfInst): JcRawInst {
        val newCondition = inst.condition.accept(this) as JcRawConditionExpr
        return when (inst.condition) {
            newCondition -> inst
            else -> JcRawIfInst(newCondition, inst.trueBranch, inst.falseBranch)
        }
    }

    override fun visitJcRawSwitchInst(inst: JcRawSwitchInst): JcRawInst {
        val newKey = inst.key.accept(this) as JcRawValue
        val newBranches = inst.branches.mapKeys { it.key.accept(this) as JcRawValue }
        return when {
            inst.key == newKey && inst.branches == newBranches -> inst
            else -> JcRawSwitchInst(newKey, newBranches, inst.default)
        }
    }

    private fun <T : JcRawExpr> exprHandler(expr: T, handler: () -> JcRawExpr): JcRawExpr {
        if (expr in mapping) return mapping.getValue(expr)
        return handler()
    }

    private fun <T : JcRawBinaryExpr> binaryHandler(expr: T, handler: (TypeName, JcRawValue, JcRawValue) -> T) =
        exprHandler(expr) {
            val newLhv = expr.lhv.accept(this) as JcRawValue
            val newRhv = expr.rhv.accept(this) as JcRawValue
            when {
                expr.lhv == newLhv && expr.rhv == newRhv -> expr
                else -> handler(newLhv.typeName, newLhv, newRhv)
            }
        }

    override fun visitJcRawAddExpr(expr: JcRawAddExpr) = binaryHandler(expr) { type, lhv, rhv ->
        JcRawAddExpr(type, lhv, rhv)
    }

    override fun visitJcRawAndExpr(expr: JcRawAndExpr) = binaryHandler(expr) { type, lhv, rhv ->
        JcRawAndExpr(type, lhv, rhv)
    }

    override fun visitJcRawCmpExpr(expr: JcRawCmpExpr) = binaryHandler(expr) { type, lhv, rhv ->
        JcRawCmpExpr(type, lhv, rhv)
    }

    override fun visitJcRawCmpgExpr(expr: JcRawCmpgExpr) = binaryHandler(expr) { type, lhv, rhv ->
        JcRawCmpgExpr(type, lhv, rhv)
    }

    override fun visitJcRawCmplExpr(expr: JcRawCmplExpr) = binaryHandler(expr) { type, lhv, rhv ->
        JcRawCmplExpr(type, lhv, rhv)
    }

    override fun visitJcRawDivExpr(expr: JcRawDivExpr) = binaryHandler(expr) { type, lhv, rhv ->
        JcRawDivExpr(type, lhv, rhv)
    }

    override fun visitJcRawMulExpr(expr: JcRawMulExpr) = binaryHandler(expr) { type, lhv, rhv ->
        JcRawMulExpr(type, lhv, rhv)
    }

    override fun visitJcRawEqExpr(expr: JcRawEqExpr) = binaryHandler(expr) { _, lhv, rhv ->
        JcRawEqExpr(expr.typeName, lhv, rhv)
    }

    override fun visitJcRawNeqExpr(expr: JcRawNeqExpr) = binaryHandler(expr) { _, lhv, rhv ->
        JcRawNeqExpr(expr.typeName, lhv, rhv)
    }

    override fun visitJcRawGeExpr(expr: JcRawGeExpr) = binaryHandler(expr) { _, lhv, rhv ->
        JcRawGeExpr(expr.typeName, lhv, rhv)
    }

    override fun visitJcRawGtExpr(expr: JcRawGtExpr) = binaryHandler(expr) { _, lhv, rhv ->
        JcRawGtExpr(expr.typeName, lhv, rhv)
    }

    override fun visitJcRawLeExpr(expr: JcRawLeExpr) = binaryHandler(expr) { _, lhv, rhv ->
        JcRawLeExpr(expr.typeName, lhv, rhv)
    }

    override fun visitJcRawLtExpr(expr: JcRawLtExpr) = binaryHandler(expr) { _, lhv, rhv ->
        JcRawLtExpr(expr.typeName, lhv, rhv)
    }

    override fun visitJcRawOrExpr(expr: JcRawOrExpr) = binaryHandler(expr) { type, lhv, rhv ->
        JcRawOrExpr(type, lhv, rhv)
    }

    override fun visitJcRawRemExpr(expr: JcRawRemExpr) = binaryHandler(expr) { type, lhv, rhv ->
        JcRawRemExpr(type, lhv, rhv)
    }

    override fun visitJcRawShlExpr(expr: JcRawShlExpr) = binaryHandler(expr) { type, lhv, rhv ->
        JcRawShlExpr(type, lhv, rhv)
    }

    override fun visitJcRawShrExpr(expr: JcRawShrExpr) = binaryHandler(expr) { type, lhv, rhv ->
        JcRawShrExpr(type, lhv, rhv)
    }

    override fun visitJcRawSubExpr(expr: JcRawSubExpr) = binaryHandler(expr) { type, lhv, rhv ->
        JcRawSubExpr(type, lhv, rhv)
    }

    override fun visitJcRawUshrExpr(expr: JcRawUshrExpr) = binaryHandler(expr) { type, lhv, rhv ->
        JcRawUshrExpr(type, lhv, rhv)
    }

    override fun visitJcRawXorExpr(expr: JcRawXorExpr) = binaryHandler(expr) { type, lhv, rhv ->
        JcRawXorExpr(type, lhv, rhv)
    }

    override fun visitJcRawLengthExpr(expr: JcRawLengthExpr) = exprHandler(expr) {
        val newArray = expr.array.accept(this) as JcRawValue
        when (expr.array) {
            newArray -> expr
            else -> JcRawLengthExpr(expr.typeName, newArray)
        }
    }

    override fun visitJcRawNegExpr(expr: JcRawNegExpr) = exprHandler(expr) {
        val newOperand = expr.operand.accept(this) as JcRawValue
        when (expr.operand) {
            newOperand -> expr
            else -> JcRawNegExpr(newOperand.typeName, newOperand)
        }
    }

    override fun visitJcRawCastExpr(expr: JcRawCastExpr) = exprHandler(expr) {
        val newOperand = expr.operand.accept(this) as JcRawValue
        when (expr.operand) {
            newOperand -> expr
            else -> JcRawCastExpr(expr.typeName, newOperand)
        }
    }

    override fun visitJcRawNewExpr(expr: JcRawNewExpr) = exprHandler(expr) { expr }

    override fun visitJcRawNewArrayExpr(expr: JcRawNewArrayExpr) = exprHandler(expr) {
        val newDimensions = expr.dimensions.map { it.accept(this) as JcRawValue }
        when (expr.dimensions) {
            newDimensions -> expr
            else -> JcRawNewArrayExpr(expr.typeName, newDimensions)
        }
    }

    override fun visitJcRawInstanceOfExpr(expr: JcRawInstanceOfExpr) = exprHandler(expr) {
        val newOperand = expr.operand.accept(this) as JcRawValue
        when (expr.operand) {
            newOperand -> expr
            else -> JcRawInstanceOfExpr(expr.typeName, newOperand, expr.targetType)
        }
    }

    override fun visitJcRawDynamicCallExpr(expr: JcRawDynamicCallExpr) = exprHandler(expr) {
        val newArgs = expr.args.map { it.accept(this) as JcRawValue }
        when (expr.args) {
            newArgs -> expr
            else -> JcRawDynamicCallExpr(
                expr.bsm,
                expr.bsmArgs,
                expr.callCiteMethodName,
                expr.callCiteArgTypes,
                expr.callCiteReturnType,
                newArgs
            )
        }
    }

    override fun visitJcRawVirtualCallExpr(expr: JcRawVirtualCallExpr) = exprHandler(expr) {
        val newInstance = expr.instance.accept(this) as JcRawValue
        val newArgs = expr.args.map { it.accept(this) as JcRawValue }
        when {
            expr.instance == newInstance && expr.args == newArgs -> expr
            else -> JcRawVirtualCallExpr(
                expr.declaringClass,
                expr.methodName,
                expr.argumentTypes,
                expr.returnType,
                newInstance,
                newArgs
            )
        }
    }

    override fun visitJcRawInterfaceCallExpr(expr: JcRawInterfaceCallExpr) = exprHandler(expr) {
        val newInstance = expr.instance.accept(this) as JcRawValue
        val newArgs = expr.args.map { it.accept(this) as JcRawValue }
        when {
            expr.instance == newInstance && expr.args == newArgs -> expr
            else -> JcRawInterfaceCallExpr(
                expr.declaringClass,
                expr.methodName,
                expr.argumentTypes,
                expr.returnType,
                newInstance,
                newArgs
            )
        }
    }

    override fun visitJcRawStaticCallExpr(expr: JcRawStaticCallExpr) = exprHandler(expr) {
        val newArgs = expr.args.map { it.accept(this) as JcRawValue }
        when (expr.args) {
            newArgs -> expr
            else -> JcRawStaticCallExpr(
                expr.declaringClass, expr.methodName, expr.argumentTypes, expr.returnType, newArgs
            )
        }
    }

    override fun visitJcRawSpecialCallExpr(expr: JcRawSpecialCallExpr) = exprHandler(expr) {
        val newInstance = expr.instance.accept(this) as JcRawValue
        val newArgs = expr.args.map { it.accept(this) as JcRawValue }
        when {
            expr.instance == newInstance && expr.args == newArgs -> expr
            else -> JcRawSpecialCallExpr(
                expr.declaringClass,
                expr.methodName,
                expr.argumentTypes,
                expr.returnType,
                newInstance,
                newArgs
            )
        }
    }


    override fun visitJcRawThis(value: JcRawThis) = exprHandler(value) { value }
    override fun visitJcRawArgument(value: JcRawArgument) = exprHandler(value) { value }
    override fun visitJcRawLocal(value: JcRawLocal) = exprHandler(value) { value }

    override fun visitJcRawFieldRef(value: JcRawFieldRef) = exprHandler(value) {
        val newInstance = value.instance?.accept(this) as? JcRawValue
        when (value.instance) {
            newInstance -> value
            else -> JcRawFieldRef(newInstance, value.declaringClass, value.fieldName, value.typeName)
        }
    }

    override fun visitJcRawArrayAccess(value: JcRawArrayAccess) = exprHandler(value) {
        val newArray = value.array.accept(this) as JcRawValue
        val newIndex = value.index.accept(this) as JcRawValue
        when {
            value.array == newArray && value.index == newIndex -> value
            else -> JcRawArrayAccess(newArray, newIndex, value.typeName)
        }
    }

    override fun visitJcRawBool(value: JcRawBool) = exprHandler(value) { value }
    override fun visitJcRawByte(value: JcRawByte) = exprHandler(value) { value }
    override fun visitJcRawChar(value: JcRawChar) = exprHandler(value) { value }
    override fun visitJcRawShort(value: JcRawShort) = exprHandler(value) { value }
    override fun visitJcRawInt(value: JcRawInt) = exprHandler(value) { value }
    override fun visitJcRawLong(value: JcRawLong) = exprHandler(value) { value }
    override fun visitJcRawFloat(value: JcRawFloat) = exprHandler(value) { value }
    override fun visitJcRawDouble(value: JcRawDouble) = exprHandler(value) { value }
    override fun visitJcRawNullConstant(value: JcRawNullConstant) = exprHandler(value) { value }
    override fun visitJcRawStringConstant(value: JcRawStringConstant) = exprHandler(value) { value }
    override fun visitJcRawClassConstant(value: JcRawClassConstant) = exprHandler(value) { value }
    override fun visitJcRawMethodConstant(value: JcRawMethodConstant) = exprHandler(value) { value }
}
