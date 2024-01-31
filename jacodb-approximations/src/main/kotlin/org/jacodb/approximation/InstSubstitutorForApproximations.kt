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

package org.jacodb.approximation

import org.jacodb.api.TypeName
import org.jacodb.api.cfg.BsmDoubleArg
import org.jacodb.api.cfg.BsmFloatArg
import org.jacodb.api.cfg.BsmHandle
import org.jacodb.api.cfg.BsmIntArg
import org.jacodb.api.cfg.BsmLongArg
import org.jacodb.api.cfg.BsmMethodTypeArg
import org.jacodb.api.cfg.BsmStringArg
import org.jacodb.api.cfg.BsmTypeArg
import org.jacodb.api.cfg.JcRawAddExpr
import org.jacodb.api.cfg.JcRawAndExpr
import org.jacodb.api.cfg.JcRawArgument
import org.jacodb.api.cfg.JcRawArrayAccess
import org.jacodb.api.cfg.JcRawAssignInst
import org.jacodb.api.cfg.JcRawBinaryExpr
import org.jacodb.api.cfg.JcRawBool
import org.jacodb.api.cfg.JcRawByte
import org.jacodb.api.cfg.JcRawCallExpr
import org.jacodb.api.cfg.JcRawCallInst
import org.jacodb.api.cfg.JcRawCastExpr
import org.jacodb.api.cfg.JcRawCatchInst
import org.jacodb.api.cfg.JcRawChar
import org.jacodb.api.cfg.JcRawClassConstant
import org.jacodb.api.cfg.JcRawCmpExpr
import org.jacodb.api.cfg.JcRawCmpgExpr
import org.jacodb.api.cfg.JcRawCmplExpr
import org.jacodb.api.cfg.JcRawConditionExpr
import org.jacodb.api.cfg.JcRawDivExpr
import org.jacodb.api.cfg.JcRawDouble
import org.jacodb.api.cfg.JcRawDynamicCallExpr
import org.jacodb.api.cfg.JcRawEnterMonitorInst
import org.jacodb.api.cfg.JcRawEqExpr
import org.jacodb.api.cfg.JcRawExitMonitorInst
import org.jacodb.api.cfg.JcRawExpr
import org.jacodb.api.cfg.JcRawExprVisitor
import org.jacodb.api.cfg.JcRawFieldRef
import org.jacodb.api.cfg.JcRawFloat
import org.jacodb.api.cfg.JcRawGeExpr
import org.jacodb.api.cfg.JcRawGotoInst
import org.jacodb.api.cfg.JcRawGtExpr
import org.jacodb.api.cfg.JcRawIfInst
import org.jacodb.api.cfg.JcRawInst
import org.jacodb.api.cfg.JcRawInstVisitor
import org.jacodb.api.cfg.JcRawInstanceOfExpr
import org.jacodb.api.cfg.JcRawInt
import org.jacodb.api.cfg.JcRawInterfaceCallExpr
import org.jacodb.api.cfg.JcRawLabelInst
import org.jacodb.api.cfg.JcRawLeExpr
import org.jacodb.api.cfg.JcRawLengthExpr
import org.jacodb.api.cfg.JcRawLineNumberInst
import org.jacodb.api.cfg.JcRawLocalVar
import org.jacodb.api.cfg.JcRawLong
import org.jacodb.api.cfg.JcRawLtExpr
import org.jacodb.api.cfg.JcRawMethodConstant
import org.jacodb.api.cfg.JcRawMethodType
import org.jacodb.api.cfg.JcRawMulExpr
import org.jacodb.api.cfg.JcRawNegExpr
import org.jacodb.api.cfg.JcRawNeqExpr
import org.jacodb.api.cfg.JcRawNewArrayExpr
import org.jacodb.api.cfg.JcRawNewExpr
import org.jacodb.api.cfg.JcRawNullConstant
import org.jacodb.api.cfg.JcRawOrExpr
import org.jacodb.api.cfg.JcRawRemExpr
import org.jacodb.api.cfg.JcRawReturnInst
import org.jacodb.api.cfg.JcRawShlExpr
import org.jacodb.api.cfg.JcRawShort
import org.jacodb.api.cfg.JcRawShrExpr
import org.jacodb.api.cfg.JcRawSimpleValue
import org.jacodb.api.cfg.JcRawSpecialCallExpr
import org.jacodb.api.cfg.JcRawStaticCallExpr
import org.jacodb.api.cfg.JcRawStringConstant
import org.jacodb.api.cfg.JcRawSubExpr
import org.jacodb.api.cfg.JcRawSwitchInst
import org.jacodb.api.cfg.JcRawThis
import org.jacodb.api.cfg.JcRawThrowInst
import org.jacodb.api.cfg.JcRawUshrExpr
import org.jacodb.api.cfg.JcRawValue
import org.jacodb.api.cfg.JcRawVirtualCallExpr
import org.jacodb.api.cfg.JcRawXorExpr
import org.jacodb.approximation.Approximations.findOriginalByApproximationOrNull
import org.jacodb.impl.types.TypeNameImpl

