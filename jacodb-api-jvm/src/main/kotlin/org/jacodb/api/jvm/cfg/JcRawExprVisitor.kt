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

package org.jacodb.api.jvm.cfg

interface JcRawValueVisitor<out T> {
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
    fun visitJcRawMethodType(value: JcRawMethodType): T

    interface Default<out T> : JcRawValueVisitor<T> {
        fun defaultVisitJcRawValue(value: JcRawValue): T

        override fun visitJcRawThis(value: JcRawThis): T = defaultVisitJcRawValue(value)
        override fun visitJcRawArgument(value: JcRawArgument): T = defaultVisitJcRawValue(value)
        override fun visitJcRawLocalVar(value: JcRawLocalVar): T = defaultVisitJcRawValue(value)
        override fun visitJcRawFieldRef(value: JcRawFieldRef): T = defaultVisitJcRawValue(value)
        override fun visitJcRawArrayAccess(value: JcRawArrayAccess): T = defaultVisitJcRawValue(value)
        override fun visitJcRawBool(value: JcRawBool): T = defaultVisitJcRawValue(value)
        override fun visitJcRawByte(value: JcRawByte): T = defaultVisitJcRawValue(value)
        override fun visitJcRawChar(value: JcRawChar): T = defaultVisitJcRawValue(value)
        override fun visitJcRawShort(value: JcRawShort): T = defaultVisitJcRawValue(value)
        override fun visitJcRawInt(value: JcRawInt): T = defaultVisitJcRawValue(value)
        override fun visitJcRawLong(value: JcRawLong): T = defaultVisitJcRawValue(value)
        override fun visitJcRawFloat(value: JcRawFloat): T = defaultVisitJcRawValue(value)
        override fun visitJcRawDouble(value: JcRawDouble): T = defaultVisitJcRawValue(value)
        override fun visitJcRawNullConstant(value: JcRawNullConstant): T = defaultVisitJcRawValue(value)
        override fun visitJcRawStringConstant(value: JcRawStringConstant): T = defaultVisitJcRawValue(value)
        override fun visitJcRawClassConstant(value: JcRawClassConstant): T = defaultVisitJcRawValue(value)
        override fun visitJcRawMethodConstant(value: JcRawMethodConstant): T = defaultVisitJcRawValue(value)
        override fun visitJcRawMethodType(value: JcRawMethodType): T = defaultVisitJcRawValue(value)
    }
}

interface JcRawExprVisitor<out T> : JcRawValueVisitor<T> {
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

    interface Default<out T> : JcRawExprVisitor<T>, JcRawValueVisitor.Default<T> {
        fun defaultVisitJcRawExpr(expr: JcRawExpr): T

        override fun defaultVisitJcRawValue(value: JcRawValue): T = defaultVisitJcRawExpr(value)

        override fun visitJcRawAddExpr(expr: JcRawAddExpr): T = defaultVisitJcRawExpr(expr)
        override fun visitJcRawAndExpr(expr: JcRawAndExpr): T = defaultVisitJcRawExpr(expr)
        override fun visitJcRawCmpExpr(expr: JcRawCmpExpr): T = defaultVisitJcRawExpr(expr)
        override fun visitJcRawCmpgExpr(expr: JcRawCmpgExpr): T = defaultVisitJcRawExpr(expr)
        override fun visitJcRawCmplExpr(expr: JcRawCmplExpr): T = defaultVisitJcRawExpr(expr)
        override fun visitJcRawDivExpr(expr: JcRawDivExpr): T = defaultVisitJcRawExpr(expr)
        override fun visitJcRawMulExpr(expr: JcRawMulExpr): T = defaultVisitJcRawExpr(expr)
        override fun visitJcRawEqExpr(expr: JcRawEqExpr): T = defaultVisitJcRawExpr(expr)
        override fun visitJcRawNeqExpr(expr: JcRawNeqExpr): T = defaultVisitJcRawExpr(expr)
        override fun visitJcRawGeExpr(expr: JcRawGeExpr): T = defaultVisitJcRawExpr(expr)
        override fun visitJcRawGtExpr(expr: JcRawGtExpr): T = defaultVisitJcRawExpr(expr)
        override fun visitJcRawLeExpr(expr: JcRawLeExpr): T = defaultVisitJcRawExpr(expr)
        override fun visitJcRawLtExpr(expr: JcRawLtExpr): T = defaultVisitJcRawExpr(expr)
        override fun visitJcRawOrExpr(expr: JcRawOrExpr): T = defaultVisitJcRawExpr(expr)
        override fun visitJcRawRemExpr(expr: JcRawRemExpr): T = defaultVisitJcRawExpr(expr)
        override fun visitJcRawShlExpr(expr: JcRawShlExpr): T = defaultVisitJcRawExpr(expr)
        override fun visitJcRawShrExpr(expr: JcRawShrExpr): T = defaultVisitJcRawExpr(expr)
        override fun visitJcRawSubExpr(expr: JcRawSubExpr): T = defaultVisitJcRawExpr(expr)
        override fun visitJcRawUshrExpr(expr: JcRawUshrExpr): T = defaultVisitJcRawExpr(expr)
        override fun visitJcRawXorExpr(expr: JcRawXorExpr): T = defaultVisitJcRawExpr(expr)
        override fun visitJcRawLengthExpr(expr: JcRawLengthExpr): T = defaultVisitJcRawExpr(expr)
        override fun visitJcRawNegExpr(expr: JcRawNegExpr): T = defaultVisitJcRawExpr(expr)
        override fun visitJcRawCastExpr(expr: JcRawCastExpr): T = defaultVisitJcRawExpr(expr)
        override fun visitJcRawNewExpr(expr: JcRawNewExpr): T = defaultVisitJcRawExpr(expr)
        override fun visitJcRawNewArrayExpr(expr: JcRawNewArrayExpr): T = defaultVisitJcRawExpr(expr)
        override fun visitJcRawInstanceOfExpr(expr: JcRawInstanceOfExpr): T = defaultVisitJcRawExpr(expr)
        override fun visitJcRawDynamicCallExpr(expr: JcRawDynamicCallExpr): T = defaultVisitJcRawExpr(expr)
        override fun visitJcRawVirtualCallExpr(expr: JcRawVirtualCallExpr): T = defaultVisitJcRawExpr(expr)
        override fun visitJcRawInterfaceCallExpr(expr: JcRawInterfaceCallExpr): T = defaultVisitJcRawExpr(expr)
        override fun visitJcRawStaticCallExpr(expr: JcRawStaticCallExpr): T = defaultVisitJcRawExpr(expr)
        override fun visitJcRawSpecialCallExpr(expr: JcRawSpecialCallExpr): T = defaultVisitJcRawExpr(expr)
    }
}
