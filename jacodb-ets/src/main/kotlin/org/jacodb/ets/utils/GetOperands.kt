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

import org.jacodb.ets.base.EtsAddExpr
import org.jacodb.ets.base.EtsAndExpr
import org.jacodb.ets.base.EtsArrayAccess
import org.jacodb.ets.base.EtsArrayLiteral
import org.jacodb.ets.base.EtsAssignStmt
import org.jacodb.ets.base.EtsAwaitExpr
import org.jacodb.ets.base.EtsBitAndExpr
import org.jacodb.ets.base.EtsBitNotExpr
import org.jacodb.ets.base.EtsBitOrExpr
import org.jacodb.ets.base.EtsBitXorExpr
import org.jacodb.ets.base.EtsBooleanConstant
import org.jacodb.ets.base.EtsCallStmt
import org.jacodb.ets.base.EtsCastExpr
import org.jacodb.ets.base.EtsCommaExpr
import org.jacodb.ets.base.EtsDeleteExpr
import org.jacodb.ets.base.EtsDivExpr
import org.jacodb.ets.base.EtsEntity
import org.jacodb.ets.base.EtsEqExpr
import org.jacodb.ets.base.EtsExpExpr
import org.jacodb.ets.base.EtsGotoStmt
import org.jacodb.ets.base.EtsGtEqExpr
import org.jacodb.ets.base.EtsGtExpr
import org.jacodb.ets.base.EtsIfStmt
import org.jacodb.ets.base.EtsInExpr
import org.jacodb.ets.base.EtsInstanceCallExpr
import org.jacodb.ets.base.EtsInstanceFieldRef
import org.jacodb.ets.base.EtsInstanceOfExpr
import org.jacodb.ets.base.EtsLeftShiftExpr
import org.jacodb.ets.base.EtsLengthExpr
import org.jacodb.ets.base.EtsLocal
import org.jacodb.ets.base.EtsLtEqExpr
import org.jacodb.ets.base.EtsLtExpr
import org.jacodb.ets.base.EtsMulExpr
import org.jacodb.ets.base.EtsNegExpr
import org.jacodb.ets.base.EtsNewArrayExpr
import org.jacodb.ets.base.EtsNewExpr
import org.jacodb.ets.base.EtsNopStmt
import org.jacodb.ets.base.EtsNotEqExpr
import org.jacodb.ets.base.EtsNotExpr
import org.jacodb.ets.base.EtsNullConstant
import org.jacodb.ets.base.EtsNullishCoalescingExpr
import org.jacodb.ets.base.EtsNumberConstant
import org.jacodb.ets.base.EtsObjectLiteral
import org.jacodb.ets.base.EtsOrExpr
import org.jacodb.ets.base.EtsParameterRef
import org.jacodb.ets.base.EtsPostDecExpr
import org.jacodb.ets.base.EtsPostIncExpr
import org.jacodb.ets.base.EtsPreDecExpr
import org.jacodb.ets.base.EtsPreIncExpr
import org.jacodb.ets.base.EtsRemExpr
import org.jacodb.ets.base.EtsReturnStmt
import org.jacodb.ets.base.EtsRightShiftExpr
import org.jacodb.ets.base.EtsStaticCallExpr
import org.jacodb.ets.base.EtsStaticFieldRef
import org.jacodb.ets.base.EtsStmt
import org.jacodb.ets.base.EtsStrictEqExpr
import org.jacodb.ets.base.EtsStrictNotEqExpr
import org.jacodb.ets.base.EtsStringConstant
import org.jacodb.ets.base.EtsSubExpr
import org.jacodb.ets.base.EtsSwitchStmt
import org.jacodb.ets.base.EtsTernaryExpr
import org.jacodb.ets.base.EtsThis
import org.jacodb.ets.base.EtsThrowStmt
import org.jacodb.ets.base.EtsTypeOfExpr
import org.jacodb.ets.base.EtsUnaryPlusExpr
import org.jacodb.ets.base.EtsUndefinedConstant
import org.jacodb.ets.base.EtsUnsignedRightShiftExpr
import org.jacodb.ets.base.EtsVoidExpr
import org.jacodb.ets.base.EtsYieldExpr

fun EtsStmt.getOperands(): Sequence<EtsEntity> {
    return accept(StmtGetOperands)
}

fun EtsEntity.getOperands(): Sequence<EtsEntity> {
    return accept(EntityGetOperands)
}

