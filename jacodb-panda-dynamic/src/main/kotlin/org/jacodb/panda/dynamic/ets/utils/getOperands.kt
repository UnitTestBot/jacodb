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

package org.jacodb.panda.dynamic.ets.utils

import org.jacodb.panda.dynamic.ets.base.EtsArrayAccess
import org.jacodb.panda.dynamic.ets.base.EtsArrayLiteral
import org.jacodb.panda.dynamic.ets.base.EtsAssignStmt
import org.jacodb.panda.dynamic.ets.base.EtsBinaryOperation
import org.jacodb.panda.dynamic.ets.base.EtsBooleanConstant
import org.jacodb.panda.dynamic.ets.base.EtsCallStmt
import org.jacodb.panda.dynamic.ets.base.EtsCastExpr
import org.jacodb.panda.dynamic.ets.base.EtsDeleteExpr
import org.jacodb.panda.dynamic.ets.base.EtsEntity
import org.jacodb.panda.dynamic.ets.base.EtsGotoStmt
import org.jacodb.panda.dynamic.ets.base.EtsIfStmt
import org.jacodb.panda.dynamic.ets.base.EtsInstanceCallExpr
import org.jacodb.panda.dynamic.ets.base.EtsInstanceFieldRef
import org.jacodb.panda.dynamic.ets.base.EtsInstanceOfExpr
import org.jacodb.panda.dynamic.ets.base.EtsLengthExpr
import org.jacodb.panda.dynamic.ets.base.EtsLocal
import org.jacodb.panda.dynamic.ets.base.EtsNewArrayExpr
import org.jacodb.panda.dynamic.ets.base.EtsNewExpr
import org.jacodb.panda.dynamic.ets.base.EtsNopStmt
import org.jacodb.panda.dynamic.ets.base.EtsNullConstant
import org.jacodb.panda.dynamic.ets.base.EtsNumberConstant
import org.jacodb.panda.dynamic.ets.base.EtsObjectLiteral
import org.jacodb.panda.dynamic.ets.base.EtsParameterRef
import org.jacodb.panda.dynamic.ets.base.EtsPhiExpr
import org.jacodb.panda.dynamic.ets.base.EtsRelationOperation
import org.jacodb.panda.dynamic.ets.base.EtsReturnStmt
import org.jacodb.panda.dynamic.ets.base.EtsStaticCallExpr
import org.jacodb.panda.dynamic.ets.base.EtsStaticFieldRef
import org.jacodb.panda.dynamic.ets.base.EtsStmt
import org.jacodb.panda.dynamic.ets.base.EtsStringConstant
import org.jacodb.panda.dynamic.ets.base.EtsSwitchStmt
import org.jacodb.panda.dynamic.ets.base.EtsThis
import org.jacodb.panda.dynamic.ets.base.EtsThrowStmt
import org.jacodb.panda.dynamic.ets.base.EtsTypeOfExpr
import org.jacodb.panda.dynamic.ets.base.EtsUnaryOperation
import org.jacodb.panda.dynamic.ets.base.EtsUndefinedConstant

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

    override fun visit(stmt: EtsSwitchStmt): Sequence<EtsEntity>  =
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
        value.properties.asSequence().map { it.second }

    override fun visit(expr: EtsNewExpr): Sequence<EtsEntity> =
        emptySequence()

    override fun visit(expr: EtsNewArrayExpr): Sequence<EtsEntity> =
        sequenceOf(expr.size)

    override fun visit(expr: EtsDeleteExpr): Sequence<EtsEntity> =
        sequenceOf(expr.arg)

    override fun visit(expr: EtsTypeOfExpr): Sequence<EtsEntity> =
        sequenceOf(expr.arg)

    override fun visit(expr: EtsInstanceOfExpr): Sequence<EtsEntity> =
        sequenceOf(expr.arg)

    override fun visit(expr: EtsLengthExpr): Sequence<EtsEntity> =
        sequenceOf(expr.arg)

    override fun visit(expr: EtsCastExpr): Sequence<EtsEntity> =
        sequenceOf(expr.arg)

    override fun visit(expr: EtsPhiExpr): Sequence<EtsEntity> =
        expr.args.asSequence()

    override fun visit(expr: EtsUnaryOperation): Sequence<EtsEntity> =
        sequenceOf(expr.arg)

    override fun visit(expr: EtsBinaryOperation): Sequence<EtsEntity> =
        sequenceOf(expr.left, expr.right)

    override fun visit(expr: EtsRelationOperation): Sequence<EtsEntity> =
        sequenceOf(expr.left, expr.right)

    override fun visit(expr: EtsInstanceCallExpr): Sequence<EtsEntity> =
        sequenceOf(expr.instance) + expr.args.asSequence()

    override fun visit(expr: EtsStaticCallExpr): Sequence<EtsEntity> =
        expr.args.asSequence()

    override fun visit(ref: EtsThis): Sequence<EtsEntity> =
        emptySequence()

    override fun visit(ref: EtsParameterRef): Sequence<EtsEntity> =
        emptySequence()

    override fun visit(ref: EtsArrayAccess): Sequence<EtsEntity> =
        sequenceOf(ref.array, ref.index)

    override fun visit(ref: EtsInstanceFieldRef): Sequence<EtsEntity> =
        sequenceOf(ref.instance)

    override fun visit(ref: EtsStaticFieldRef): Sequence<EtsEntity> =
        emptySequence()
}
