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

package org.utbot.jcdb.api.cfg

import org.utbot.jcdb.api.JcRawAddExpr
import org.utbot.jcdb.api.JcRawAndExpr
import org.utbot.jcdb.api.JcRawArgument
import org.utbot.jcdb.api.JcRawArrayAccess
import org.utbot.jcdb.api.JcRawAssignInst
import org.utbot.jcdb.api.JcRawBool
import org.utbot.jcdb.api.JcRawByte
import org.utbot.jcdb.api.JcRawCallInst
import org.utbot.jcdb.api.JcRawCastExpr
import org.utbot.jcdb.api.JcRawCatchInst
import org.utbot.jcdb.api.JcRawChar
import org.utbot.jcdb.api.JcRawClassConstant
import org.utbot.jcdb.api.JcRawCmpExpr
import org.utbot.jcdb.api.JcRawCmpgExpr
import org.utbot.jcdb.api.JcRawCmplExpr
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
import org.utbot.jcdb.api.JcRawSpecialCallExpr
import org.utbot.jcdb.api.JcRawStaticCallExpr
import org.utbot.jcdb.api.JcRawStringConstant
import org.utbot.jcdb.api.JcRawSubExpr
import org.utbot.jcdb.api.JcRawSwitchInst
import org.utbot.jcdb.api.JcRawThis
import org.utbot.jcdb.api.JcRawThrowInst
import org.utbot.jcdb.api.JcRawUshrExpr
import org.utbot.jcdb.api.JcRawVirtualCallExpr
import org.utbot.jcdb.api.JcRawXorExpr

interface JcRawInstVisitor<T> {
    fun visitJcRawAssignInst(inst: JcRawAssignInst): T
    fun visitJcRawEnterMonitorInst(inst: JcRawEnterMonitorInst): T
    fun visitJcRawExitMonitorInst(inst: JcRawExitMonitorInst): T
    fun visitJcRawCallInst(inst: JcRawCallInst): T
    fun visitJcRawLabelInst(inst: JcRawLabelInst): T
    fun visitJcRawReturnInst(inst: JcRawReturnInst): T
    fun visitJcRawThrowInst(inst: JcRawThrowInst): T
    fun visitJcRawCatchInst(inst: JcRawCatchInst): T
    fun visitJcRawGotoInst(inst: JcRawGotoInst): T
    fun visitJcRawIfInst(inst: JcRawIfInst): T
    fun visitJcRawSwitchInst(inst: JcRawSwitchInst): T
}

interface DefaultJcRawInstVisitor<T> : JcRawInstVisitor<T> {
    val defaultInstHandler: (JcRawInst) -> T

    override fun visitJcRawAssignInst(inst: JcRawAssignInst): T = defaultInstHandler(inst)
    override fun visitJcRawEnterMonitorInst(inst: JcRawEnterMonitorInst): T = defaultInstHandler(inst)
    override fun visitJcRawExitMonitorInst(inst: JcRawExitMonitorInst): T = defaultInstHandler(inst)
    override fun visitJcRawCallInst(inst: JcRawCallInst): T = defaultInstHandler(inst)
    override fun visitJcRawLabelInst(inst: JcRawLabelInst): T = defaultInstHandler(inst)
    override fun visitJcRawReturnInst(inst: JcRawReturnInst): T = defaultInstHandler(inst)
    override fun visitJcRawThrowInst(inst: JcRawThrowInst): T = defaultInstHandler(inst)
    override fun visitJcRawCatchInst(inst: JcRawCatchInst): T = defaultInstHandler(inst)
    override fun visitJcRawGotoInst(inst: JcRawGotoInst): T = defaultInstHandler(inst)
    override fun visitJcRawIfInst(inst: JcRawIfInst): T = defaultInstHandler(inst)
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
    fun visitJcRawLocal(value: JcRawLocal): T
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


interface DefaultJcRawExprVisitor<T> : JcRawExprVisitor<T> {
    val defaultExprHandler: (JcRawExpr) -> T