private object StmtGetOperands : EtsStmt.Visitor<Sequence<EtsEntity>> {

    override fun visit(stmt: EtsNopStmt): Sequence<EtsEntity> =
        emptySequence()

    override fun visit(stmt: EtsAssignStmt): Sequence<EtsEntity> =
        sequenceOf(stmt.rhv)

    override fun visit(stmt: EtsCallStmt): Sequence<EtsEntity> =
        sequenceOf(stmt.expr)

    override fun visit(stmt: EtsReturnStmt): Sequence<EtsEntity> =
        listOfNotNull(stmt.returnValue).asSequence()

    override fun visit(stmt: EtsThrowStmt): Sequence<EtsEntity> =
        sequenceOf(stmt.arg)

    override fun visit(stmt: EtsGotoStmt): Sequence<EtsEntity> =
        emptySequence()

    override fun visit(stmt: EtsIfStmt): Sequence<EtsEntity> =
        sequenceOf(stmt.condition)

    override fun visit(stmt: EtsSwitchStmt): Sequence<EtsEntity> =
        sequenceOf(stmt.arg) + stmt.cases.asSequence()
}

private object EntityGetOperands : EtsEntity.Visitor<Sequence<EtsEntity>> {

    override fun visit(value: EtsLocal): Sequence<EtsEntity> =
        emptySequence()

    override fun visit(value: EtsStringConstant): Sequence<EtsEntity> =
        emptySequence()

    override fun visit(value: EtsBooleanConstant): Sequence<EtsEntity> =
        emptySequence()

    override fun visit(value: EtsNumberConstant): Sequence<EtsEntity> =
        emptySequence()

    override fun visit(value: EtsNullConstant): Sequence<EtsEntity> =
        emptySequence()

    override fun visit(value: EtsUndefinedConstant): Sequence<EtsEntity> =
        emptySequence()

    override fun visit(value: EtsArrayLiteral): Sequence<EtsEntity> =
        value.elements.asSequence()

    // TODO: check
    override fun visit(value: EtsObjectLiteral): Sequence<EtsEntity> =
        value.properties.asSequence().map { (_, v) -> v }

    override fun visit(value: EtsThis): Sequence<EtsEntity> =
        emptySequence()

    override fun visit(value: EtsParameterRef): Sequence<EtsEntity> =
        emptySequence()

    override fun visit(value: EtsArrayAccess): Sequence<EtsEntity> =
        sequenceOf(value.array, value.index)

    override fun visit(value: EtsInstanceFieldRef): Sequence<EtsEntity> =
        sequenceOf(value.instance)

    override fun visit(value: EtsStaticFieldRef): Sequence<EtsEntity> =
        emptySequence()

    override fun visit(expr: EtsNewExpr): Sequence<EtsEntity> =
        emptySequence()

    override fun visit(expr: EtsNewArrayExpr): Sequence<EtsEntity> =
        sequenceOf(expr.size)

    override fun visit(expr: EtsLengthExpr): Sequence<EtsEntity> =
        sequenceOf(expr.arg)

    override fun visit(expr: EtsCastExpr): Sequence<EtsEntity> =
        sequenceOf(expr.arg)

    override fun visit(expr: EtsInstanceOfExpr): Sequence<EtsEntity> =
        sequenceOf(expr.arg)

    override fun visit(expr: EtsDeleteExpr): Sequence<EtsEntity> =
        sequenceOf(expr.arg)

    override fun visit(expr: EtsAwaitExpr): Sequence<EtsEntity> =
        sequenceOf(expr.arg)

    override fun visit(expr: EtsYieldExpr): Sequence<EtsEntity> =
        sequenceOf(expr.arg)

    override fun visit(expr: EtsTypeOfExpr): Sequence<EtsEntity> =
        sequenceOf(expr.arg)

    override fun visit(expr: EtsVoidExpr): Sequence<EtsEntity> =
        sequenceOf(expr.arg)

    override fun visit(expr: EtsNotExpr): Sequence<EtsEntity> =
        sequenceOf(expr.arg)

    override fun visit(expr: EtsBitNotExpr): Sequence<EtsEntity> =
        sequenceOf(expr.arg)

    override fun visit(expr: EtsNegExpr): Sequence<EtsEntity> =
        sequenceOf(expr.arg)

    override fun visit(expr: EtsUnaryPlusExpr): Sequence<EtsEntity> =
        sequenceOf(expr.arg)

