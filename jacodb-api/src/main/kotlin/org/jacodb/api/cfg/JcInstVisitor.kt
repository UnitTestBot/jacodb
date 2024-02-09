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

interface JcInstVisitor<T> {
    fun visitJcAssignInst(inst: JcAssignInst): T
    fun visitJcEnterMonitorInst(inst: JcEnterMonitorInst): T
    fun visitJcExitMonitorInst(inst: JcExitMonitorInst): T
    fun visitJcCallInst(inst: JcCallInst): T
    fun visitJcReturnInst(inst: JcReturnInst): T
    fun visitJcThrowInst(inst: JcThrowInst): T
    fun visitJcCatchInst(inst: JcCatchInst): T
    fun visitJcGotoInst(inst: JcGotoInst): T
    fun visitJcIfInst(inst: JcIfInst): T
    fun visitJcSwitchInst(inst: JcSwitchInst): T
    fun visitExternalJcInst(inst: JcInst): T

}

@JvmDefaultWithoutCompatibility
interface DefaultJcInstVisitor<T> : JcInstVisitor<T> {
    val defaultInstHandler: (JcInst) -> T

    override fun visitJcAssignInst(inst: JcAssignInst): T = defaultInstHandler(inst)

    override fun visitJcEnterMonitorInst(inst: JcEnterMonitorInst): T = defaultInstHandler(inst)

    override fun visitJcExitMonitorInst(inst: JcExitMonitorInst): T = defaultInstHandler(inst)

    override fun visitJcCallInst(inst: JcCallInst): T = defaultInstHandler(inst)

    override fun visitJcReturnInst(inst: JcReturnInst): T = defaultInstHandler(inst)

    override fun visitJcThrowInst(inst: JcThrowInst): T = defaultInstHandler(inst)

    override fun visitJcCatchInst(inst: JcCatchInst): T = defaultInstHandler(inst)

    override fun visitJcGotoInst(inst: JcGotoInst): T = defaultInstHandler(inst)

    override fun visitJcIfInst(inst: JcIfInst): T = defaultInstHandler(inst)

    override fun visitJcSwitchInst(inst: JcSwitchInst): T = defaultInstHandler(inst)

    override fun visitExternalJcInst(inst: JcInst): T = defaultInstHandler(inst)
}

interface JcExprVisitor<T> {
    fun visitJcAddExpr(expr: JcAddExpr): T
    fun visitJcAndExpr(expr: JcAndExpr): T
    fun visitJcCmpExpr(expr: JcCmpExpr): T
    fun visitJcCmpgExpr(expr: JcCmpgExpr): T
    fun visitJcCmplExpr(expr: JcCmplExpr): T
    fun visitJcDivExpr(expr: JcDivExpr): T
    fun visitJcMulExpr(expr: JcMulExpr): T
    fun visitJcEqExpr(expr: JcEqExpr): T
    fun visitJcNeqExpr(expr: JcNeqExpr): T
    fun visitJcGeExpr(expr: JcGeExpr): T
    fun visitJcGtExpr(expr: JcGtExpr): T
    fun visitJcLeExpr(expr: JcLeExpr): T
    fun visitJcLtExpr(expr: JcLtExpr): T
    fun visitJcOrExpr(expr: JcOrExpr): T
    fun visitJcRemExpr(expr: JcRemExpr): T
    fun visitJcShlExpr(expr: JcShlExpr): T
    fun visitJcShrExpr(expr: JcShrExpr): T
    fun visitJcSubExpr(expr: JcSubExpr): T
    fun visitJcUshrExpr(expr: JcUshrExpr): T
    fun visitJcXorExpr(expr: JcXorExpr): T
    fun visitJcLengthExpr(expr: JcLengthExpr): T
    fun visitJcNegExpr(expr: JcNegExpr): T
    fun visitJcCastExpr(expr: JcCastExpr): T
    fun visitJcNewExpr(expr: JcNewExpr): T
    fun visitJcNewArrayExpr(expr: JcNewArrayExpr): T
    fun visitJcInstanceOfExpr(expr: JcInstanceOfExpr): T
    fun visitJcLambdaExpr(expr: JcLambdaExpr): T
    fun visitJcDynamicCallExpr(expr: JcDynamicCallExpr): T
    fun visitJcVirtualCallExpr(expr: JcVirtualCallExpr): T
    fun visitJcStaticCallExpr(expr: JcStaticCallExpr): T
    fun visitJcSpecialCallExpr(expr: JcSpecialCallExpr): T

    fun visitJcThis(value: JcThis): T
    fun visitJcArgument(value: JcArgument): T
    fun visitJcLocalVar(value: JcLocalVar): T
    fun visitJcFieldRef(value: JcFieldRef): T
    fun visitJcArrayAccess(value: JcArrayAccess): T
    fun visitJcBool(value: JcBool): T
    fun visitJcByte(value: JcByte): T
    fun visitJcChar(value: JcChar): T
    fun visitJcShort(value: JcShort): T
    fun visitJcInt(value: JcInt): T
    fun visitJcLong(value: JcLong): T
    fun visitJcFloat(value: JcFloat): T
    fun visitJcDouble(value: JcDouble): T
    fun visitJcNullConstant(value: JcNullConstant): T
    fun visitJcStringConstant(value: JcStringConstant): T
    fun visitJcClassConstant(value: JcClassConstant): T
    fun visitJcMethodConstant(value: JcMethodConstant): T
    fun visitJcMethodType(value: JcMethodType): T
    fun visitJcPhiExpr(expr: JcPhiExpr): T

