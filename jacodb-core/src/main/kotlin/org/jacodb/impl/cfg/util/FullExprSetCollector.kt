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

package org.jacodb.impl.cfg.util

import org.jacodb.api.cfg.JcRawAddExpr
import org.jacodb.api.cfg.JcRawAndExpr
import org.jacodb.api.cfg.JcRawArgument
import org.jacodb.api.cfg.JcRawArrayAccess
import org.jacodb.api.cfg.JcRawBool
import org.jacodb.api.cfg.JcRawByte
import org.jacodb.api.cfg.JcRawCastExpr
import org.jacodb.api.cfg.JcRawChar
import org.jacodb.api.cfg.JcRawClassConstant
import org.jacodb.api.cfg.JcRawCmpExpr
import org.jacodb.api.cfg.JcRawCmpgExpr
import org.jacodb.api.cfg.JcRawCmplExpr
import org.jacodb.api.cfg.JcRawDivExpr
import org.jacodb.api.cfg.JcRawDouble
import org.jacodb.api.cfg.JcRawDynamicCallExpr
import org.jacodb.api.cfg.JcRawEqExpr
import org.jacodb.api.cfg.JcRawExpr
import org.jacodb.api.cfg.JcRawExprVisitor
import org.jacodb.api.cfg.JcRawFieldRef
import org.jacodb.api.cfg.JcRawFloat
import org.jacodb.api.cfg.JcRawGeExpr
import org.jacodb.api.cfg.JcRawGtExpr
import org.jacodb.api.cfg.JcRawInstanceOfExpr
import org.jacodb.api.cfg.JcRawInt
import org.jacodb.api.cfg.JcRawInterfaceCallExpr
import org.jacodb.api.cfg.JcRawLeExpr
import org.jacodb.api.cfg.JcRawLengthExpr
import org.jacodb.api.cfg.JcRawLocalVar
import org.jacodb.api.cfg.JcRawLong
import org.jacodb.api.cfg.JcRawLtExpr
import org.jacodb.api.cfg.JcRawMethodConstant
import org.jacodb.api.cfg.JcRawMulExpr
import org.jacodb.api.cfg.JcRawNegExpr
import org.jacodb.api.cfg.JcRawNeqExpr
import org.jacodb.api.cfg.JcRawNewArrayExpr
import org.jacodb.api.cfg.JcRawNewExpr
import org.jacodb.api.cfg.JcRawNullConstant
import org.jacodb.api.cfg.JcRawOrExpr
import org.jacodb.api.cfg.JcRawRemExpr
import org.jacodb.api.cfg.JcRawShlExpr
import org.jacodb.api.cfg.JcRawShort
import org.jacodb.api.cfg.JcRawShrExpr
import org.jacodb.api.cfg.JcRawSpecialCallExpr
import org.jacodb.api.cfg.JcRawStaticCallExpr
import org.jacodb.api.cfg.JcRawStringConstant
import org.jacodb.api.cfg.JcRawSubExpr
import org.jacodb.api.cfg.JcRawThis
import org.jacodb.api.cfg.JcRawUshrExpr
import org.jacodb.api.cfg.JcRawVirtualCallExpr
import org.jacodb.api.cfg.JcRawXorExpr

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

