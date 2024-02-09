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

abstract class AbstractFullRawExprSetCollector :
    JcRawExprVisitor<Any>,
    JcRawInstVisitor.Default<Any> {

    override fun defaultVisitJcRawInst(inst: JcRawInst) {
        inst.operands.forEach {
            ifMatches(it)
            it.accept(this)
        }
    }

    private fun visitBinaryExpr(expr: JcRawBinaryExpr) {
        ifMatches(expr)
        expr.lhv.accept(this)
        expr.rhv.accept(this)
    }

    private fun visitCallExpr(expr: JcRawCallExpr) {
        ifMatches(expr)
        if (expr is JcRawInstanceExpr) {
            expr.instance.accept(this)
        }
        expr.args.forEach { it.accept(this) }
    }

    override fun visitJcRawAddExpr(expr: JcRawAddExpr) = visitBinaryExpr(expr)
    override fun visitJcRawAndExpr(expr: JcRawAndExpr) = visitBinaryExpr(expr)
    override fun visitJcRawCmpExpr(expr: JcRawCmpExpr) = visitBinaryExpr(expr)
    override fun visitJcRawCmpgExpr(expr: JcRawCmpgExpr) = visitBinaryExpr(expr)
    override fun visitJcRawCmplExpr(expr: JcRawCmplExpr) = visitBinaryExpr(expr)
    override fun visitJcRawDivExpr(expr: JcRawDivExpr) = visitBinaryExpr(expr)
    override fun visitJcRawMulExpr(expr: JcRawMulExpr) = visitBinaryExpr(expr)
    override fun visitJcRawEqExpr(expr: JcRawEqExpr) = visitBinaryExpr(expr)
    override fun visitJcRawNeqExpr(expr: JcRawNeqExpr) = visitBinaryExpr(expr)
    override fun visitJcRawGeExpr(expr: JcRawGeExpr) = visitBinaryExpr(expr)
    override fun visitJcRawGtExpr(expr: JcRawGtExpr) = visitBinaryExpr(expr)
    override fun visitJcRawLeExpr(expr: JcRawLeExpr) = visitBinaryExpr(expr)
    override fun visitJcRawLtExpr(expr: JcRawLtExpr) = visitBinaryExpr(expr)
    override fun visitJcRawOrExpr(expr: JcRawOrExpr) = visitBinaryExpr(expr)
    override fun visitJcRawRemExpr(expr: JcRawRemExpr) = visitBinaryExpr(expr)
    override fun visitJcRawShlExpr(expr: JcRawShlExpr) = visitBinaryExpr(expr)
    override fun visitJcRawShrExpr(expr: JcRawShrExpr) = visitBinaryExpr(expr)
    override fun visitJcRawSubExpr(expr: JcRawSubExpr) = visitBinaryExpr(expr)
    override fun visitJcRawUshrExpr(expr: JcRawUshrExpr) = visitBinaryExpr(expr)
    override fun visitJcRawXorExpr(expr: JcRawXorExpr) = visitBinaryExpr(expr)

    override fun visitJcRawLengthExpr(expr: JcRawLengthExpr) {
        ifMatches(expr)
        expr.array.accept(this)
    }

    override fun visitJcRawNegExpr(expr: JcRawNegExpr) {
        ifMatches(expr)
        expr.operand.accept(this)
    }

    override fun visitJcRawCastExpr(expr: JcRawCastExpr) {
        ifMatches(expr)
        expr.operand.accept(this)
    }

    override fun visitJcRawNewExpr(expr: JcRawNewExpr) {
        ifMatches(expr)
    }

    override fun visitJcRawNewArrayExpr(expr: JcRawNewArrayExpr) {
        ifMatches(expr)
        expr.dimensions.forEach { it.accept(this) }
    }

    override fun visitJcRawInstanceOfExpr(expr: JcRawInstanceOfExpr) {
        ifMatches(expr)
        expr.operand.accept(this)
    }

    override fun visitJcRawDynamicCallExpr(expr: JcRawDynamicCallExpr) {
        ifMatches(expr)
        expr.args.forEach { it.accept(this) }
    }

    override fun visitJcRawVirtualCallExpr(expr: JcRawVirtualCallExpr) = visitCallExpr(expr)
    override fun visitJcRawInterfaceCallExpr(expr: JcRawInterfaceCallExpr) = visitCallExpr(expr)
    override fun visitJcRawStaticCallExpr(expr: JcRawStaticCallExpr) = visitCallExpr(expr)
    override fun visitJcRawSpecialCallExpr(expr: JcRawSpecialCallExpr) = visitCallExpr(expr)
    override fun visitJcRawThis(value: JcRawThis) = ifMatches(value)
    override fun visitJcRawArgument(value: JcRawArgument) = ifMatches(value)
    override fun visitJcRawLocalVar(value: JcRawLocalVar) = ifMatches(value)

    override fun visitJcRawFieldRef(value: JcRawFieldRef) {
        ifMatches(value)
        value.instance?.accept(this)
    }

    override fun visitJcRawArrayAccess(value: JcRawArrayAccess) {
        ifMatches(value)
        value.array.accept(this)
        value.index.accept(this)
    }

    override fun visitJcRawBool(value: JcRawBool) = ifMatches(value)
    override fun visitJcRawByte(value: JcRawByte) = ifMatches(value)
    override fun visitJcRawChar(value: JcRawChar) = ifMatches(value)
    override fun visitJcRawShort(value: JcRawShort) = ifMatches(value)
    override fun visitJcRawInt(value: JcRawInt) = ifMatches(value)
    override fun visitJcRawLong(value: JcRawLong) = ifMatches(value)
    override fun visitJcRawFloat(value: JcRawFloat) = ifMatches(value)
    override fun visitJcRawDouble(value: JcRawDouble) = ifMatches(value)
    override fun visitJcRawNullConstant(value: JcRawNullConstant) = ifMatches(value)
    override fun visitJcRawStringConstant(value: JcRawStringConstant) = ifMatches(value)
    override fun visitJcRawClassConstant(value: JcRawClassConstant) = ifMatches(value)
    override fun visitJcRawMethodConstant(value: JcRawMethodConstant) = ifMatches(value)
    override fun visitJcRawMethodType(value: JcRawMethodType) = ifMatches(value)

    abstract fun ifMatches(expr: JcRawExpr)
}

