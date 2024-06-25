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

package org.jacodb.panda.dynamic.ark.utils

import org.jacodb.panda.dynamic.ark.base.ArkArrayAccess
import org.jacodb.panda.dynamic.ark.base.ArkArrayLiteral
import org.jacodb.panda.dynamic.ark.base.ArkAssignStmt
import org.jacodb.panda.dynamic.ark.base.ArkBinaryOperation
import org.jacodb.panda.dynamic.ark.base.ArkBooleanConstant
import org.jacodb.panda.dynamic.ark.base.ArkCallStmt
import org.jacodb.panda.dynamic.ark.base.ArkCastExpr
import org.jacodb.panda.dynamic.ark.base.ArkDeleteStmt
import org.jacodb.panda.dynamic.ark.base.ArkEntity
import org.jacodb.panda.dynamic.ark.base.ArkGotoStmt
import org.jacodb.panda.dynamic.ark.base.ArkIfStmt
import org.jacodb.panda.dynamic.ark.base.ArkInstanceCallExpr
import org.jacodb.panda.dynamic.ark.base.ArkInstanceFieldRef
import org.jacodb.panda.dynamic.ark.base.ArkInstanceOfExpr
import org.jacodb.panda.dynamic.ark.base.ArkLengthExpr
import org.jacodb.panda.dynamic.ark.base.ArkLocal
import org.jacodb.panda.dynamic.ark.base.ArkNewArrayExpr
import org.jacodb.panda.dynamic.ark.base.ArkNewExpr
import org.jacodb.panda.dynamic.ark.base.ArkNopStmt
import org.jacodb.panda.dynamic.ark.base.ArkNullConstant
import org.jacodb.panda.dynamic.ark.base.ArkNumberConstant
import org.jacodb.panda.dynamic.ark.base.ArkObjectLiteral
import org.jacodb.panda.dynamic.ark.base.ArkParameterRef
import org.jacodb.panda.dynamic.ark.base.ArkPhiExpr
import org.jacodb.panda.dynamic.ark.base.ArkRelationOperation
import org.jacodb.panda.dynamic.ark.base.ArkReturnStmt
import org.jacodb.panda.dynamic.ark.base.ArkStaticCallExpr
import org.jacodb.panda.dynamic.ark.base.ArkStaticFieldRef
import org.jacodb.panda.dynamic.ark.base.ArkStmt
import org.jacodb.panda.dynamic.ark.base.ArkStringConstant
import org.jacodb.panda.dynamic.ark.base.ArkSwitchStmt
import org.jacodb.panda.dynamic.ark.base.ArkThis
import org.jacodb.panda.dynamic.ark.base.ArkThrowStmt
import org.jacodb.panda.dynamic.ark.base.ArkTypeOfExpr
import org.jacodb.panda.dynamic.ark.base.ArkUnaryOperation
import org.jacodb.panda.dynamic.ark.base.ArkUndefinedConstant

fun ArkStmt.getOperands(): Sequence<ArkEntity> {
    return accept(StmtGetOperands)
}

fun ArkEntity.getOperands(): Sequence<ArkEntity> {
    return accept(EntityGetOperands)
}

private object StmtGetOperands : ArkStmt.Visitor<Sequence<ArkEntity>> {
    override fun visit(stmt: ArkNopStmt): Sequence<ArkEntity> = sequence {
        // empty
    }

    override fun visit(stmt: ArkAssignStmt): Sequence<ArkEntity> = sequence {
        yield(stmt.rhv)
    }

    override fun visit(stmt: ArkCallStmt): Sequence<ArkEntity> = sequence {
        yield(stmt.expr)
    }

    override fun visit(stmt: ArkDeleteStmt): Sequence<ArkEntity> = sequence {
        yield(stmt.arg)
    }

    override fun visit(stmt: ArkReturnStmt): Sequence<ArkEntity> = sequence {
        if (stmt.returnValue != null) {
            yield(stmt.returnValue)
        }
    }

