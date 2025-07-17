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

package org.jacodb.ets.utils

import org.jacodb.ets.model.EtsAddExpr
import org.jacodb.ets.model.EtsAndExpr
import org.jacodb.ets.model.EtsArrayAccess
import org.jacodb.ets.model.EtsAssignStmt
import org.jacodb.ets.model.EtsAwaitExpr
import org.jacodb.ets.model.EtsBitAndExpr
import org.jacodb.ets.model.EtsBitNotExpr
import org.jacodb.ets.model.EtsBitOrExpr
import org.jacodb.ets.model.EtsBitXorExpr
import org.jacodb.ets.model.EtsBooleanConstant
import org.jacodb.ets.model.EtsCallStmt
import org.jacodb.ets.model.EtsCastExpr
import org.jacodb.ets.model.EtsCaughtExceptionRef
import org.jacodb.ets.model.EtsClosureFieldRef
import org.jacodb.ets.model.EtsConstant
import org.jacodb.ets.model.EtsDeleteExpr
import org.jacodb.ets.model.EtsDivExpr
import org.jacodb.ets.model.EtsEntity
import org.jacodb.ets.model.EtsEqExpr
import org.jacodb.ets.model.EtsExpExpr
import org.jacodb.ets.model.EtsGlobalRef
import org.jacodb.ets.model.EtsGtEqExpr
import org.jacodb.ets.model.EtsGtExpr
import org.jacodb.ets.model.EtsIfStmt
import org.jacodb.ets.model.EtsInExpr
import org.jacodb.ets.model.EtsInstanceCallExpr
import org.jacodb.ets.model.EtsInstanceFieldRef
import org.jacodb.ets.model.EtsInstanceOfExpr
import org.jacodb.ets.model.EtsLeftShiftExpr
import org.jacodb.ets.model.EtsLocal
import org.jacodb.ets.model.EtsLtEqExpr
import org.jacodb.ets.model.EtsLtExpr
import org.jacodb.ets.model.EtsMulExpr
import org.jacodb.ets.model.EtsNegExpr
import org.jacodb.ets.model.EtsNewArrayExpr
import org.jacodb.ets.model.EtsNewExpr
import org.jacodb.ets.model.EtsNopStmt
import org.jacodb.ets.model.EtsNotEqExpr
import org.jacodb.ets.model.EtsNotExpr
import org.jacodb.ets.model.EtsNullConstant
import org.jacodb.ets.model.EtsNullishCoalescingExpr
import org.jacodb.ets.model.EtsNumberConstant
import org.jacodb.ets.model.EtsOrExpr
import org.jacodb.ets.model.EtsParameterRef
import org.jacodb.ets.model.EtsPostDecExpr
import org.jacodb.ets.model.EtsPostIncExpr
import org.jacodb.ets.model.EtsPreDecExpr
import org.jacodb.ets.model.EtsPreIncExpr
import org.jacodb.ets.model.EtsPtrCallExpr
import org.jacodb.ets.model.EtsRawEntity
import org.jacodb.ets.model.EtsRawStmt
import org.jacodb.ets.model.EtsRemExpr
import org.jacodb.ets.model.EtsReturnStmt
import org.jacodb.ets.model.EtsRightShiftExpr
import org.jacodb.ets.model.EtsStaticCallExpr
import org.jacodb.ets.model.EtsStaticFieldRef
import org.jacodb.ets.model.EtsStmt
import org.jacodb.ets.model.EtsStrictEqExpr
import org.jacodb.ets.model.EtsStrictNotEqExpr
import org.jacodb.ets.model.EtsStringConstant
import org.jacodb.ets.model.EtsSubExpr
import org.jacodb.ets.model.EtsThis
import org.jacodb.ets.model.EtsThrowStmt
import org.jacodb.ets.model.EtsTypeOfExpr
import org.jacodb.ets.model.EtsUnaryPlusExpr
import org.jacodb.ets.model.EtsUndefinedConstant
import org.jacodb.ets.model.EtsUnsignedRightShiftExpr
import org.jacodb.ets.model.EtsVoidExpr
import org.jacodb.ets.model.EtsYieldExpr

/**
 * An example handler for [EtsEntity] and [EtsStmt].
 *
 * This class is not meant to be used directly, but rather serves as a template for creating custom handlers.
 *
 * **Note:** this class might seem as completely useless, but it actually guards
 * from missing any of the visit methods in the future.
 * If this fails to compile, you might need to implement new methods in [AbstractHandler].
 */
