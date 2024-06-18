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

import org.jacodb.api.common.cfg.CommonAssignInst
import org.jacodb.api.common.cfg.CommonInst
import org.jacodb.api.common.cfg.CommonInstLocation
import org.jacodb.panda.dynamic.ark.model.ArkMethod

data class ArkInstLocation(
    override val method: ArkMethod,
    // val index: Int,
    // val lineNumber: Int,
) : CommonInstLocation

interface Stmt : CommonInst {
    override val location: ArkInstLocation

    override val method: ArkMethod
        get() = location.method

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

data class NopStmt(
    override val location: ArkInstLocation,
) : Stmt {
    override fun toString(): String = "nop"

    override fun <R> accept(visitor: Stmt.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class AssignStmt(
    override val location: ArkInstLocation,
    override val lhv: ArkLValue,
    override val rhv: ArkEntity,
) : Stmt, CommonAssignInst {
    override fun toString(): String {
        return "$lhv := $rhv"
    }

    override fun <R> accept(visitor: Stmt.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class CallStmt(
    override val location: ArkInstLocation,
    val expr: ArkCallExpr,
) : Stmt {
    override fun toString(): String {
        return expr.toString()
    }

    override fun <R> accept(visitor: Stmt.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class DeleteStmt(
    override val location: ArkInstLocation,
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
    override val location: ArkInstLocation,
    val arg: ArkEntity?,
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
    override val location: ArkInstLocation,
    val arg: ArkEntity,
) : TerminatingStmt {
    override fun toString(): String {
        return "throw $arg"
    }

    override fun <R> accept(visitor: Stmt.Visitor<R>): R {
        return visitor.visit(this)
    }
}

interface BranchingStmt : Stmt

class GotoStmt(
    override val location: ArkInstLocation,
) : BranchingStmt {
    override fun toString(): String = "goto"

    override fun <R> accept(visitor: Stmt.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class IfStmt(
    override val location: ArkInstLocation,
    val condition: ArkConditionExpr,
) : BranchingStmt {
    override fun toString(): String {
        return "if ($condition)"
    }

    override fun <R> accept(visitor: Stmt.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class SwitchStmt(
    override val location: ArkInstLocation,
    val arg: ArkEntity,
    val cases: List<ArkEntity>,
) : BranchingStmt {
    override fun toString(): String {
        return "switch ($arg)"
    }

    override fun <R> accept(visitor: Stmt.Visitor<R>): R {
        return visitor.visit(this)
    }
}
