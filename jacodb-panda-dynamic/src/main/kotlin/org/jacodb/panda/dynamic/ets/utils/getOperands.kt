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
    override fun visit(stmt: EtsNopStmt): Sequence<EtsEntity> = sequence {
        // empty
    }

    override fun visit(stmt: EtsAssignStmt): Sequence<EtsEntity> = sequence {
        yield(stmt.rhv)
    }

    override fun visit(stmt: EtsCallStmt): Sequence<EtsEntity> = sequence {
        yield(stmt.expr)
    }

    override fun visit(stmt: EtsReturnStmt): Sequence<EtsEntity> = sequence {
        if (stmt.returnValue != null) {
            yield(stmt.returnValue)
        }
    }

    override fun visit(stmt: EtsThrowStmt): Sequence<EtsEntity> = sequence {
        yield(stmt.arg)
    }

    override fun visit(stmt: EtsGotoStmt): Sequence<EtsEntity> = sequence {
        // empty
    }

    override fun visit(stmt: EtsIfStmt): Sequence<EtsEntity> = sequence {
        yield(stmt.condition)
    }

    override fun visit(stmt: EtsSwitchStmt): Sequence<EtsEntity> = sequence {
        yield(stmt.arg)
        yieldAll(stmt.cases)
    }
}

private object EntityGetOperands : EtsEntity.Visitor<Sequence<EtsEntity>> {
    override fun visit(value: EtsLocal): Sequence<EtsEntity> = sequence {
        // empty
    }

    override fun visit(value: EtsStringConstant): Sequence<EtsEntity> = sequence {
        // empty
    }

    override fun visit(value: EtsBooleanConstant): Sequence<EtsEntity> = sequence {
        // empty
    }

    override fun visit(value: EtsNumberConstant): Sequence<EtsEntity> = sequence {
        // empty
    }

    override fun visit(value: EtsNullConstant): Sequence<EtsEntity> = sequence {
        // empty
    }

    override fun visit(value: EtsUndefinedConstant): Sequence<EtsEntity> = sequence {
        // empty
    }

    override fun visit(value: EtsArrayLiteral): Sequence<EtsEntity> = sequence {
        // TODO: check
        for (element in value.elements) {
            yield(element)
        }
    }

    override fun visit(value: EtsObjectLiteral): Sequence<EtsEntity> = sequence {
        // TODO: check
        for ((_, v) in value.properties) {
            yield(v)
        }
    }

    override fun visit(expr: EtsNewExpr): Sequence<EtsEntity> = sequence {
        // empty
    }

    override fun visit(expr: EtsNewArrayExpr): Sequence<EtsEntity> = sequence {
        yield(expr.size)
    }

    override fun visit(expr: EtsTypeOfExpr): Sequence<EtsEntity> = sequence {
        yield(expr.arg)
    }

    override fun visit(expr: EtsInstanceOfExpr): Sequence<EtsEntity> = sequence {
        yield(expr.arg)
    }

    override fun visit(expr: EtsLengthExpr): Sequence<EtsEntity> = sequence {
        yield(expr.arg)
    }

    override fun visit(expr: EtsCastExpr): Sequence<EtsEntity> = sequence {
        yield(expr.arg)
    }

    override fun visit(expr: EtsPhiExpr): Sequence<EtsEntity> = sequence {
        yieldAll(expr.args)
    }

    override fun visit(expr: EtsUnaryOperation): Sequence<EtsEntity> = sequence {
        yield(expr.arg)
    }

    override fun visit(expr: EtsBinaryOperation): Sequence<EtsEntity> = sequence {
        yield(expr.left)
        yield(expr.right)
    }

    override fun visit(expr: EtsRelationOperation): Sequence<EtsEntity> = sequence {
        yield(expr.left)
        yield(expr.right)
    }

    override fun visit(expr: EtsInstanceCallExpr): Sequence<EtsEntity> = sequence {
        yield(expr.instance)
        yieldAll(expr.args)
    }

    override fun visit(expr: EtsStaticCallExpr): Sequence<EtsEntity> = sequence {
        yieldAll(expr.args)
    }

    override fun visit(ref: EtsThis): Sequence<EtsEntity> = sequence {
        // empty
    }

    override fun visit(ref: EtsParameterRef): Sequence<EtsEntity> = sequence {
        // empty
    }

    override fun visit(ref: EtsArrayAccess): Sequence<EtsEntity> = sequence {
        yield(ref.array)
        yield(ref.index)
    }

    override fun visit(ref: EtsInstanceFieldRef): Sequence<EtsEntity> = sequence {
        yield(ref.instance)
    }

    override fun visit(ref: EtsStaticFieldRef): Sequence<EtsEntity> = sequence {
        // empty
    }
}