/**
 * Removes all occurrences of approximations with their targets in [JcRawInst]s and [JcRawExpr]s.
 */
object InstSubstitutorForApproximations : JcRawInstVisitor<JcRawInst>, JcRawExprVisitor<JcRawExpr> {
    override fun visitJcRawAssignInst(inst: JcRawAssignInst): JcRawInst {
        val newLhv = inst.lhv.accept(this) as JcRawValue
        val newRhv = inst.rhv.accept(this)
        return JcRawAssignInst(inst.owner, newLhv, newRhv)
    }

    override fun visitJcRawEnterMonitorInst(inst: JcRawEnterMonitorInst): JcRawInst {
        val newMonitor = inst.monitor.accept(this) as JcRawSimpleValue
        return JcRawEnterMonitorInst(inst.owner, newMonitor)
    }

    override fun visitJcRawExitMonitorInst(inst: JcRawExitMonitorInst): JcRawInst {
        val newMonitor = inst.monitor.accept(this) as JcRawSimpleValue
        return JcRawExitMonitorInst(inst.owner, newMonitor)
    }

    override fun visitJcRawCallInst(inst: JcRawCallInst): JcRawInst {
        val newCall = inst.callExpr.accept(this) as JcRawCallExpr
        return JcRawCallInst(inst.owner, newCall)
    }

    override fun visitJcRawLabelInst(inst: JcRawLabelInst): JcRawInst {
        return JcRawLabelInst(inst.owner, inst.name)
    }

    override fun visitJcRawLineNumberInst(inst: JcRawLineNumberInst): JcRawInst {
        return JcRawLineNumberInst(inst.owner, inst.lineNumber, inst.start)
    }

    override fun visitJcRawReturnInst(inst: JcRawReturnInst): JcRawInst {
        val newReturn = inst.returnValue?.accept(this) as? JcRawValue
        return JcRawReturnInst(inst.owner, newReturn)
    }

    override fun visitJcRawThrowInst(inst: JcRawThrowInst): JcRawInst {
        val newThrowable = inst.throwable.accept(this) as JcRawValue
        return JcRawThrowInst(inst.owner, newThrowable)
    }

    override fun visitJcRawCatchInst(inst: JcRawCatchInst): JcRawInst {
        val newThrowable = inst.throwable.accept(this) as JcRawValue
        val entries = inst.entries.map {
            it.copy(acceptedThrowable = it.acceptedThrowable.eliminateApproximation())
        }

        return JcRawCatchInst(inst.owner, newThrowable, inst.handler, entries)
    }

    override fun visitJcRawGotoInst(inst: JcRawGotoInst): JcRawInst {
        return JcRawGotoInst(inst.owner, inst.target)
    }

    override fun visitJcRawIfInst(inst: JcRawIfInst): JcRawInst {
        val newCondition = inst.condition.accept(this) as JcRawConditionExpr
        return JcRawIfInst(inst.owner, newCondition, inst.trueBranch, inst.falseBranch)
    }

    override fun visitJcRawSwitchInst(inst: JcRawSwitchInst): JcRawInst {
        val newKey = inst.key.accept(this) as JcRawValue
        val newBranches = inst.branches.mapKeys { it.key.accept(this) as JcRawValue }
        return JcRawSwitchInst(inst.owner, newKey, newBranches, inst.default)
    }

    private fun <T : JcRawBinaryExpr> binaryHandler(
        expr: T,
        constructor: (TypeName, JcRawValue, JcRawValue) -> T
    ): T {
        val newLhv = expr.lhv.accept(this) as JcRawValue
        val newRhv = expr.rhv.accept(this) as JcRawValue

        return constructor(newLhv.typeName.eliminateApproximation(), newLhv, newRhv)
    }

    override fun visitJcRawAddExpr(expr: JcRawAddExpr): JcRawExpr = binaryHandler(expr) { type, lhv, rhv ->
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
        JcRawEqExpr(expr.typeName.eliminateApproximation(), lhv, rhv)
    }

    override fun visitJcRawNeqExpr(expr: JcRawNeqExpr) = binaryHandler(expr) { _, lhv, rhv ->
        JcRawNeqExpr(expr.typeName.eliminateApproximation(), lhv, rhv)
    }

    override fun visitJcRawGeExpr(expr: JcRawGeExpr) = binaryHandler(expr) { _, lhv, rhv ->
        JcRawGeExpr(expr.typeName.eliminateApproximation(), lhv, rhv)
    }

