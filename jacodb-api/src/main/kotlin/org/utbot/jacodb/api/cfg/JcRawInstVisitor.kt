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

package org.utbot.jacodb.api.cfg

interface JcRawInstVisitor<T> {
    fun visitJcRawAssignInst(inst: JcRawAssignInst): T
    fun visitJcRawEnterMonitorInst(inst: JcRawEnterMonitorInst): T
    fun visitJcRawExitMonitorInst(inst: JcRawExitMonitorInst): T
    fun visitJcRawCallInst(inst: JcRawCallInst): T
    fun visitJcRawLabelInst(inst: JcRawLabelInst): T
    fun visitJcRawLineNumberInst(inst: JcRawLineNumberInst): T
    fun visitJcRawReturnInst(inst: JcRawReturnInst): T
    fun visitJcRawThrowInst(inst: JcRawThrowInst): T
    fun visitJcRawCatchInst(inst: JcRawCatchInst): T
    fun visitJcRawGotoInst(inst: JcRawGotoInst): T
    fun visitJcRawIfInst(inst: JcRawIfInst): T
    fun visitJcRawSwitchInst(inst: JcRawSwitchInst): T
}

@JvmDefaultWithoutCompatibility
interface DefaultJcRawInstVisitor<T> : JcRawInstVisitor<T> {
    val defaultInstHandler: (JcRawInst) -> T

    @JvmDefault
    override fun visitJcRawAssignInst(inst: JcRawAssignInst): T = defaultInstHandler(inst)
    @JvmDefault
    override fun visitJcRawEnterMonitorInst(inst: JcRawEnterMonitorInst): T = defaultInstHandler(inst)
    @JvmDefault
    override fun visitJcRawExitMonitorInst(inst: JcRawExitMonitorInst): T = defaultInstHandler(inst)
    @JvmDefault
    override fun visitJcRawCallInst(inst: JcRawCallInst): T = defaultInstHandler(inst)
    @JvmDefault
    override fun visitJcRawLabelInst(inst: JcRawLabelInst): T = defaultInstHandler(inst)
    @JvmDefault
    override fun visitJcRawLineNumberInst(inst: JcRawLineNumberInst): T = defaultInstHandler(inst)
    @JvmDefault
    override fun visitJcRawReturnInst(inst: JcRawReturnInst): T = defaultInstHandler(inst)
    @JvmDefault
    override fun visitJcRawThrowInst(inst: JcRawThrowInst): T = defaultInstHandler(inst)
    @JvmDefault
    override fun visitJcRawCatchInst(inst: JcRawCatchInst): T = defaultInstHandler(inst)
    @JvmDefault
    override fun visitJcRawGotoInst(inst: JcRawGotoInst): T = defaultInstHandler(inst)
    @JvmDefault
    override fun visitJcRawIfInst(inst: JcRawIfInst): T = defaultInstHandler(inst)
    @JvmDefault
    override fun visitJcRawSwitchInst(inst: JcRawSwitchInst): T = defaultInstHandler(inst)

}

interface JcRawExprVisitor<T> {
    fun visitJcRawAddExpr(expr: JcRawAddExpr): T
    fun visitJcRawAndExpr(expr: JcRawAndExpr): T
    fun visitJcRawCmpExpr(expr: JcRawCmpExpr): T
    fun visitJcRawCmpgExpr(expr: JcRawCmpgExpr): T
    fun visitJcRawCmplExpr(expr: JcRawCmplExpr): T
    fun visitJcRawDivExpr(expr: JcRawDivExpr): T
    fun visitJcRawMulExpr(expr: JcRawMulExpr): T
    fun visitJcRawEqExpr(expr: JcRawEqExpr): T
    fun visitJcRawNeqExpr(expr: JcRawNeqExpr): T
    fun visitJcRawGeExpr(expr: JcRawGeExpr): T
    fun visitJcRawGtExpr(expr: JcRawGtExpr): T
    fun visitJcRawLeExpr(expr: JcRawLeExpr): T
    fun visitJcRawLtExpr(expr: JcRawLtExpr): T
    fun visitJcRawOrExpr(expr: JcRawOrExpr): T
    fun visitJcRawRemExpr(expr: JcRawRemExpr): T
    fun visitJcRawShlExpr(expr: JcRawShlExpr): T
    fun visitJcRawShrExpr(expr: JcRawShrExpr): T
    fun visitJcRawSubExpr(expr: JcRawSubExpr): T
    fun visitJcRawUshrExpr(expr: JcRawUshrExpr): T
    fun visitJcRawXorExpr(expr: JcRawXorExpr): T
    fun visitJcRawLengthExpr(expr: JcRawLengthExpr): T
    fun visitJcRawNegExpr(expr: JcRawNegExpr): T
    fun visitJcRawCastExpr(expr: JcRawCastExpr): T
    fun visitJcRawNewExpr(expr: JcRawNewExpr): T
    fun visitJcRawNewArrayExpr(expr: JcRawNewArrayExpr): T
    fun visitJcRawInstanceOfExpr(expr: JcRawInstanceOfExpr): T
    fun visitJcRawDynamicCallExpr(expr: JcRawDynamicCallExpr): T
    fun visitJcRawVirtualCallExpr(expr: JcRawVirtualCallExpr): T
    fun visitJcRawInterfaceCallExpr(expr: JcRawInterfaceCallExpr): T
    fun visitJcRawStaticCallExpr(expr: JcRawStaticCallExpr): T
    fun visitJcRawSpecialCallExpr(expr: JcRawSpecialCallExpr): T

