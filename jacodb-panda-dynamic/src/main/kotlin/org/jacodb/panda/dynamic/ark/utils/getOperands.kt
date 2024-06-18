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

import org.jacodb.panda.dynamic.ark.base.ArkEntity
import org.jacodb.panda.dynamic.ark.base.ArkThis
import org.jacodb.panda.dynamic.ark.base.ArrayAccess
import org.jacodb.panda.dynamic.ark.base.ArrayLiteral
import org.jacodb.panda.dynamic.ark.base.AssignStmt
import org.jacodb.panda.dynamic.ark.base.BinaryOperation
import org.jacodb.panda.dynamic.ark.base.BooleanConstant
import org.jacodb.panda.dynamic.ark.base.CallStmt
import org.jacodb.panda.dynamic.ark.base.CastExpr
import org.jacodb.panda.dynamic.ark.base.DeleteStmt
import org.jacodb.panda.dynamic.ark.base.GotoStmt
import org.jacodb.panda.dynamic.ark.base.IfStmt
import org.jacodb.panda.dynamic.ark.base.InstanceCallExpr
import org.jacodb.panda.dynamic.ark.base.InstanceFieldRef
import org.jacodb.panda.dynamic.ark.base.InstanceOfExpr
import org.jacodb.panda.dynamic.ark.base.LengthExpr
import org.jacodb.panda.dynamic.ark.base.Local
import org.jacodb.panda.dynamic.ark.base.NewArrayExpr
import org.jacodb.panda.dynamic.ark.base.NewExpr
import org.jacodb.panda.dynamic.ark.base.NopStmt
import org.jacodb.panda.dynamic.ark.base.NullConstant
import org.jacodb.panda.dynamic.ark.base.NumberConstant
import org.jacodb.panda.dynamic.ark.base.ObjectLiteral
import org.jacodb.panda.dynamic.ark.base.ParameterRef
import org.jacodb.panda.dynamic.ark.base.PhiExpr
import org.jacodb.panda.dynamic.ark.base.RelationOperation
import org.jacodb.panda.dynamic.ark.base.ReturnStmt
import org.jacodb.panda.dynamic.ark.base.StaticCallExpr
import org.jacodb.panda.dynamic.ark.base.StaticFieldRef
import org.jacodb.panda.dynamic.ark.base.Stmt
import org.jacodb.panda.dynamic.ark.base.StringConstant
import org.jacodb.panda.dynamic.ark.base.SwitchStmt
import org.jacodb.panda.dynamic.ark.base.ThrowStmt
import org.jacodb.panda.dynamic.ark.base.TypeOfExpr
import org.jacodb.panda.dynamic.ark.base.UnaryOperation
import org.jacodb.panda.dynamic.ark.base.UndefinedConstant

fun Stmt.getOperands(): Sequence<ArkEntity> {
    return accept(StmtGetOperands)
}

fun ArkEntity.getOperands(): Sequence<ArkEntity> {
    return accept(ValueGetOperands)
}

private object StmtGetOperands : Stmt.Visitor<Sequence<ArkEntity>> {
    override fun visit(stmt: NopStmt): Sequence<ArkEntity> = sequence {
        // empty
    }

    override fun visit(stmt: AssignStmt): Sequence<ArkEntity> = sequence {
        yield(stmt.rhv)
    }

    override fun visit(stmt: CallStmt): Sequence<ArkEntity> = sequence {
        yield(stmt.expr)
    }

    override fun visit(stmt: DeleteStmt): Sequence<ArkEntity> = sequence {
        yield(stmt.arg)
    }

    override fun visit(stmt: ReturnStmt): Sequence<ArkEntity> = sequence {
        if (stmt.arg != null) {
            yield(stmt.arg)
        }
    }

    override fun visit(stmt: ThrowStmt): Sequence<ArkEntity> = sequence {
        yield(stmt.arg)
    }

    override fun visit(stmt: GotoStmt): Sequence<ArkEntity> = sequence {
        // empty
    }

    override fun visit(stmt: IfStmt): Sequence<ArkEntity> = sequence {
        yield(stmt.condition)
    }

    override fun visit(stmt: SwitchStmt): Sequence<ArkEntity> = sequence {
        yield(stmt.arg)
        for (case in stmt.cases) {
            yield(case)
        }
    }
}

private object ValueGetOperands : ArkEntity.Visitor<Sequence<ArkEntity>> {
    override fun visit(value: Local): Sequence<ArkEntity> = sequence {
        // empty
    }

    override fun visit(value: StringConstant): Sequence<ArkEntity> = sequence {
        // empty
    }

    override fun visit(value: BooleanConstant): Sequence<ArkEntity> = sequence {
        // empty
    }

    override fun visit(value: NumberConstant): Sequence<ArkEntity> = sequence {
        // empty
    }

    override fun visit(value: NullConstant): Sequence<ArkEntity> = sequence {
        // empty
    }

    override fun visit(value: UndefinedConstant): Sequence<ArkEntity> = sequence {
        // empty
    }

    override fun visit(value: ArrayLiteral): Sequence<ArkEntity> = sequence {
        // TODO: check
        for (element in value.elements) {
            yield(element)
        }
    }

    override fun visit(value: ObjectLiteral): Sequence<ArkEntity> = sequence {
        // TODO: check
        for ((_, v) in value.properties) {
            yield(v)
        }
    }

    override fun visit(expr: NewExpr): Sequence<ArkEntity> = sequence {
        // empty
    }

    override fun visit(expr: NewArrayExpr): Sequence<ArkEntity> = sequence {
        yield(expr.size)
    }

    override fun visit(expr: TypeOfExpr): Sequence<ArkEntity> = sequence {
        yield(expr.arg)
    }

    override fun visit(expr: InstanceOfExpr): Sequence<ArkEntity> = sequence {
        yield(expr.arg)
    }

    override fun visit(expr: LengthExpr): Sequence<ArkEntity> = sequence {
        yield(expr.arg)
    }

    override fun visit(expr: CastExpr): Sequence<ArkEntity> = sequence {
        yield(expr.arg)
    }

    override fun visit(expr: PhiExpr): Sequence<ArkEntity> = sequence {
        yieldAll(expr.args)
    }

    override fun visit(expr: UnaryOperation): Sequence<ArkEntity> = sequence {
        yield(expr.arg)
    }

    override fun visit(expr: BinaryOperation): Sequence<ArkEntity> = sequence {
        yield(expr.left)
        yield(expr.right)
    }

    override fun visit(expr: RelationOperation): Sequence<ArkEntity> = sequence {
        yield(expr.left)
        yield(expr.right)
    }

    override fun visit(expr: InstanceCallExpr): Sequence<ArkEntity> = sequence {
        yield(expr.instance)
        yieldAll(expr.args)
    }

    override fun visit(expr: StaticCallExpr): Sequence<ArkEntity> = sequence {
        yieldAll(expr.args)
    }

    override fun visit(ref: ArkThis): Sequence<ArkEntity> = sequence {
        // empty
    }

    override fun visit(ref: ParameterRef): Sequence<ArkEntity> = sequence {
        // empty
    }

    override fun visit(ref: ArrayAccess): Sequence<ArkEntity> = sequence {
        yield(ref.array)
        yield(ref.index)
    }

    override fun visit(ref: InstanceFieldRef): Sequence<ArkEntity> = sequence {
        yield(ref.instance)
    }

    override fun visit(ref: StaticFieldRef): Sequence<ArkEntity> = sequence {
        // empty
    }
}
