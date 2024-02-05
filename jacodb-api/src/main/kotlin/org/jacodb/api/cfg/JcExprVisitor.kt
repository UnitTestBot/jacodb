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

interface JcExprVisitor<out T> {
    fun visitExternalJcExpr(expr: JcExpr): T

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
    fun visitJcPhiExpr(expr: JcPhiExpr): T
    fun visitJcLambdaExpr(expr: JcLambdaExpr): T
    fun visitJcDynamicCallExpr(expr: JcDynamicCallExpr): T
    fun visitJcVirtualCallExpr(expr: JcVirtualCallExpr): T
    fun visitJcStaticCallExpr(expr: JcStaticCallExpr): T
    fun visitJcSpecialCallExpr(expr: JcSpecialCallExpr): T

    fun visitExternalJcValue(value: JcValue): T

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

    interface Default<out T> : JcExprVisitor<T> {
        fun defaultVisitJcExpr(expr: JcExpr): T
        fun defaultVisitJcValue(value: JcValue): T = defaultVisitJcExpr(value)

        override fun visitExternalJcExpr(expr: JcExpr): T = defaultVisitJcExpr(expr)

        override fun visitJcAddExpr(expr: JcAddExpr): T = defaultVisitJcExpr(expr)
        override fun visitJcAndExpr(expr: JcAndExpr): T = defaultVisitJcExpr(expr)
        override fun visitJcCmpExpr(expr: JcCmpExpr): T = defaultVisitJcExpr(expr)
        override fun visitJcCmpgExpr(expr: JcCmpgExpr): T = defaultVisitJcExpr(expr)
        override fun visitJcCmplExpr(expr: JcCmplExpr): T = defaultVisitJcExpr(expr)
        override fun visitJcDivExpr(expr: JcDivExpr): T = defaultVisitJcExpr(expr)
        override fun visitJcMulExpr(expr: JcMulExpr): T = defaultVisitJcExpr(expr)
        override fun visitJcEqExpr(expr: JcEqExpr): T = defaultVisitJcExpr(expr)
        override fun visitJcNeqExpr(expr: JcNeqExpr): T = defaultVisitJcExpr(expr)
        override fun visitJcGeExpr(expr: JcGeExpr): T = defaultVisitJcExpr(expr)
        override fun visitJcGtExpr(expr: JcGtExpr): T = defaultVisitJcExpr(expr)
        override fun visitJcLeExpr(expr: JcLeExpr): T = defaultVisitJcExpr(expr)
        override fun visitJcLtExpr(expr: JcLtExpr): T = defaultVisitJcExpr(expr)
        override fun visitJcOrExpr(expr: JcOrExpr): T = defaultVisitJcExpr(expr)
        override fun visitJcRemExpr(expr: JcRemExpr): T = defaultVisitJcExpr(expr)
        override fun visitJcShlExpr(expr: JcShlExpr): T = defaultVisitJcExpr(expr)
        override fun visitJcShrExpr(expr: JcShrExpr): T = defaultVisitJcExpr(expr)
        override fun visitJcSubExpr(expr: JcSubExpr): T = defaultVisitJcExpr(expr)
        override fun visitJcUshrExpr(expr: JcUshrExpr): T = defaultVisitJcExpr(expr)
        override fun visitJcXorExpr(expr: JcXorExpr): T = defaultVisitJcExpr(expr)
        override fun visitJcLengthExpr(expr: JcLengthExpr): T = defaultVisitJcExpr(expr)
        override fun visitJcNegExpr(expr: JcNegExpr): T = defaultVisitJcExpr(expr)
        override fun visitJcCastExpr(expr: JcCastExpr): T = defaultVisitJcExpr(expr)
        override fun visitJcNewExpr(expr: JcNewExpr): T = defaultVisitJcExpr(expr)
        override fun visitJcNewArrayExpr(expr: JcNewArrayExpr): T = defaultVisitJcExpr(expr)
        override fun visitJcInstanceOfExpr(expr: JcInstanceOfExpr): T = defaultVisitJcExpr(expr)
        override fun visitJcPhiExpr(expr: JcPhiExpr): T = defaultVisitJcExpr(expr)
        override fun visitJcLambdaExpr(expr: JcLambdaExpr): T = defaultVisitJcExpr(expr)
        override fun visitJcDynamicCallExpr(expr: JcDynamicCallExpr): T = defaultVisitJcExpr(expr)
        override fun visitJcVirtualCallExpr(expr: JcVirtualCallExpr): T = defaultVisitJcExpr(expr)
        override fun visitJcStaticCallExpr(expr: JcStaticCallExpr): T = defaultVisitJcExpr(expr)
        override fun visitJcSpecialCallExpr(expr: JcSpecialCallExpr): T = defaultVisitJcExpr(expr)

        override fun visitExternalJcValue(value: JcValue): T = defaultVisitJcValue(value)

        override fun visitJcThis(value: JcThis): T = defaultVisitJcValue(value)
        override fun visitJcArgument(value: JcArgument): T = defaultVisitJcValue(value)
        override fun visitJcLocalVar(value: JcLocalVar): T = defaultVisitJcValue(value)
        override fun visitJcFieldRef(value: JcFieldRef): T = defaultVisitJcValue(value)
        override fun visitJcArrayAccess(value: JcArrayAccess): T = defaultVisitJcValue(value)
        override fun visitJcBool(value: JcBool): T = defaultVisitJcValue(value)
        override fun visitJcByte(value: JcByte): T = defaultVisitJcValue(value)
        override fun visitJcChar(value: JcChar): T = defaultVisitJcValue(value)
        override fun visitJcShort(value: JcShort): T = defaultVisitJcValue(value)
        override fun visitJcInt(value: JcInt): T = defaultVisitJcValue(value)
        override fun visitJcLong(value: JcLong): T = defaultVisitJcValue(value)
        override fun visitJcFloat(value: JcFloat): T = defaultVisitJcValue(value)
        override fun visitJcDouble(value: JcDouble): T = defaultVisitJcValue(value)
        override fun visitJcNullConstant(value: JcNullConstant): T = defaultVisitJcValue(value)
        override fun visitJcStringConstant(value: JcStringConstant): T = defaultVisitJcValue(value)
        override fun visitJcClassConstant(value: JcClassConstant): T = defaultVisitJcValue(value)
        override fun visitJcMethodConstant(value: JcMethodConstant): T = defaultVisitJcValue(value)
        override fun visitJcMethodType(value: JcMethodType): T = defaultVisitJcValue(value)
    }
}