    fun visitJcRawThis(value: JcRawThis): T
    fun visitJcRawArgument(value: JcRawArgument): T
    fun visitJcRawLocalVar(value: JcRawLocalVar): T
    fun visitJcRawFieldRef(value: JcRawFieldRef): T
    fun visitJcRawArrayAccess(value: JcRawArrayAccess): T
    fun visitJcRawBool(value: JcRawBool): T
    fun visitJcRawByte(value: JcRawByte): T
    fun visitJcRawChar(value: JcRawChar): T
    fun visitJcRawShort(value: JcRawShort): T
    fun visitJcRawInt(value: JcRawInt): T
    fun visitJcRawLong(value: JcRawLong): T
    fun visitJcRawFloat(value: JcRawFloat): T
    fun visitJcRawDouble(value: JcRawDouble): T
    fun visitJcRawNullConstant(value: JcRawNullConstant): T
    fun visitJcRawStringConstant(value: JcRawStringConstant): T
    fun visitJcRawClassConstant(value: JcRawClassConstant): T
    fun visitJcRawMethodConstant(value: JcRawMethodConstant): T
}

@JvmDefaultWithoutCompatibility
interface DefaultJcRawExprVisitor<T> : JcRawExprVisitor<T> {
    val defaultExprHandler: (JcRawExpr) -> T

