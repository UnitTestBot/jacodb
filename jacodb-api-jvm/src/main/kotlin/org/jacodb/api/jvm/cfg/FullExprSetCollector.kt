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

import org.jacodb.api.common.cfg.CommonExpr
import org.jacodb.api.common.cfg.CommonInst

abstract class AbstractFullRawExprSetCollector :
    JcRawExprVisitor<Unit>,
    JcRawInstVisitor.Default<Unit> {

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

    override fun defaultVisitJcExpr(expr: JcExpr) {
        ifMatches(expr)
    }

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

    override fun visitJcAddExpr(expr: JcAddExpr): Any = visitBinaryExpr(expr)
    override fun visitJcAndExpr(expr: JcAndExpr): Any = visitBinaryExpr(expr)
    override fun visitJcCmpExpr(expr: JcCmpExpr): Any = visitBinaryExpr(expr)
    override fun visitJcCmpgExpr(expr: JcCmpgExpr): Any = visitBinaryExpr(expr)
    override fun visitJcCmplExpr(expr: JcCmplExpr): Any = visitBinaryExpr(expr)
    override fun visitJcDivExpr(expr: JcDivExpr): Any = visitBinaryExpr(expr)
    override fun visitJcMulExpr(expr: JcMulExpr): Any = visitBinaryExpr(expr)
    override fun visitJcEqExpr(expr: JcEqExpr): Any = visitBinaryExpr(expr)
    override fun visitJcNeqExpr(expr: JcNeqExpr): Any = visitBinaryExpr(expr)
    override fun visitJcGeExpr(expr: JcGeExpr): Any = visitBinaryExpr(expr)
    override fun visitJcGtExpr(expr: JcGtExpr): Any = visitBinaryExpr(expr)
    override fun visitJcLeExpr(expr: JcLeExpr): Any = visitBinaryExpr(expr)
    override fun visitJcLtExpr(expr: JcLtExpr): Any = visitBinaryExpr(expr)
    override fun visitJcOrExpr(expr: JcOrExpr): Any = visitBinaryExpr(expr)
    override fun visitJcRemExpr(expr: JcRemExpr): Any = visitBinaryExpr(expr)
    override fun visitJcShlExpr(expr: JcShlExpr): Any = visitBinaryExpr(expr)
    override fun visitJcShrExpr(expr: JcShrExpr): Any = visitBinaryExpr(expr)
    override fun visitJcSubExpr(expr: JcSubExpr): Any = visitBinaryExpr(expr)
    override fun visitJcUshrExpr(expr: JcUshrExpr): Any = visitBinaryExpr(expr)
    override fun visitJcXorExpr(expr: JcXorExpr): Any = visitBinaryExpr(expr)

    override fun visitJcLambdaExpr(expr: JcLambdaExpr): Any {
        ifMatches(expr)
        expr.args.forEach { it.accept(this) }
        return Unit
    }

    override fun visitJcLengthExpr(expr: JcLengthExpr): Any {
        ifMatches(expr)
        expr.array.accept(this)
        return Unit
    }

    override fun visitJcNegExpr(expr: JcNegExpr): Any {
        ifMatches(expr)
        expr.operand.accept(this)
        return Unit
    }

    override fun visitJcCastExpr(expr: JcCastExpr): Any {
        ifMatches(expr)
        expr.operand.accept(this)
        return Unit
    }

    override fun visitJcNewExpr(expr: JcNewExpr): Any {
        ifMatches(expr)
        return Unit
    }

    override fun visitJcNewArrayExpr(expr: JcNewArrayExpr): Any {
        ifMatches(expr)
        expr.dimensions.forEach { it.accept(this) }
        return Unit
    }

    override fun visitJcInstanceOfExpr(expr: JcInstanceOfExpr): Any {
        ifMatches(expr)
        expr.operand.accept(this)
        return Unit
    }

    override fun visitJcDynamicCallExpr(expr: JcDynamicCallExpr): Any {
        ifMatches(expr)
        expr.args.forEach { it.accept(this) }
        return Unit
    }

    override fun visitJcVirtualCallExpr(expr: JcVirtualCallExpr): Any = visitCallExpr(expr)
    override fun visitJcStaticCallExpr(expr: JcStaticCallExpr): Any = visitCallExpr(expr)
    override fun visitJcSpecialCallExpr(expr: JcSpecialCallExpr): Any = visitCallExpr(expr)
    override fun visitJcThis(value: JcThis): Any = ifMatches(value)
    override fun visitJcArgument(value: JcArgument): Any = ifMatches(value)
    override fun visitJcLocalVar(value: JcLocalVar): Any = ifMatches(value)

    override fun visitJcFieldRef(value: JcFieldRef): Any {
        ifMatches(value)
        value.instance?.accept(this)
        return Unit
    }

    override fun visitJcArrayAccess(value: JcArrayAccess): Any {
        ifMatches(value)
        value.array.accept(this)
        value.index.accept(this)
        return Unit
    }

    override fun visitJcPhiExpr(expr: JcPhiExpr): Any {
        ifMatches(expr)
        expr.args.forEach { it.accept(this) }
        expr.values.forEach { it.accept(this) }
        return Unit
    }

    abstract fun ifMatches(expr: JcExpr)
}