    override fun visitJcRawGtExpr(expr: JcRawGtExpr) = binaryHandler(expr) { _, lhv, rhv ->
        JcRawGtExpr(expr.typeName.eliminateApproximation(), lhv, rhv)
    }

    override fun visitJcRawLeExpr(expr: JcRawLeExpr) = binaryHandler(expr) { _, lhv, rhv ->
        JcRawLeExpr(expr.typeName.eliminateApproximation(), lhv, rhv)
    }

    override fun visitJcRawLtExpr(expr: JcRawLtExpr) = binaryHandler(expr) { _, lhv, rhv ->
        JcRawLtExpr(expr.typeName.eliminateApproximation(), lhv, rhv)
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

    override fun visitJcRawLengthExpr(expr: JcRawLengthExpr): JcRawExpr {
        val newArray = expr.array.accept(this) as JcRawValue
        return JcRawLengthExpr(expr.typeName.eliminateApproximation(), newArray)
    }

    override fun visitJcRawNegExpr(expr: JcRawNegExpr): JcRawExpr {
        val newOperand = expr.operand.accept(this) as JcRawValue
        return JcRawNegExpr(newOperand.typeName.eliminateApproximation(), newOperand)
    }

    override fun visitJcRawCastExpr(expr: JcRawCastExpr): JcRawExpr {
        val newOperand = expr.operand.accept(this) as JcRawValue
        return JcRawCastExpr(expr.typeName.eliminateApproximation(), newOperand)
    }

    override fun visitJcRawNewExpr(expr: JcRawNewExpr): JcRawExpr {
        return expr.eliminateApproximations(expr.typeName) { expr.copy(typeName = it) }
    }

    override fun visitJcRawNewArrayExpr(expr: JcRawNewArrayExpr): JcRawExpr {
        val newDimensions = expr.dimensions.map { it.accept(this) as JcRawValue }
        return JcRawNewArrayExpr(expr.typeName.eliminateApproximation(), newDimensions)
    }

    override fun visitJcRawInstanceOfExpr(expr: JcRawInstanceOfExpr): JcRawExpr {
        val newOperand = expr.operand.accept(this) as JcRawValue
        return JcRawInstanceOfExpr(
            expr.typeName.eliminateApproximation(),
            newOperand,
            expr.targetType.eliminateApproximation()
        )
    }

    private fun BsmHandle.eliminateApproximations(): BsmHandle = copy(
        declaringClass = declaringClass.eliminateApproximation(),
        argTypes = argTypes.map { it.eliminateApproximation() },
        returnType = returnType.eliminateApproximation()
    )

    override fun visitJcRawDynamicCallExpr(expr: JcRawDynamicCallExpr): JcRawExpr {
        with(expr) {
            val newArgs = args.map { it.accept(this@InstSubstitutorForApproximations) as JcRawValue }

            return JcRawDynamicCallExpr(
                bsm.eliminateApproximations(),
                bsmArgs.map { arg ->
                    when (arg) {
                        is BsmDoubleArg -> arg
                        is BsmFloatArg -> arg
                        is BsmHandle -> arg.eliminateApproximations()
                        is BsmIntArg -> arg
                        is BsmLongArg -> arg
                        is BsmMethodTypeArg -> arg.copy(
                            arg.argumentTypes.map { it.eliminateApproximation() },
                            arg.returnType.eliminateApproximation()
                        )

                        is BsmStringArg -> arg
                        is BsmTypeArg -> arg.copy(arg.typeName.eliminateApproximation())
                    }
                },
                callSiteMethodName,
                callSiteArgTypes.map { it.eliminateApproximation() },
                callSiteReturnType.eliminateApproximation(),
                newArgs
            )
        }
    }

    override fun visitJcRawVirtualCallExpr(expr: JcRawVirtualCallExpr): JcRawExpr {
        val newInstance = expr.instance.accept(this) as JcRawValue
        val newArgs = expr.args.map { it.accept(this) as JcRawValue }

        return with(expr) {
            JcRawVirtualCallExpr(
                declaringClass.eliminateApproximation(),
                methodName,
                argumentTypes.map { it.eliminateApproximation() },
                returnType.eliminateApproximation(),
                newInstance,
                newArgs
            )
        }
    }

    override fun visitJcRawInterfaceCallExpr(expr: JcRawInterfaceCallExpr): JcRawExpr {
        val newInstance = expr.instance.accept(this) as JcRawValue
        val newArgs = expr.args.map { it.accept(this) as JcRawValue }

        return with(expr) {
            JcRawInterfaceCallExpr(
                declaringClass.eliminateApproximation(),
                methodName,
                argumentTypes.map { it.eliminateApproximation() },
                returnType.eliminateApproximation(),
                newInstance,
                newArgs
            )
        }
    }

    override fun visitJcRawStaticCallExpr(expr: JcRawStaticCallExpr): JcRawExpr {
        val newArgs = expr.args.map { it.accept(this) as JcRawValue }

        return with(expr) {
            JcRawStaticCallExpr(
                declaringClass.eliminateApproximation(),
                methodName,
                argumentTypes.map { it.eliminateApproximation() },
                returnType.eliminateApproximation(),
                newArgs,
                isInterfaceMethodCall
            )
        }
    }

    override fun visitJcRawSpecialCallExpr(expr: JcRawSpecialCallExpr): JcRawExpr {
        val newInstance = expr.instance.accept(this) as JcRawValue
        val newArgs = expr.args.map { it.accept(this) as JcRawValue }

        return with(expr) {
            JcRawSpecialCallExpr(
                declaringClass.eliminateApproximation(),
                methodName,
                argumentTypes.map { it.eliminateApproximation() },
                returnType.eliminateApproximation(),
                newInstance,
                newArgs
            )
        }
    }

    override fun visitJcRawThis(value: JcRawThis): JcRawExpr {
        return value.copy(value.typeName.eliminateApproximation())
    }

    override fun visitJcRawArgument(value: JcRawArgument): JcRawExpr {
        return value.eliminateApproximations(value.typeName) { value.copy(typeName = it) }
    }

    private fun <T : JcRawExpr> T.eliminateApproximations(typeName: TypeName, constructor: (TypeName) -> T): T {
        val className = typeName.typeName.toApproximationName()
        val originalClassName = findOriginalByApproximationOrNull(className) ?: return this
        return constructor(TypeNameImpl(originalClassName))
    }

    override fun visitJcRawLocalVar(value: JcRawLocalVar): JcRawExpr {
        return value.copy(typeName = value.typeName.eliminateApproximation())
    }

    override fun visitJcRawFieldRef(value: JcRawFieldRef): JcRawExpr {
        val newInstance = value.instance?.accept(this) as? JcRawValue
        return JcRawFieldRef(
            newInstance,
            value.declaringClass.eliminateApproximation(),
            value.fieldName,
            value.typeName.eliminateApproximation()
        )
    }

    override fun visitJcRawArrayAccess(value: JcRawArrayAccess): JcRawExpr {
        val newArray = value.array.accept(this) as JcRawValue
        val newIndex = value.index.accept(this) as JcRawValue

        return JcRawArrayAccess(newArray, newIndex, value.typeName.eliminateApproximation())
    }

    override fun visitJcRawBool(value: JcRawBool): JcRawExpr {
        return value.copy(typeName = value.typeName.eliminateApproximation())
    }

    override fun visitJcRawByte(value: JcRawByte): JcRawExpr {
        return value.copy(typeName = value.typeName.eliminateApproximation())
    }

    override fun visitJcRawChar(value: JcRawChar): JcRawExpr {
        return value.copy(typeName = value.typeName.eliminateApproximation())
    }

    override fun visitJcRawShort(value: JcRawShort): JcRawExpr {
        return value.copy(typeName = value.typeName.eliminateApproximation())
    }

    override fun visitJcRawInt(value: JcRawInt): JcRawExpr {
        return value.copy(typeName = value.typeName.eliminateApproximation())
    }

    override fun visitJcRawLong(value: JcRawLong): JcRawExpr {
        return value.copy(typeName = value.typeName.eliminateApproximation())
    }

    override fun visitJcRawFloat(value: JcRawFloat): JcRawExpr {
        return value.copy(typeName = value.typeName.eliminateApproximation())
    }

    override fun visitJcRawDouble(value: JcRawDouble): JcRawExpr {
        return value.copy(typeName = value.typeName.eliminateApproximation())
    }

    override fun visitJcRawNullConstant(value: JcRawNullConstant): JcRawExpr {
        return value.copy(typeName = value.typeName.eliminateApproximation())
    }

    override fun visitJcRawStringConstant(value: JcRawStringConstant): JcRawExpr {
        return value.eliminateApproximations(value.typeName) { value.copy(typeName = it) }
    }

    override fun visitJcRawClassConstant(value: JcRawClassConstant): JcRawExpr {
        return JcRawClassConstant(
            value.className.eliminateApproximation(),
            value.typeName.eliminateApproximation()
        )
    }

    override fun visitJcRawMethodConstant(value: JcRawMethodConstant): JcRawExpr {
        return with(value) {
            JcRawMethodConstant(
                declaringClass.eliminateApproximation(),
                name,
                argumentTypes.map { it.eliminateApproximation() },
                returnType.eliminateApproximation(),
                typeName.eliminateApproximation()
            )
        }
    }

    override fun visitJcRawMethodType(value: JcRawMethodType): JcRawExpr {
        return with(value) {
            JcRawMethodType(
                argumentTypes.map { it.eliminateApproximation() },
                returnType.eliminateApproximation(),
                typeName.eliminateApproximation()
            )
        }
    }
}