class ExampleHandler : AbstractHandler() {
    override fun handle(value: EtsEntity) {
        // Handle the entity
        println("entity: $value")
    }

    override fun handle(stmt: EtsStmt) {
        // Handle the statement
        println("statement: $stmt")
    }
}

abstract class AbstractHandler : EtsEntity.Visitor<Unit>, EtsStmt.Visitor<Unit> {

    abstract fun handle(value: EtsEntity)
    abstract fun handle(stmt: EtsStmt)

    final override fun visit(stmt: EtsRawStmt) {
        handle(stmt)
    }

    final override fun visit(stmt: EtsNopStmt) {
        handle(stmt)
    }

    final override fun visit(stmt: EtsAssignStmt) {
        handle(stmt)
        stmt.lhv.accept(this)
        stmt.rhv.accept(this)
    }

    final override fun visit(stmt: EtsCallStmt) {
        handle(stmt)
        stmt.expr.accept(this)
    }

    final override fun visit(stmt: EtsReturnStmt) {
        handle(stmt)
        stmt.returnValue?.accept(this)
    }

    final override fun visit(stmt: EtsThrowStmt) {
        handle(stmt)
        stmt.exception.accept(this)
    }

    final override fun visit(stmt: EtsIfStmt) {
        handle(stmt)
        stmt.condition.accept(this)
    }

    final override fun visit(value: EtsRawEntity) {
        handle(value)
    }

    final override fun visit(value: EtsLocal) {
        handle(value)
    }

    final override fun visit(value: EtsConstant) {
        handle(value)
    }

    final override fun visit(value: EtsStringConstant) {
        handle(value)
    }

    final override fun visit(value: EtsBooleanConstant) {
        handle(value)
    }

    final override fun visit(value: EtsNumberConstant) {
        handle(value)
    }

    final override fun visit(value: EtsNullConstant) {
        handle(value)
    }

    final override fun visit(value: EtsUndefinedConstant) {
        handle(value)
    }

    final override fun visit(value: EtsThis) {
        handle(value)
    }

    final override fun visit(value: EtsParameterRef) {
        handle(value)
    }

    final override fun visit(value: EtsArrayAccess) {
        handle(value)
        value.array.accept(this)
        value.index.accept(this)
    }

    final override fun visit(value: EtsInstanceFieldRef) {
        handle(value)
        value.instance.accept(this)
    }

    final override fun visit(value: EtsStaticFieldRef) {
        handle(value)
    }

    final override fun visit(value: EtsCaughtExceptionRef) {
        handle(value)
    }

    final override fun visit(value: EtsGlobalRef) {
        handle(value)
        value.ref?.accept(this)
    }

    final override fun visit(value: EtsClosureFieldRef) {
        handle(value)
        value.base.accept(this)
    }

    final override fun visit(expr: EtsNewExpr) {
        handle(expr)
    }

    final override fun visit(expr: EtsNewArrayExpr) {
        handle(expr)
        expr.size.accept(this)
    }

    final override fun visit(expr: EtsCastExpr) {
        handle(expr)
        expr.arg.accept(this)
    }

    final override fun visit(expr: EtsInstanceOfExpr) {
        handle(expr)
        expr.arg.accept(this)
    }

    final override fun visit(expr: EtsDeleteExpr) {
        handle(expr)
        expr.arg.accept(this)
    }

    final override fun visit(expr: EtsAwaitExpr) {
        handle(expr)
        expr.arg.accept(this)
    }

    final override fun visit(expr: EtsYieldExpr) {
        handle(expr)
        expr.arg.accept(this)
    }

    final override fun visit(expr: EtsTypeOfExpr) {
        handle(expr)
        expr.arg.accept(this)
    }

    final override fun visit(expr: EtsVoidExpr) {
        handle(expr)
        expr.arg.accept(this)
    }

    final override fun visit(expr: EtsNotExpr) {
        handle(expr)
        expr.arg.accept(this)
    }

    final override fun visit(expr: EtsBitNotExpr) {
        handle(expr)
        expr.arg.accept(this)
    }

    final override fun visit(expr: EtsNegExpr) {
        handle(expr)
        expr.arg.accept(this)
    }

    final override fun visit(expr: EtsUnaryPlusExpr) {
        handle(expr)
        expr.arg.accept(this)
    }

    final override fun visit(expr: EtsPreIncExpr) {
        handle(expr)
        expr.arg.accept(this)
    }