    override fun visit(expr: EtsPreIncExpr): Sequence<EtsEntity> =
        sequenceOf(expr.arg)

    override fun visit(expr: EtsPreDecExpr): Sequence<EtsEntity> =
        sequenceOf(expr.arg)

    override fun visit(expr: EtsPostIncExpr): Sequence<EtsEntity> =
        sequenceOf(expr.arg)

    override fun visit(expr: EtsPostDecExpr): Sequence<EtsEntity> =
        sequenceOf(expr.arg)

    override fun visit(expr: EtsEqExpr): Sequence<EtsEntity> =
        sequenceOf(expr.left, expr.right)

    override fun visit(expr: EtsNotEqExpr): Sequence<EtsEntity> =
        sequenceOf(expr.left, expr.right)

    override fun visit(expr: EtsStrictEqExpr): Sequence<EtsEntity> =
        sequenceOf(expr.left, expr.right)

    override fun visit(expr: EtsStrictNotEqExpr): Sequence<EtsEntity> =
        sequenceOf(expr.left, expr.right)

    override fun visit(expr: EtsLtExpr): Sequence<EtsEntity> =
        sequenceOf(expr.left, expr.right)

    override fun visit(expr: EtsLtEqExpr): Sequence<EtsEntity> =
        sequenceOf(expr.left, expr.right)

    override fun visit(expr: EtsGtExpr): Sequence<EtsEntity> =
        sequenceOf(expr.left, expr.right)

    override fun visit(expr: EtsGtEqExpr): Sequence<EtsEntity> =
        sequenceOf(expr.left, expr.right)

    override fun visit(expr: EtsInExpr): Sequence<EtsEntity> =
        sequenceOf(expr.left, expr.right)

    override fun visit(expr: EtsAddExpr): Sequence<EtsEntity> =
        sequenceOf(expr.left, expr.right)

    override fun visit(expr: EtsSubExpr): Sequence<EtsEntity> =
        sequenceOf(expr.left, expr.right)

    override fun visit(expr: EtsMulExpr): Sequence<EtsEntity> =
        sequenceOf(expr.left, expr.right)

    override fun visit(expr: EtsDivExpr): Sequence<EtsEntity> =
        sequenceOf(expr.left, expr.right)

    override fun visit(expr: EtsRemExpr): Sequence<EtsEntity> =
        sequenceOf(expr.left, expr.right)

    override fun visit(expr: EtsExpExpr): Sequence<EtsEntity> =
        sequenceOf(expr.left, expr.right)

    override fun visit(expr: EtsBitAndExpr): Sequence<EtsEntity> =
        sequenceOf(expr.left, expr.right)

    override fun visit(expr: EtsBitOrExpr): Sequence<EtsEntity> =
        sequenceOf(expr.left, expr.right)

    override fun visit(expr: EtsBitXorExpr): Sequence<EtsEntity> =
        sequenceOf(expr.left, expr.right)

    override fun visit(expr: EtsLeftShiftExpr): Sequence<EtsEntity> =
        sequenceOf(expr.left, expr.right)

    override fun visit(expr: EtsRightShiftExpr): Sequence<EtsEntity> =
        sequenceOf(expr.left, expr.right)

    override fun visit(expr: EtsUnsignedRightShiftExpr): Sequence<EtsEntity> =
        sequenceOf(expr.left, expr.right)

    override fun visit(expr: EtsAndExpr): Sequence<EtsEntity> =
        sequenceOf(expr.left, expr.right)

    override fun visit(expr: EtsOrExpr): Sequence<EtsEntity> =
        sequenceOf(expr.left, expr.right)

    override fun visit(expr: EtsNullishCoalescingExpr): Sequence<EtsEntity> =
        sequenceOf(expr.left, expr.right)

    override fun visit(expr: EtsInstanceCallExpr): Sequence<EtsEntity> =
        sequenceOf(expr.instance) + expr.args.asSequence()

    override fun visit(expr: EtsStaticCallExpr): Sequence<EtsEntity> =
        expr.args.asSequence()

    override fun visit(expr: EtsCommaExpr): Sequence<EtsEntity> =
        sequenceOf(expr.left, expr.right)

    override fun visit(expr: EtsTernaryExpr): Sequence<EtsEntity> =
        sequenceOf(expr.condition, expr.thenExpr, expr.elseExpr)
}