    override fun visitJcRawAddExpr(expr: JcRawAddExpr): T = defaultExprHandler(expr)
    override fun visitJcRawAndExpr(expr: JcRawAndExpr): T = defaultExprHandler(expr)
    override fun visitJcRawCmpExpr(expr: JcRawCmpExpr): T = defaultExprHandler(expr)
    override fun visitJcRawCmpgExpr(expr: JcRawCmpgExpr): T = defaultExprHandler(expr)
    override fun visitJcRawCmplExpr(expr: JcRawCmplExpr): T = defaultExprHandler(expr)
    override fun visitJcRawDivExpr(expr: JcRawDivExpr): T = defaultExprHandler(expr)
    override fun visitJcRawMulExpr(expr: JcRawMulExpr): T = defaultExprHandler(expr)
    override fun visitJcRawEqExpr(expr: JcRawEqExpr): T = defaultExprHandler(expr)
    override fun visitJcRawNeqExpr(expr: JcRawNeqExpr): T = defaultExprHandler(expr)
    override fun visitJcRawGeExpr(expr: JcRawGeExpr): T = defaultExprHandler(expr)
    override fun visitJcRawGtExpr(expr: JcRawGtExpr): T = defaultExprHandler(expr)
    override fun visitJcRawLeExpr(expr: JcRawLeExpr): T = defaultExprHandler(expr)
    override fun visitJcRawLtExpr(expr: JcRawLtExpr): T = defaultExprHandler(expr)
    override fun visitJcRawOrExpr(expr: JcRawOrExpr): T = defaultExprHandler(expr)
    override fun visitJcRawRemExpr(expr: JcRawRemExpr): T = defaultExprHandler(expr)
    override fun visitJcRawShlExpr(expr: JcRawShlExpr): T = defaultExprHandler(expr)
    override fun visitJcRawShrExpr(expr: JcRawShrExpr): T = defaultExprHandler(expr)
    override fun visitJcRawSubExpr(expr: JcRawSubExpr): T = defaultExprHandler(expr)
    override fun visitJcRawUshrExpr(expr: JcRawUshrExpr): T = defaultExprHandler(expr)
    override fun visitJcRawXorExpr(expr: JcRawXorExpr): T = defaultExprHandler(expr)
    override fun visitJcRawLengthExpr(expr: JcRawLengthExpr): T = defaultExprHandler(expr)
    override fun visitJcRawNegExpr(expr: JcRawNegExpr): T = defaultExprHandler(expr)
    override fun visitJcRawCastExpr(expr: JcRawCastExpr): T = defaultExprHandler(expr)
    override fun visitJcRawNewExpr(expr: JcRawNewExpr): T = defaultExprHandler(expr)
    override fun visitJcRawNewArrayExpr(expr: JcRawNewArrayExpr): T = defaultExprHandler(expr)
    override fun visitJcRawInstanceOfExpr(expr: JcRawInstanceOfExpr): T = defaultExprHandler(expr)
    override fun visitJcRawDynamicCallExpr(expr: JcRawDynamicCallExpr): T = defaultExprHandler(expr)
    override fun visitJcRawVirtualCallExpr(expr: JcRawVirtualCallExpr): T = defaultExprHandler(expr)
    override fun visitJcRawInterfaceCallExpr(expr: JcRawInterfaceCallExpr): T = defaultExprHandler(expr)
    override fun visitJcRawStaticCallExpr(expr: JcRawStaticCallExpr): T = defaultExprHandler(expr)
    override fun visitJcRawSpecialCallExpr(expr: JcRawSpecialCallExpr): T = defaultExprHandler(expr)

    override fun visitJcRawThis(value: JcRawThis): T = defaultExprHandler(value)
    override fun visitJcRawArgument(value: JcRawArgument): T = defaultExprHandler(value)
    override fun visitJcRawLocal(value: JcRawLocal): T = defaultExprHandler(value)
    override fun visitJcRawFieldRef(value: JcRawFieldRef): T = defaultExprHandler(value)
    override fun visitJcRawArrayAccess(value: JcRawArrayAccess): T = defaultExprHandler(value)
    override fun visitJcRawBool(value: JcRawBool): T = defaultExprHandler(value)
    override fun visitJcRawByte(value: JcRawByte): T = defaultExprHandler(value)
    override fun visitJcRawChar(value: JcRawChar): T = defaultExprHandler(value)
    override fun visitJcRawShort(value: JcRawShort): T = defaultExprHandler(value)
    override fun visitJcRawInt(value: JcRawInt): T = defaultExprHandler(value)
    override fun visitJcRawLong(value: JcRawLong): T = defaultExprHandler(value)
    override fun visitJcRawFloat(value: JcRawFloat): T = defaultExprHandler(value)
    override fun visitJcRawDouble(value: JcRawDouble): T = defaultExprHandler(value)
    override fun visitJcRawNullConstant(value: JcRawNullConstant): T = defaultExprHandler(value)
    override fun visitJcRawStringConstant(value: JcRawStringConstant): T = defaultExprHandler(value)
    override fun visitJcRawClassConstant(value: JcRawClassConstant): T = defaultExprHandler(value)
    override fun visitJcRawMethodConstant(value: JcRawMethodConstant): T = defaultExprHandler(value)
}
