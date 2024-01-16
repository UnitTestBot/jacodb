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

package org.jacodb.impl.cfg.util

import org.jacodb.api.core.TypeName
import org.jacodb.api.jvm.cfg.JcRawAddExpr
import org.jacodb.api.jvm.cfg.JcRawAndExpr
import org.jacodb.api.jvm.cfg.JcRawArgument
import org.jacodb.api.jvm.cfg.JcRawArrayAccess
import org.jacodb.api.jvm.cfg.JcRawAssignInst
import org.jacodb.api.jvm.cfg.JcRawBinaryExpr
import org.jacodb.api.jvm.cfg.JcRawBool
import org.jacodb.api.jvm.cfg.JcRawByte
import org.jacodb.api.jvm.cfg.JcRawCallExpr
import org.jacodb.api.jvm.cfg.JcRawCallInst
import org.jacodb.api.jvm.cfg.JcRawCastExpr
import org.jacodb.api.jvm.cfg.JcRawCatchInst
import org.jacodb.api.jvm.cfg.JcRawChar
import org.jacodb.api.jvm.cfg.JcRawClassConstant
import org.jacodb.api.jvm.cfg.JcRawCmpExpr
import org.jacodb.api.jvm.cfg.JcRawCmpgExpr
import org.jacodb.api.jvm.cfg.JcRawCmplExpr
import org.jacodb.api.jvm.cfg.JcRawConditionExpr
import org.jacodb.api.jvm.cfg.JcRawDivExpr
import org.jacodb.api.jvm.cfg.JcRawDouble
import org.jacodb.api.jvm.cfg.JcRawDynamicCallExpr
import org.jacodb.api.jvm.cfg.JcRawEnterMonitorInst
import org.jacodb.api.jvm.cfg.JcRawEqExpr
import org.jacodb.api.jvm.cfg.JcRawExitMonitorInst
import org.jacodb.api.jvm.cfg.JcRawExpr
import org.jacodb.api.jvm.cfg.JcRawExprVisitor
import org.jacodb.api.jvm.cfg.JcRawFieldRef
import org.jacodb.api.jvm.cfg.JcRawFloat
import org.jacodb.api.jvm.cfg.JcRawGeExpr
import org.jacodb.api.jvm.cfg.JcRawGotoInst
import org.jacodb.api.jvm.cfg.JcRawGtExpr
import org.jacodb.api.jvm.cfg.JcRawIfInst
import org.jacodb.api.jvm.cfg.JcRawInst
import org.jacodb.api.jvm.cfg.JcRawInstVisitor
import org.jacodb.api.jvm.cfg.JcRawInstanceOfExpr
import org.jacodb.api.jvm.cfg.JcRawInt
import org.jacodb.api.jvm.cfg.JcRawInterfaceCallExpr
import org.jacodb.api.jvm.cfg.JcRawLabelInst
import org.jacodb.api.jvm.cfg.JcRawLeExpr
import org.jacodb.api.jvm.cfg.JcRawLengthExpr
import org.jacodb.api.jvm.cfg.JcRawLineNumberInst
import org.jacodb.api.jvm.cfg.JcRawLocalVar
import org.jacodb.api.jvm.cfg.JcRawLong
import org.jacodb.api.jvm.cfg.JcRawLtExpr
import org.jacodb.api.jvm.cfg.JcRawMethodConstant
import org.jacodb.api.jvm.cfg.JcRawMethodType
import org.jacodb.api.jvm.cfg.JcRawMulExpr
import org.jacodb.api.jvm.cfg.JcRawNegExpr
import org.jacodb.api.jvm.cfg.JcRawNeqExpr
import org.jacodb.api.jvm.cfg.JcRawNewArrayExpr
import org.jacodb.api.jvm.cfg.JcRawNewExpr
import org.jacodb.api.jvm.cfg.JcRawNullConstant
import org.jacodb.api.jvm.cfg.JcRawOrExpr
import org.jacodb.api.jvm.cfg.JcRawRemExpr
import org.jacodb.api.jvm.cfg.JcRawReturnInst
import org.jacodb.api.jvm.cfg.JcRawShlExpr
import org.jacodb.api.jvm.cfg.JcRawShort
import org.jacodb.api.jvm.cfg.JcRawShrExpr
import org.jacodb.api.jvm.cfg.JcRawSimpleValue
import org.jacodb.api.jvm.cfg.JcRawSpecialCallExpr
import org.jacodb.api.jvm.cfg.JcRawStaticCallExpr
import org.jacodb.api.jvm.cfg.JcRawStringConstant
import org.jacodb.api.jvm.cfg.JcRawSubExpr
import org.jacodb.api.jvm.cfg.JcRawSwitchInst
import org.jacodb.api.jvm.cfg.JcRawThis
import org.jacodb.api.jvm.cfg.JcRawThrowInst
import org.jacodb.api.jvm.cfg.JcRawUshrExpr
import org.jacodb.api.jvm.cfg.JcRawValue
import org.jacodb.api.jvm.cfg.JcRawVirtualCallExpr
import org.jacodb.api.jvm.cfg.JcRawXorExpr