    fun visitExternalJcExpr(expr: JcExpr): T
}

@JvmDefaultWithoutCompatibility
interface DefaultJcExprVisitor<T> : JcExprVisitor<T> {
    val defaultExprHandler: (JcExpr) -> T

    override fun visitJcAddExpr(expr: JcAddExpr): T = defaultExprHandler(expr)

    override fun visitJcAndExpr(expr: JcAndExpr): T = defaultExprHandler(expr)

    override fun visitJcCmpExpr(expr: JcCmpExpr): T = defaultExprHandler(expr)

    override fun visitJcCmpgExpr(expr: JcCmpgExpr): T = defaultExprHandler(expr)

    override fun visitJcCmplExpr(expr: JcCmplExpr): T = defaultExprHandler(expr)

    override fun visitJcDivExpr(expr: JcDivExpr): T = defaultExprHandler(expr)

    override fun visitJcMulExpr(expr: JcMulExpr): T = defaultExprHandler(expr)

    override fun visitJcEqExpr(expr: JcEqExpr): T = defaultExprHandler(expr)

    override fun visitJcNeqExpr(expr: JcNeqExpr): T = defaultExprHandler(expr)

    override fun visitJcGeExpr(expr: JcGeExpr): T = defaultExprHandler(expr)

    override fun visitJcGtExpr(expr: JcGtExpr): T = defaultExprHandler(expr)

    override fun visitJcLeExpr(expr: JcLeExpr): T = defaultExprHandler(expr)

    override fun visitJcLtExpr(expr: JcLtExpr): T = defaultExprHandler(expr)

    override fun visitJcOrExpr(expr: JcOrExpr): T = defaultExprHandler(expr)

    override fun visitJcRemExpr(expr: JcRemExpr): T = defaultExprHandler(expr)

    override fun visitJcShlExpr(expr: JcShlExpr): T = defaultExprHandler(expr)

    override fun visitJcShrExpr(expr: JcShrExpr): T = defaultExprHandler(expr)

    override fun visitJcSubExpr(expr: JcSubExpr): T = defaultExprHandler(expr)

    override fun visitJcUshrExpr(expr: JcUshrExpr): T = defaultExprHandler(expr)

    override fun visitJcXorExpr(expr: JcXorExpr): T = defaultExprHandler(expr)

    override fun visitJcLengthExpr(expr: JcLengthExpr): T = defaultExprHandler(expr)

    override fun visitJcNegExpr(expr: JcNegExpr): T = defaultExprHandler(expr)

    override fun visitJcCastExpr(expr: JcCastExpr): T = defaultExprHandler(expr)

    override fun visitJcNewExpr(expr: JcNewExpr): T = defaultExprHandler(expr)

    override fun visitJcNewArrayExpr(expr: JcNewArrayExpr): T = defaultExprHandler(expr)

    override fun visitJcInstanceOfExpr(expr: JcInstanceOfExpr): T = defaultExprHandler(expr)

    override fun visitJcLambdaExpr(expr: JcLambdaExpr): T = defaultExprHandler(expr)

    override fun visitJcDynamicCallExpr(expr: JcDynamicCallExpr): T = defaultExprHandler(expr)

    override fun visitJcVirtualCallExpr(expr: JcVirtualCallExpr): T = defaultExprHandler(expr)

    override fun visitJcStaticCallExpr(expr: JcStaticCallExpr): T = defaultExprHandler(expr)

    override fun visitJcSpecialCallExpr(expr: JcSpecialCallExpr): T = defaultExprHandler(expr)

    override fun visitJcThis(value: JcThis): T = defaultExprHandler(value)

    override fun visitJcArgument(value: JcArgument): T = defaultExprHandler(value)

    override fun visitJcLocalVar(value: JcLocalVar): T = defaultExprHandler(value)

    override fun visitJcFieldRef(value: JcFieldRef): T = defaultExprHandler(value)

    override fun visitJcArrayAccess(value: JcArrayAccess): T = defaultExprHandler(value)

    override fun visitJcBool(value: JcBool): T = defaultExprHandler(value)

    override fun visitJcByte(value: JcByte): T = defaultExprHandler(value)

    override fun visitJcChar(value: JcChar): T = defaultExprHandler(value)

    override fun visitJcShort(value: JcShort): T = defaultExprHandler(value)

    override fun visitJcInt(value: JcInt): T = defaultExprHandler(value)

    override fun visitJcLong(value: JcLong): T = defaultExprHandler(value)

    override fun visitJcFloat(value: JcFloat): T = defaultExprHandler(value)

    override fun visitJcDouble(value: JcDouble): T = defaultExprHandler(value)

    override fun visitJcNullConstant(value: JcNullConstant): T = defaultExprHandler(value)

    override fun visitJcStringConstant(value: JcStringConstant): T = defaultExprHandler(value)

    override fun visitJcClassConstant(value: JcClassConstant): T = defaultExprHandler(value)

    override fun visitJcMethodConstant(value: JcMethodConstant): T = defaultExprHandler(value)

    override fun visitJcMethodType(value: JcMethodType): T = defaultExprHandler(value)

    override fun visitJcPhiExpr(expr: JcPhiExpr): T = defaultExprHandler(expr)

    override fun visitExternalJcExpr(expr: JcExpr): T = defaultExprHandler(expr)
}
