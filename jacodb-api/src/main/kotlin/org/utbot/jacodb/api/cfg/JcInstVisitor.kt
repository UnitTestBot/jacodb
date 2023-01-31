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
}

@JvmDefaultWithoutCompatibility
interface DefaultJcInstVisitor<T> : JcInstVisitor<T> {
    val defaultInstHandler: (JcInst) -> T

    @JvmDefault
    override fun visitJcAssignInst(inst: JcAssignInst): T = defaultInstHandler(inst)
    @JvmDefault
    override fun visitJcEnterMonitorInst(inst: JcEnterMonitorInst): T = defaultInstHandler(inst)
    @JvmDefault
    override fun visitJcExitMonitorInst(inst: JcExitMonitorInst): T = defaultInstHandler(inst)
    @JvmDefault
    override fun visitJcCallInst(inst: JcCallInst): T = defaultInstHandler(inst)
    @JvmDefault
    override fun visitJcReturnInst(inst: JcReturnInst): T = defaultInstHandler(inst)
    @JvmDefault
    override fun visitJcThrowInst(inst: JcThrowInst): T = defaultInstHandler(inst)
    @JvmDefault
    override fun visitJcCatchInst(inst: JcCatchInst): T = defaultInstHandler(inst)
    @JvmDefault
    override fun visitJcGotoInst(inst: JcGotoInst): T = defaultInstHandler(inst)
    @JvmDefault
    override fun visitJcIfInst(inst: JcIfInst): T = defaultInstHandler(inst)
    @JvmDefault
    override fun visitJcSwitchInst(inst: JcSwitchInst): T = defaultInstHandler(inst)
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
    fun visitJcPhiExpr(value: JcPhiExpr): T
}


@JvmDefaultWithoutCompatibility
interface DefaultJcExprVisitor<T> : JcExprVisitor<T> {
    val defaultExprHandler: (JcExpr) -> T

    @JvmDefault
    override fun visitJcAddExpr(expr: JcAddExpr): T = defaultExprHandler(expr)
    @JvmDefault
    override fun visitJcAndExpr(expr: JcAndExpr): T = defaultExprHandler(expr)
    @JvmDefault
    override fun visitJcCmpExpr(expr: JcCmpExpr): T = defaultExprHandler(expr)
    @JvmDefault
    override fun visitJcCmpgExpr(expr: JcCmpgExpr): T = defaultExprHandler(expr)
    @JvmDefault
    override fun visitJcCmplExpr(expr: JcCmplExpr): T = defaultExprHandler(expr)
    @JvmDefault
    override fun visitJcDivExpr(expr: JcDivExpr): T = defaultExprHandler(expr)
    @JvmDefault
    override fun visitJcMulExpr(expr: JcMulExpr): T = defaultExprHandler(expr)
    @JvmDefault
    override fun visitJcEqExpr(expr: JcEqExpr): T = defaultExprHandler(expr)
    @JvmDefault
    override fun visitJcNeqExpr(expr: JcNeqExpr): T = defaultExprHandler(expr)
    @JvmDefault
    override fun visitJcGeExpr(expr: JcGeExpr): T = defaultExprHandler(expr)
    @JvmDefault
    override fun visitJcGtExpr(expr: JcGtExpr): T = defaultExprHandler(expr)
    @JvmDefault
    override fun visitJcLeExpr(expr: JcLeExpr): T = defaultExprHandler(expr)
    @JvmDefault
    override fun visitJcLtExpr(expr: JcLtExpr): T = defaultExprHandler(expr)
    @JvmDefault
    override fun visitJcOrExpr(expr: JcOrExpr): T = defaultExprHandler(expr)
    @JvmDefault
    override fun visitJcRemExpr(expr: JcRemExpr): T = defaultExprHandler(expr)
    @JvmDefault
    override fun visitJcShlExpr(expr: JcShlExpr): T = defaultExprHandler(expr)
    @JvmDefault
    override fun visitJcShrExpr(expr: JcShrExpr): T = defaultExprHandler(expr)
    @JvmDefault
    override fun visitJcSubExpr(expr: JcSubExpr): T = defaultExprHandler(expr)
    @JvmDefault
    override fun visitJcUshrExpr(expr: JcUshrExpr): T = defaultExprHandler(expr)
    @JvmDefault
    override fun visitJcXorExpr(expr: JcXorExpr): T = defaultExprHandler(expr)
    @JvmDefault
    override fun visitJcLengthExpr(expr: JcLengthExpr): T = defaultExprHandler(expr)
    @JvmDefault
    override fun visitJcNegExpr(expr: JcNegExpr): T = defaultExprHandler(expr)
    @JvmDefault
    override fun visitJcCastExpr(expr: JcCastExpr): T = defaultExprHandler(expr)
    @JvmDefault
    override fun visitJcNewExpr(expr: JcNewExpr): T = defaultExprHandler(expr)
    @JvmDefault
    override fun visitJcNewArrayExpr(expr: JcNewArrayExpr): T = defaultExprHandler(expr)
    @JvmDefault
    override fun visitJcInstanceOfExpr(expr: JcInstanceOfExpr): T = defaultExprHandler(expr)
    @JvmDefault
    override fun visitJcLambdaExpr(expr: JcLambdaExpr): T = defaultExprHandler(expr)
    @JvmDefault
    override fun visitJcDynamicCallExpr(expr: JcDynamicCallExpr): T = defaultExprHandler(expr)
    @JvmDefault
    override fun visitJcVirtualCallExpr(expr: JcVirtualCallExpr): T = defaultExprHandler(expr)
    @JvmDefault
    override fun visitJcStaticCallExpr(expr: JcStaticCallExpr): T = defaultExprHandler(expr)
    @JvmDefault
    override fun visitJcSpecialCallExpr(expr: JcSpecialCallExpr): T = defaultExprHandler(expr)