abstract class AbstractFullExprSetCollector :
    JcExprVisitor.Default<Any>,
    JcInstVisitor.Default<Any> {

    override fun defaultVisitJcInst(inst: JcInst) {
        inst.operands.forEach {
            ifMatches(it)
            it.accept(this)
        }
    }

    private fun visitBinaryExpr(expr: JcBinaryExpr) {
        ifMatches(expr)
        expr.lhv.accept(this)
        expr.rhv.accept(this)
    }

    private fun visitCallExpr(expr: JcCallExpr) {
        ifMatches(expr)
        if (expr is JcInstanceCallExpr) {
            expr.instance.accept(this)
        }
        expr.args.forEach { it.accept(this) }
    }

    override fun visitJcAddExpr(expr: JcAddExpr) = visitBinaryExpr(expr)
    override fun visitJcAndExpr(expr: JcAndExpr) = visitBinaryExpr(expr)
    override fun visitJcCmpExpr(expr: JcCmpExpr) = visitBinaryExpr(expr)
    override fun visitJcCmpgExpr(expr: JcCmpgExpr) = visitBinaryExpr(expr)
    override fun visitJcCmplExpr(expr: JcCmplExpr) = visitBinaryExpr(expr)
    override fun visitJcDivExpr(expr: JcDivExpr) = visitBinaryExpr(expr)
    override fun visitJcMulExpr(expr: JcMulExpr) = visitBinaryExpr(expr)
    override fun visitJcEqExpr(expr: JcEqExpr) = visitBinaryExpr(expr)
    override fun visitJcNeqExpr(expr: JcNeqExpr) = visitBinaryExpr(expr)
    override fun visitJcGeExpr(expr: JcGeExpr) = visitBinaryExpr(expr)
    override fun visitJcGtExpr(expr: JcGtExpr) = visitBinaryExpr(expr)
    override fun visitJcLeExpr(expr: JcLeExpr) = visitBinaryExpr(expr)
    override fun visitJcLtExpr(expr: JcLtExpr) = visitBinaryExpr(expr)
    override fun visitJcOrExpr(expr: JcOrExpr) = visitBinaryExpr(expr)
    override fun visitJcRemExpr(expr: JcRemExpr) = visitBinaryExpr(expr)
    override fun visitJcShlExpr(expr: JcShlExpr) = visitBinaryExpr(expr)
    override fun visitJcShrExpr(expr: JcShrExpr) = visitBinaryExpr(expr)
    override fun visitJcSubExpr(expr: JcSubExpr) = visitBinaryExpr(expr)
    override fun visitJcUshrExpr(expr: JcUshrExpr) = visitBinaryExpr(expr)
    override fun visitJcXorExpr(expr: JcXorExpr) = visitBinaryExpr(expr)

    override fun visitJcLambdaExpr(expr: JcLambdaExpr) {
        ifMatches(expr)
        expr.args.forEach { it.accept(this) }
    }

    override fun visitJcLengthExpr(expr: JcLengthExpr) {
        ifMatches(expr)
        expr.array.accept(this)
    }

    override fun visitJcNegExpr(expr: JcNegExpr) {
        ifMatches(expr)
        expr.operand.accept(this)
    }

    override fun visitJcCastExpr(expr: JcCastExpr) {
        ifMatches(expr)
        expr.operand.accept(this)
    }

    override fun visitJcNewExpr(expr: JcNewExpr) {
        ifMatches(expr)
    }

    override fun visitJcNewArrayExpr(expr: JcNewArrayExpr) {
        ifMatches(expr)
        expr.dimensions.forEach { it.accept(this) }
    }

    override fun visitJcInstanceOfExpr(expr: JcInstanceOfExpr) {
        ifMatches(expr)
        expr.operand.accept(this)
    }

    override fun visitJcDynamicCallExpr(expr: JcDynamicCallExpr) {
        ifMatches(expr)
        expr.args.forEach { it.accept(this) }
    }

    override fun visitJcVirtualCallExpr(expr: JcVirtualCallExpr) = visitCallExpr(expr)
    override fun visitJcStaticCallExpr(expr: JcStaticCallExpr) = visitCallExpr(expr)
    override fun visitJcSpecialCallExpr(expr: JcSpecialCallExpr) = visitCallExpr(expr)
    override fun visitJcThis(value: JcThis) = ifMatches(value)
    override fun visitJcArgument(value: JcArgument) = ifMatches(value)
    override fun visitJcLocalVar(value: JcLocalVar) = ifMatches(value)

    override fun visitJcFieldRef(value: JcFieldRef) {
        ifMatches(value)
        value.instance?.accept(this)
    }

    override fun visitJcArrayAccess(value: JcArrayAccess) {
        ifMatches(value)
        value.array.accept(this)
        value.index.accept(this)
    }

    override fun visitJcPhiExpr(expr: JcPhiExpr) {
        ifMatches(expr)
        expr.args.forEach { it.accept(this) }
        expr.values.forEach { it.accept(this) }
    }

    override fun defaultVisitJcExpr(expr: JcExpr) = ifMatches(expr)
    override fun visitJcBool(value: JcBool) = ifMatches(value)
    override fun visitJcByte(value: JcByte) = ifMatches(value)
    override fun visitJcChar(value: JcChar) = ifMatches(value)
    override fun visitJcShort(value: JcShort) = ifMatches(value)
    override fun visitJcInt(value: JcInt) = ifMatches(value)
    override fun visitJcLong(value: JcLong) = ifMatches(value)
    override fun visitJcFloat(value: JcFloat) = ifMatches(value)
    override fun visitJcDouble(value: JcDouble) = ifMatches(value)
    override fun visitJcNullConstant(value: JcNullConstant) = ifMatches(value)
    override fun visitJcStringConstant(value: JcStringConstant) = ifMatches(value)
    override fun visitJcClassConstant(value: JcClassConstant) = ifMatches(value)
    override fun visitJcMethodConstant(value: JcMethodConstant) = ifMatches(value)
    override fun visitJcMethodType(value: JcMethodType) = ifMatches(value)

    abstract fun ifMatches(expr: JcExpr)
}
