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
import org.jacodb.panda.dynamic.ark.base.This
import org.jacodb.panda.dynamic.ark.base.ThrowStmt
import org.jacodb.panda.dynamic.ark.base.TypeOfExpr
import org.jacodb.panda.dynamic.ark.base.UnaryOperation
import org.jacodb.panda.dynamic.ark.base.UndefinedConstant
import org.jacodb.panda.dynamic.ark.base.Value

fun Stmt.getUses(): Sequence<Value> {
    return accept(StmtGetUses)
}

fun Value.getUses(): Sequence<Value> {
    return accept(ValueGetUses)
}

private object StmtGetUses : Stmt.Visitor<Sequence<Value>> {
    override fun visit(stmt: NopStmt): Sequence<Value> = sequence {
        // empty
    }

    override fun visit(stmt: AssignStmt): Sequence<Value> = sequence {
        yieldAll(stmt.left.getUses())
        yield(stmt.right)
        yieldAll(stmt.right.getUses())
    }

    override fun visit(stmt: CallStmt): Sequence<Value> = sequence {
        yield(stmt.expr)
        yieldAll(stmt.expr.getUses())
    }

    override fun visit(stmt: DeleteStmt): Sequence<Value> = sequence {
        yield(stmt.arg)
        yieldAll(stmt.arg.getUses())
    }

    override fun visit(stmt: ReturnStmt): Sequence<Value> = sequence {
        if (stmt.arg != null) {
            yield(stmt.arg)
            yieldAll(stmt.arg.getUses())
        }
    }

    override fun visit(stmt: ThrowStmt): Sequence<Value> = sequence {
        yield(stmt.arg)
        yieldAll(stmt.arg.getUses())
    }

    override fun visit(stmt: GotoStmt): Sequence<Value> = sequence {
        // empty
    }

    override fun visit(stmt: IfStmt): Sequence<Value> = sequence {
        yield(stmt.condition)
        yieldAll(stmt.condition.getUses())
    }

    override fun visit(stmt: SwitchStmt): Sequence<Value> = sequence {
        yield(stmt.arg)
        yieldAll(stmt.arg.getUses())
        for (case in stmt.cases) {
            yield(case)
            yieldAll(case.getUses())
        }
    }
}

private object ValueGetUses : Value.Visitor<Sequence<Value>> {
    override fun visit(value: Local): Sequence<Value> = sequence {
        // empty
    }

    override fun visit(value: StringConstant): Sequence<Value> = sequence {
        // empty
    }

    override fun visit(value: BooleanConstant): Sequence<Value> = sequence {
        // empty
    }

    override fun visit(value: NumberConstant): Sequence<Value> = sequence {
        // empty
    }

    override fun visit(value: NullConstant): Sequence<Value> = sequence {
        // empty
    }

    override fun visit(value: UndefinedConstant): Sequence<Value> = sequence {
        // empty
    }

    override fun visit(value: ArrayLiteral): Sequence<Value> = sequence {
        // TODO: check
        for (element in value.elements) {
            yield(element)
            yieldAll(element.getUses())
        }
    }

    override fun visit(value: ObjectLiteral): Sequence<Value> = sequence {
        // TODO: check
        for ((_, v) in value.properties) {
            yield(v)
            yieldAll(v.getUses())
        }
    }

    override fun visit(expr: NewExpr): Sequence<Value> = sequence {
        // empty
    }

    override fun visit(expr: NewArrayExpr): Sequence<Value> = sequence {
        yield(expr.size)
        yieldAll(expr.size.getUses())
    }

    override fun visit(expr: TypeOfExpr): Sequence<Value> = sequence {
        yield(expr.arg)
        yieldAll(expr.arg.getUses())
    }

    override fun visit(expr: InstanceOfExpr): Sequence<Value> = sequence {
        yield(expr.arg)
        yieldAll(expr.arg.getUses())
    }

    override fun visit(expr: LengthExpr): Sequence<Value> = sequence {
        yield(expr.arg)
        yieldAll(expr.arg.getUses())
    }

    override fun visit(expr: CastExpr): Sequence<Value> = sequence {
        yield(expr.arg)
        yieldAll(expr.arg.getUses())
    }

    override fun visit(expr: PhiExpr): Sequence<Value> = sequence {
        for (arg in expr.args) {
            yieldAll(arg.getUses())
        }
    }

    override fun visit(expr: UnaryOperation): Sequence<Value> = sequence {
        yield(expr.arg)
        yieldAll(expr.arg.getUses())
    }

    override fun visit(expr: BinaryOperation): Sequence<Value> = sequence {
        yield(expr.left)
        yieldAll(expr.left.getUses())
        yield(expr.right)
        yieldAll(expr.right.getUses())
    }

    override fun visit(expr: RelationOperation): Sequence<Value> = sequence {
        yield(expr.left)
        yieldAll(expr.left.getUses())
        yield(expr.right)
        yieldAll(expr.right.getUses())
    }

    override fun visit(expr: InstanceCallExpr): Sequence<Value> = sequence {
        yield(expr.instance)
        yieldAll(expr.instance.getUses())
        for (arg in expr.args) {
            yield(arg)
            yieldAll(arg.getUses())
        }
    }

    override fun visit(expr: StaticCallExpr): Sequence<Value> = sequence {
        for (arg in expr.args) {
            yield(arg)
            yieldAll(arg.getUses())
        }
    }

    override fun visit(ref: This): Sequence<Value> = sequence {
        // empty
    }

    override fun visit(ref: ParameterRef): Sequence<Value> = sequence {
        // empty
    }

    override fun visit(ref: ArrayAccess): Sequence<Value> = sequence {
        yield(ref.array)
        yieldAll(ref.array.getUses())
        yield(ref.index)
        yieldAll(ref.index.getUses())
    }

    override fun visit(ref: InstanceFieldRef): Sequence<Value> = sequence {
        yield(ref.instance)
        yieldAll(ref.instance.getUses())
    }

    override fun visit(ref: StaticFieldRef): Sequence<Value> = sequence {
        // empty
    }
}