    final override fun visit(expr: EtsPreDecExpr) {
        handle(expr)
        expr.arg.accept(this)
    }

    final override fun visit(expr: EtsPostIncExpr) {
        handle(expr)
        expr.arg.accept(this)
    }

    final override fun visit(expr: EtsPostDecExpr) {
        handle(expr)
        expr.arg.accept(this)
    }

    final override fun visit(expr: EtsEqExpr) {
        handle(expr)
        expr.left.accept(this)
        expr.right.accept(this)
    }

    final override fun visit(expr: EtsNotEqExpr) {
        handle(expr)
        expr.left.accept(this)
        expr.right.accept(this)
    }

    final override fun visit(expr: EtsStrictEqExpr) {
        handle(expr)
        expr.left.accept(this)
        expr.right.accept(this)
    }

    final override fun visit(expr: EtsStrictNotEqExpr) {
        handle(expr)
        expr.left.accept(this)
        expr.right.accept(this)
    }

    final override fun visit(expr: EtsLtExpr) {
        handle(expr)
        expr.left.accept(this)
        expr.right.accept(this)
    }

    final override fun visit(expr: EtsLtEqExpr) {
        handle(expr)
        expr.left.accept(this)
        expr.right.accept(this)
    }

    final override fun visit(expr: EtsGtExpr) {
        handle(expr)
        expr.left.accept(this)
        expr.right.accept(this)
    }

    final override fun visit(expr: EtsGtEqExpr) {
        handle(expr)
        expr.left.accept(this)
        expr.right.accept(this)
    }

    final override fun visit(expr: EtsInExpr) {
        handle(expr)
        expr.left.accept(this)
        expr.right.accept(this)
    }

    final override fun visit(expr: EtsAddExpr) {
        handle(expr)
        expr.left.accept(this)
        expr.right.accept(this)
    }

    final override fun visit(expr: EtsSubExpr) {
        handle(expr)
        expr.left.accept(this)
        expr.right.accept(this)
    }

    final override fun visit(expr: EtsMulExpr) {
        handle(expr)
        expr.left.accept(this)
        expr.right.accept(this)
    }

    final override fun visit(expr: EtsDivExpr) {
        handle(expr)
        expr.left.accept(this)
        expr.right.accept(this)
    }

    final override fun visit(expr: EtsRemExpr) {
        handle(expr)
        expr.left.accept(this)
        expr.right.accept(this)
    }

    final override fun visit(expr: EtsExpExpr) {
        handle(expr)
        expr.left.accept(this)
        expr.right.accept(this)
    }

    final override fun visit(expr: EtsBitAndExpr) {
        handle(expr)
        expr.left.accept(this)
        expr.right.accept(this)
    }

    final override fun visit(expr: EtsBitOrExpr) {
        handle(expr)
        expr.left.accept(this)
        expr.right.accept(this)
    }

    final override fun visit(expr: EtsBitXorExpr) {
        handle(expr)
        expr.left.accept(this)
        expr.right.accept(this)
    }

    final override fun visit(expr: EtsLeftShiftExpr) {
        handle(expr)
        expr.left.accept(this)
        expr.right.accept(this)
    }

    final override fun visit(expr: EtsRightShiftExpr) {
        handle(expr)
        expr.left.accept(this)
        expr.right.accept(this)
    }

    final override fun visit(expr: EtsUnsignedRightShiftExpr) {
        handle(expr)
        expr.left.accept(this)
        expr.right.accept(this)
    }

    final override fun visit(expr: EtsAndExpr) {
        handle(expr)
        expr.left.accept(this)
        expr.right.accept(this)
    }

    final override fun visit(expr: EtsOrExpr) {
        handle(expr)
        expr.left.accept(this)
        expr.right.accept(this)
    }

    final override fun visit(expr: EtsNullishCoalescingExpr) {
        handle(expr)
        expr.left.accept(this)
        expr.right.accept(this)
    }

    final override fun visit(expr: EtsInstanceCallExpr) {
        handle(expr)
        expr.instance.accept(this)
        expr.args.forEach { it.accept(this) }
    }

    final override fun visit(expr: EtsStaticCallExpr) {
        handle(expr)
        expr.args.forEach { it.accept(this) }
    }

    final override fun visit(expr: EtsPtrCallExpr) {
        handle(expr)
        expr.ptr.accept(this)
        expr.args.forEach { it.accept(this) }
    }
}
