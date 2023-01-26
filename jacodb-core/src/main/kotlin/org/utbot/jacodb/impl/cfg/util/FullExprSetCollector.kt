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

package org.utbot.jacodb.impl.cfg.util

import org.utbot.jacodb.api.cfg.*

class FullExprSetCollector : JcRawExprVisitor<Unit> {
    val exprs = mutableSetOf<JcRawExpr>()

    override fun visitJcRawAddExpr(expr: JcRawAddExpr) {
        exprs.add(expr)
        expr.lhv.accept(this)
        expr.rhv.accept(this)
    }

    override fun visitJcRawAndExpr(expr: JcRawAndExpr) {
        exprs.add(expr)
        expr.lhv.accept(this)
        expr.rhv.accept(this)
    }

    override fun visitJcRawCmpExpr(expr: JcRawCmpExpr) {
        exprs.add(expr)
        expr.lhv.accept(this)
        expr.rhv.accept(this)
    }

    override fun visitJcRawCmpgExpr(expr: JcRawCmpgExpr) {
        exprs.add(expr)
        expr.lhv.accept(this)
        expr.rhv.accept(this)
    }

    override fun visitJcRawCmplExpr(expr: JcRawCmplExpr) {
        exprs.add(expr)
        expr.lhv.accept(this)
        expr.rhv.accept(this)
    }

    override fun visitJcRawDivExpr(expr: JcRawDivExpr) {
        exprs.add(expr)
        expr.lhv.accept(this)
        expr.rhv.accept(this)
    }

    override fun visitJcRawMulExpr(expr: JcRawMulExpr) {
        exprs.add(expr)
        expr.lhv.accept(this)
        expr.rhv.accept(this)
    }

    override fun visitJcRawEqExpr(expr: JcRawEqExpr) {
        exprs.add(expr)
        expr.lhv.accept(this)
        expr.rhv.accept(this)
    }

    override fun visitJcRawNeqExpr(expr: JcRawNeqExpr) {
        exprs.add(expr)
        expr.lhv.accept(this)
        expr.rhv.accept(this)
    }

    override fun visitJcRawGeExpr(expr: JcRawGeExpr) {
        exprs.add(expr)
        expr.lhv.accept(this)
        expr.rhv.accept(this)
    }

    override fun visitJcRawGtExpr(expr: JcRawGtExpr) {
        exprs.add(expr)
        expr.lhv.accept(this)
        expr.rhv.accept(this)
    }

    override fun visitJcRawLeExpr(expr: JcRawLeExpr) {
        exprs.add(expr)
        expr.lhv.accept(this)
        expr.rhv.accept(this)
    }

    override fun visitJcRawLtExpr(expr: JcRawLtExpr) {
        exprs.add(expr)
        expr.lhv.accept(this)
        expr.rhv.accept(this)
    }

    override fun visitJcRawOrExpr(expr: JcRawOrExpr) {
        exprs.add(expr)
        expr.lhv.accept(this)
        expr.rhv.accept(this)
    }

    override fun visitJcRawRemExpr(expr: JcRawRemExpr) {
        exprs.add(expr)
        expr.lhv.accept(this)
        expr.rhv.accept(this)
    }

    override fun visitJcRawShlExpr(expr: JcRawShlExpr) {
        exprs.add(expr)
        expr.lhv.accept(this)
        expr.rhv.accept(this)
    }

    override fun visitJcRawShrExpr(expr: JcRawShrExpr) {
        exprs.add(expr)
        expr.lhv.accept(this)
        expr.rhv.accept(this)
    }

    override fun visitJcRawSubExpr(expr: JcRawSubExpr) {
        exprs.add(expr)
        expr.lhv.accept(this)
        expr.rhv.accept(this)
    }

    override fun visitJcRawUshrExpr(expr: JcRawUshrExpr) {
        exprs.add(expr)
        expr.lhv.accept(this)
        expr.rhv.accept(this)
    }