    @JvmDefault
    override fun visitJcThis(value: JcThis): T = defaultExprHandler(value)
    @JvmDefault
    override fun visitJcArgument(value: JcArgument): T = defaultExprHandler(value)
    @JvmDefault
    override fun visitJcLocalVar(value: JcLocalVar): T = defaultExprHandler(value)
    @JvmDefault
    override fun visitJcFieldRef(value: JcFieldRef): T = defaultExprHandler(value)
    @JvmDefault
    override fun visitJcArrayAccess(value: JcArrayAccess): T = defaultExprHandler(value)
    @JvmDefault
    override fun visitJcBool(value: JcBool): T = defaultExprHandler(value)
    @JvmDefault
    override fun visitJcByte(value: JcByte): T = defaultExprHandler(value)
    @JvmDefault
    override fun visitJcChar(value: JcChar): T = defaultExprHandler(value)
    @JvmDefault
    override fun visitJcShort(value: JcShort): T = defaultExprHandler(value)
    @JvmDefault
    override fun visitJcInt(value: JcInt): T = defaultExprHandler(value)
    @JvmDefault
    override fun visitJcLong(value: JcLong): T = defaultExprHandler(value)
    @JvmDefault
    override fun visitJcFloat(value: JcFloat): T = defaultExprHandler(value)
    @JvmDefault
    override fun visitJcDouble(value: JcDouble): T = defaultExprHandler(value)
    @JvmDefault
    override fun visitJcNullConstant(value: JcNullConstant): T = defaultExprHandler(value)
    @JvmDefault
    override fun visitJcStringConstant(value: JcStringConstant): T = defaultExprHandler(value)
    @JvmDefault
    override fun visitJcClassConstant(value: JcClassConstant): T = defaultExprHandler(value)
    @JvmDefault
    override fun visitJcMethodConstant(value: JcMethodConstant): T = defaultExprHandler(value)
    @JvmDefault
    override fun visitJcPhiExpr(value: JcPhiExpr): T = defaultExprHandler(value)
}
