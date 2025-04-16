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
import org.jacodb.ets.model.EtsDeleteExpr
import org.jacodb.ets.model.EtsDivExpr
import org.jacodb.ets.model.EtsEntity
import org.jacodb.ets.model.EtsEqExpr
import org.jacodb.ets.model.EtsExpExpr
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
        sequenceOf(stmt.exception)

    override fun visit(stmt: EtsIfStmt): Sequence<EtsEntity> =
        sequenceOf(stmt.condition)

    override fun visit(stmt: EtsRawStmt): Sequence<EtsEntity> =
        emptySequence()
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

    override fun visit(expr: EtsPtrCallExpr): Sequence<EtsEntity> =
        sequenceOf(expr.ptr) + expr.args.asSequence()

    override fun visit(value: EtsRawEntity): Sequence<EtsEntity> =
        emptySequence()
}