    override fun visitJcRawXorExpr(expr: JcRawXorExpr) {
        exprs.add(expr)
        expr.lhv.accept(this)
        expr.rhv.accept(this)
    }

    override fun visitJcRawLengthExpr(expr: JcRawLengthExpr) {
        exprs.add(expr)
        expr.array.accept(this)
    }

    override fun visitJcRawNegExpr(expr: JcRawNegExpr) {
        exprs.add(expr)
        expr.operand.accept(this)
    }

    override fun visitJcRawCastExpr(expr: JcRawCastExpr) {
        exprs.add(expr)
        expr.operand.accept(this)
    }

    override fun visitJcRawNewExpr(expr: JcRawNewExpr) {
        exprs.add(expr)
    }

    override fun visitJcRawNewArrayExpr(expr: JcRawNewArrayExpr) {
        exprs.add(expr)
        expr.dimensions.forEach { it.accept(this) }
    }

    override fun visitJcRawInstanceOfExpr(expr: JcRawInstanceOfExpr) {
        exprs.add(expr)
        expr.operand.accept(this)
    }

    override fun visitJcRawDynamicCallExpr(expr: JcRawDynamicCallExpr) {
        exprs.add(expr)
        expr.args.forEach { it.accept(this) }
    }

    override fun visitJcRawVirtualCallExpr(expr: JcRawVirtualCallExpr) {
        exprs.add(expr)
        expr.instance.accept(this)
        expr.args.forEach { it.accept(this) }
    }

    override fun visitJcRawInterfaceCallExpr(expr: JcRawInterfaceCallExpr) {
        exprs.add(expr)
        expr.instance.accept(this)
        expr.args.forEach { it.accept(this) }
    }

    override fun visitJcRawStaticCallExpr(expr: JcRawStaticCallExpr) {
        exprs.add(expr)
        expr.args.forEach { it.accept(this) }
    }

    override fun visitJcRawSpecialCallExpr(expr: JcRawSpecialCallExpr) {
        exprs.add(expr)
        expr.instance.accept(this)
        expr.args.forEach { it.accept(this) }
    }

    override fun visitJcRawThis(value: JcRawThis) {
        exprs.add(value)
    }

    override fun visitJcRawArgument(value: JcRawArgument) {
        exprs.add(value)
    }

    override fun visitJcRawLocalVar(value: JcRawLocalVar) {
        exprs.add(value)
    }

    override fun visitJcRawFieldRef(value: JcRawFieldRef) {
        exprs.add(value)
        value.instance?.accept(this)
    }

    override fun visitJcRawArrayAccess(value: JcRawArrayAccess) {
        exprs.add(value)
        value.array.accept(this)
        value.index.accept(this)
    }

    override fun visitJcRawBool(value: JcRawBool) {
        exprs.add(value)
    }

    override fun visitJcRawByte(value: JcRawByte) {
        exprs.add(value)
    }

    override fun visitJcRawChar(value: JcRawChar) {
        exprs.add(value)
    }

    override fun visitJcRawShort(value: JcRawShort) {
        exprs.add(value)
    }

    override fun visitJcRawInt(value: JcRawInt) {
        exprs.add(value)
    }

    override fun visitJcRawLong(value: JcRawLong) {
        exprs.add(value)
    }

    override fun visitJcRawFloat(value: JcRawFloat) {
        exprs.add(value)
    }

    override fun visitJcRawDouble(value: JcRawDouble) {
        exprs.add(value)
    }

    override fun visitJcRawNullConstant(value: JcRawNullConstant) {
        exprs.add(value)
    }

    override fun visitJcRawStringConstant(value: JcRawStringConstant) {
        exprs.add(value)
    }

    override fun visitJcRawClassConstant(value: JcRawClassConstant) {
        exprs.add(value)
    }

    override fun visitJcRawMethodConstant(value: JcRawMethodConstant) {
        exprs.add(value)
    }
}