    override fun visit(stmt: ArkThrowStmt): Sequence<ArkEntity> = sequence {
        yield(stmt.arg)
    }

    override fun visit(stmt: ArkGotoStmt): Sequence<ArkEntity> = sequence {
        // empty
    }

    override fun visit(stmt: ArkIfStmt): Sequence<ArkEntity> = sequence {
        yield(stmt.condition)
    }

    override fun visit(stmt: ArkSwitchStmt): Sequence<ArkEntity> = sequence {
        yield(stmt.arg)
        yieldAll(stmt.cases)
    }
}

private object EntityGetOperands : ArkEntity.Visitor<Sequence<ArkEntity>> {
    override fun visit(value: ArkLocal): Sequence<ArkEntity> = sequence {
        // empty
    }

    override fun visit(value: ArkStringConstant): Sequence<ArkEntity> = sequence {
        // empty
    }

    override fun visit(value: ArkBooleanConstant): Sequence<ArkEntity> = sequence {
        // empty
    }

    override fun visit(value: ArkNumberConstant): Sequence<ArkEntity> = sequence {
        // empty
    }

    override fun visit(value: ArkNullConstant): Sequence<ArkEntity> = sequence {
        // empty
    }

    override fun visit(value: ArkUndefinedConstant): Sequence<ArkEntity> = sequence {
        // empty
    }

    override fun visit(value: ArkArrayLiteral): Sequence<ArkEntity> = sequence {
        // TODO: check
        for (element in value.elements) {
            yield(element)
        }
    }

    override fun visit(value: ArkObjectLiteral): Sequence<ArkEntity> = sequence {
        // TODO: check
        for ((_, v) in value.properties) {
            yield(v)
        }
    }

    override fun visit(expr: ArkNewExpr): Sequence<ArkEntity> = sequence {
        // empty
    }

    override fun visit(expr: ArkNewArrayExpr): Sequence<ArkEntity> = sequence {
        yield(expr.size)
    }

    override fun visit(expr: ArkTypeOfExpr): Sequence<ArkEntity> = sequence {
        yield(expr.arg)
    }

    override fun visit(expr: ArkInstanceOfExpr): Sequence<ArkEntity> = sequence {
        yield(expr.arg)
    }

    override fun visit(expr: ArkLengthExpr): Sequence<ArkEntity> = sequence {
        yield(expr.arg)
    }

    override fun visit(expr: ArkCastExpr): Sequence<ArkEntity> = sequence {
        yield(expr.arg)
    }

    override fun visit(expr: ArkPhiExpr): Sequence<ArkEntity> = sequence {
        yieldAll(expr.args)
    }

    override fun visit(expr: ArkUnaryOperation): Sequence<ArkEntity> = sequence {
        yield(expr.arg)
    }

    override fun visit(expr: ArkBinaryOperation): Sequence<ArkEntity> = sequence {
        yield(expr.left)
        yield(expr.right)
    }

    override fun visit(expr: ArkRelationOperation): Sequence<ArkEntity> = sequence {
        yield(expr.left)
        yield(expr.right)
    }

    override fun visit(expr: ArkInstanceCallExpr): Sequence<ArkEntity> = sequence {
        yield(expr.instance)
        yieldAll(expr.args)
    }

    override fun visit(expr: ArkStaticCallExpr): Sequence<ArkEntity> = sequence {
        yieldAll(expr.args)
    }

    override fun visit(ref: ArkThis): Sequence<ArkEntity> = sequence {
        // empty
    }

    override fun visit(ref: ArkParameterRef): Sequence<ArkEntity> = sequence {
        // empty
    }

    override fun visit(ref: ArkArrayAccess): Sequence<ArkEntity> = sequence {
        yield(ref.array)
        yield(ref.index)
    }

    override fun visit(ref: ArkInstanceFieldRef): Sequence<ArkEntity> = sequence {
        yield(ref.instance)
    }

    override fun visit(ref: ArkStaticFieldRef): Sequence<ArkEntity> = sequence {
        // empty
    }
}