    @JvmDefault
    override fun visitJcRawAddExpr(expr: JcRawAddExpr): T = defaultExprHandler(expr)
    @JvmDefault
    override fun visitJcRawAndExpr(expr: JcRawAndExpr): T = defaultExprHandler(expr)
    @JvmDefault
    override fun visitJcRawCmpExpr(expr: JcRawCmpExpr): T = defaultExprHandler(expr)
    @JvmDefault
    override fun visitJcRawCmpgExpr(expr: JcRawCmpgExpr): T = defaultExprHandler(expr)
    @JvmDefault
    override fun visitJcRawCmplExpr(expr: JcRawCmplExpr): T = defaultExprHandler(expr)
    @JvmDefault
    override fun visitJcRawDivExpr(expr: JcRawDivExpr): T = defaultExprHandler(expr)
    @JvmDefault
    override fun visitJcRawMulExpr(expr: JcRawMulExpr): T = defaultExprHandler(expr)
    @JvmDefault
    override fun visitJcRawEqExpr(expr: JcRawEqExpr): T = defaultExprHandler(expr)
    @JvmDefault
    override fun visitJcRawNeqExpr(expr: JcRawNeqExpr): T = defaultExprHandler(expr)
    @JvmDefault
    override fun visitJcRawGeExpr(expr: JcRawGeExpr): T = defaultExprHandler(expr)
    @JvmDefault
    override fun visitJcRawGtExpr(expr: JcRawGtExpr): T = defaultExprHandler(expr)
    @JvmDefault
    override fun visitJcRawLeExpr(expr: JcRawLeExpr): T = defaultExprHandler(expr)
    @JvmDefault
    override fun visitJcRawLtExpr(expr: JcRawLtExpr): T = defaultExprHandler(expr)
    @JvmDefault
    override fun visitJcRawOrExpr(expr: JcRawOrExpr): T = defaultExprHandler(expr)
    @JvmDefault
    override fun visitJcRawRemExpr(expr: JcRawRemExpr): T = defaultExprHandler(expr)
    @JvmDefault
    override fun visitJcRawShlExpr(expr: JcRawShlExpr): T = defaultExprHandler(expr)
    @JvmDefault
    override fun visitJcRawShrExpr(expr: JcRawShrExpr): T = defaultExprHandler(expr)
    @JvmDefault
    override fun visitJcRawSubExpr(expr: JcRawSubExpr): T = defaultExprHandler(expr)
    @JvmDefault
    override fun visitJcRawUshrExpr(expr: JcRawUshrExpr): T = defaultExprHandler(expr)
    @JvmDefault
    override fun visitJcRawXorExpr(expr: JcRawXorExpr): T = defaultExprHandler(expr)
    @JvmDefault
    override fun visitJcRawLengthExpr(expr: JcRawLengthExpr): T = defaultExprHandler(expr)
    @JvmDefault
    override fun visitJcRawNegExpr(expr: JcRawNegExpr): T = defaultExprHandler(expr)
    @JvmDefault
    override fun visitJcRawCastExpr(expr: JcRawCastExpr): T = defaultExprHandler(expr)
    @JvmDefault
    override fun visitJcRawNewExpr(expr: JcRawNewExpr): T = defaultExprHandler(expr)
    @JvmDefault
    override fun visitJcRawNewArrayExpr(expr: JcRawNewArrayExpr): T = defaultExprHandler(expr)
    @JvmDefault
    override fun visitJcRawInstanceOfExpr(expr: JcRawInstanceOfExpr): T = defaultExprHandler(expr)
    @JvmDefault
    override fun visitJcRawDynamicCallExpr(expr: JcRawDynamicCallExpr): T = defaultExprHandler(expr)
    @JvmDefault
    override fun visitJcRawVirtualCallExpr(expr: JcRawVirtualCallExpr): T = defaultExprHandler(expr)
    @JvmDefault
    override fun visitJcRawInterfaceCallExpr(expr: JcRawInterfaceCallExpr): T = defaultExprHandler(expr)
    @JvmDefault
    override fun visitJcRawStaticCallExpr(expr: JcRawStaticCallExpr): T = defaultExprHandler(expr)
    @JvmDefault
    override fun visitJcRawSpecialCallExpr(expr: JcRawSpecialCallExpr): T = defaultExprHandler(expr)
    @JvmDefault
    override fun visitJcRawThis(value: JcRawThis): T = defaultExprHandler(value)
    @JvmDefault
    override fun visitJcRawArgument(value: JcRawArgument): T = defaultExprHandler(value)
    @JvmDefault
    override fun visitJcRawLocalVar(value: JcRawLocalVar): T = defaultExprHandler(value)
    @JvmDefault
    override fun visitJcRawFieldRef(value: JcRawFieldRef): T = defaultExprHandler(value)
    @JvmDefault
    override fun visitJcRawArrayAccess(value: JcRawArrayAccess): T = defaultExprHandler(value)
    @JvmDefault
    override fun visitJcRawBool(value: JcRawBool): T = defaultExprHandler(value)
    @JvmDefault
    override fun visitJcRawByte(value: JcRawByte): T = defaultExprHandler(value)
    @JvmDefault
    override fun visitJcRawChar(value: JcRawChar): T = defaultExprHandler(value)
    @JvmDefault
    override fun visitJcRawShort(value: JcRawShort): T = defaultExprHandler(value)
    @JvmDefault
    override fun visitJcRawInt(value: JcRawInt): T = defaultExprHandler(value)
    @JvmDefault
    override fun visitJcRawLong(value: JcRawLong): T = defaultExprHandler(value)
    @JvmDefault
    override fun visitJcRawFloat(value: JcRawFloat): T = defaultExprHandler(value)
    @JvmDefault
    override fun visitJcRawDouble(value: JcRawDouble): T = defaultExprHandler(value)
    @JvmDefault
    override fun visitJcRawNullConstant(value: JcRawNullConstant): T = defaultExprHandler(value)
    @JvmDefault
    override fun visitJcRawStringConstant(value: JcRawStringConstant): T = defaultExprHandler(value)
    @JvmDefault
    override fun visitJcRawClassConstant(value: JcRawClassConstant): T = defaultExprHandler(value)
    @JvmDefault
    override fun visitJcRawMethodConstant(value: JcRawMethodConstant): T = defaultExprHandler(value)
}