class ExprMapper(val mapping: Map<JcRawExpr, JcRawExpr>) : JcRawInstVisitor<JcRawInst>, JcRawExprVisitor<JcRawExpr> {

    override fun visitJcRawAssignInst(inst: JcRawAssignInst): JcRawInst {
        val newLhv = inst.lhv.accept(this) as JcRawValue
        val newRhv = inst.rhv.accept(this)
        return when {
            inst.lhv == newLhv && inst.rhv == newRhv -> inst
            else -> JcRawAssignInst(inst.owner, newLhv, newRhv)
        }
    }

    override fun visitJcRawEnterMonitorInst(inst: JcRawEnterMonitorInst): JcRawInst {
        val newMonitor = inst.monitor.accept(this) as JcRawSimpleValue
        return when (inst.monitor) {
            newMonitor -> inst
            else -> JcRawEnterMonitorInst(inst.owner, newMonitor)
        }
    }

    override fun visitJcRawExitMonitorInst(inst: JcRawExitMonitorInst): JcRawInst {
        val newMonitor = inst.monitor.accept(this) as JcRawSimpleValue
        return when (inst.monitor) {
            newMonitor -> inst
            else -> JcRawExitMonitorInst(inst.owner, newMonitor)
        }
    }

    override fun visitJcRawCallInst(inst: JcRawCallInst): JcRawInst {
        val newCall = inst.callExpr.accept(this) as JcRawCallExpr
        return when (inst.callExpr) {
            newCall -> inst
            else -> JcRawCallInst(inst.owner, newCall)
        }
    }

    override fun visitJcRawLabelInst(inst: JcRawLabelInst): JcRawInst {
        return inst
    }

    override fun visitJcRawLineNumberInst(inst: JcRawLineNumberInst): JcRawInst {
        return inst
    }

    override fun visitJcRawReturnInst(inst: JcRawReturnInst): JcRawInst {
        val newReturn = inst.returnValue?.accept(this) as? JcRawValue
        return when (inst.returnValue) {
            newReturn -> inst
            else -> JcRawReturnInst(inst.owner, newReturn)
        }
    }

    override fun visitJcRawThrowInst(inst: JcRawThrowInst): JcRawInst {
        val newThrowable = inst.throwable.accept(this) as JcRawValue
        return when (inst.throwable) {
            newThrowable -> inst
            else -> JcRawThrowInst(inst.owner, newThrowable)
        }
    }

    override fun visitJcRawCatchInst(inst: JcRawCatchInst): JcRawInst {
        val newThrowable = inst.throwable.accept(this) as JcRawValue
        return when (inst.throwable) {
            newThrowable -> inst
            else -> JcRawCatchInst(inst.owner, newThrowable, inst.handler, inst.entries)
        }
    }

    override fun visitJcRawGotoInst(inst: JcRawGotoInst): JcRawInst {
        return inst
    }

    override fun visitJcRawIfInst(inst: JcRawIfInst): JcRawInst {
        val newCondition = inst.condition.accept(this) as JcRawConditionExpr
        return when (inst.condition) {
            newCondition -> inst
            else -> JcRawIfInst(inst.owner, newCondition, inst.trueBranch, inst.falseBranch)
        }
    }

    override fun visitJcRawSwitchInst(inst: JcRawSwitchInst): JcRawInst {
        val newKey = inst.key.accept(this) as JcRawValue
        val newBranches = inst.branches.mapKeys { it.key.accept(this) as JcRawValue }
        return when {
            inst.key == newKey && inst.branches == newBranches -> inst
            else -> JcRawSwitchInst(inst.owner, newKey, newBranches, inst.default)
        }
    }

    private inline fun <T : JcRawExpr> exprHandler(expr: T, handler: () -> JcRawExpr): JcRawExpr {
        return mapping.getOrElse(expr, handler)
    }

    private inline fun <T : JcRawBinaryExpr> binaryHandler(expr: T, handler: (TypeName, JcRawValue, JcRawValue) -> T) =
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
                expr.callSiteMethodName,
                expr.callSiteArgTypes,
                expr.callSiteReturnType,
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
    override fun visitJcRawLocalVar(value: JcRawLocalVar) = exprHandler(value) { value }

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
    override fun visitJcRawMethodType(value: JcRawMethodType) = exprHandler(value) { value }
}
