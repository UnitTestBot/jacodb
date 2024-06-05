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

package org.jacodb.panda.dynamic.ark.base

interface Stmt {
    interface Visitor<out R> {
        fun visit(stmt: NopStmt): R
        fun visit(stmt: AssignStmt): R
        fun visit(stmt: CallStmt): R
        fun visit(stmt: DeleteStmt): R
        fun visit(stmt: ReturnStmt): R
        fun visit(stmt: ThrowStmt): R
        fun visit(stmt: GotoStmt): R
        fun visit(stmt: IfStmt): R
        fun visit(stmt: SwitchStmt): R

        interface Default<out R> : Visitor<R> {
            override fun visit(stmt: NopStmt): R = defaultVisit(stmt)
            override fun visit(stmt: AssignStmt): R = defaultVisit(stmt)
            override fun visit(stmt: CallStmt): R = defaultVisit(stmt)
            override fun visit(stmt: DeleteStmt): R = defaultVisit(stmt)
            override fun visit(stmt: ReturnStmt): R = defaultVisit(stmt)
            override fun visit(stmt: ThrowStmt): R = defaultVisit(stmt)
            override fun visit(stmt: GotoStmt): R = defaultVisit(stmt)
            override fun visit(stmt: IfStmt): R = defaultVisit(stmt)
            override fun visit(stmt: SwitchStmt): R = defaultVisit(stmt)

            fun defaultVisit(stmt: Stmt): R
        }
    }

    fun <R> accept(visitor: Visitor<R>): R
}

object NopStmt : Stmt {
    override fun toString(): String = "nop"

    override fun <R> accept(visitor: Stmt.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class AssignStmt(
    val left: Value, // Local
    val right: Value,
) : Stmt {
    override fun toString(): String {
        return "$left := $right"
    }

    override fun <R> accept(visitor: Stmt.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class CallStmt(
    val expr: CallExpr,
) : Stmt {
    override fun toString(): String {
        return expr.toString()
    }

    override fun <R> accept(visitor: Stmt.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class DeleteStmt(
    val arg: FieldRef,
) : Stmt {
    override fun toString(): String {
        return "delete $arg"
    }

    override fun <R> accept(visitor: Stmt.Visitor<R>): R {
        return visitor.visit(this)
    }
}

interface TerminatingStmt : Stmt

data class ReturnStmt(
    val arg: Value?,
) : TerminatingStmt {
    override fun toString(): String {
        return if (arg != null) {
            "return $arg"
        } else {
            "return"
        }
    }

    override fun <R> accept(visitor: Stmt.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class ThrowStmt(
    val arg: Value,
) : TerminatingStmt {
    override fun toString(): String {
        return "throw $arg"
    }

    override fun <R> accept(visitor: Stmt.Visitor<R>): R {
        return visitor.visit(this)
    }
}

interface BranchingStmt : Stmt

object GotoStmt : BranchingStmt {
    override fun toString(): String = "goto"

    override fun <R> accept(visitor: Stmt.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class IfStmt(
    val condition: ConditionExpr,
) : BranchingStmt {
    override fun toString(): String {
        return "if ($condition)"
    }

    override fun <R> accept(visitor: Stmt.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class SwitchStmt(
    val arg: Value,
    val cases: List<Value>,
) : BranchingStmt {
    override fun toString(): String {
        return "switch ($arg)"
    }

    override fun <R> accept(visitor: Stmt.Visitor<R>): R {
        return visitor.visit(this)
    }
}
