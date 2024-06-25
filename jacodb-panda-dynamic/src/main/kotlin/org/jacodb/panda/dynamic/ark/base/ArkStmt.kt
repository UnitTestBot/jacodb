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
import org.jacodb.api.common.cfg.CommonReturnInst
import org.jacodb.panda.dynamic.ark.model.ArkMethod

data class ArkInstLocation(
    override val method: ArkMethod,
    val index: Int,
) : CommonInstLocation

interface ArkStmt : CommonInst {
    override val location: ArkInstLocation

    override val method: ArkMethod
        get() = location.method

    interface Visitor<out R> {
        fun visit(stmt: ArkNopStmt): R
        fun visit(stmt: ArkAssignStmt): R
        fun visit(stmt: ArkCallStmt): R
        fun visit(stmt: ArkDeleteStmt): R
        fun visit(stmt: ArkReturnStmt): R
        fun visit(stmt: ArkThrowStmt): R
        fun visit(stmt: ArkGotoStmt): R
        fun visit(stmt: ArkIfStmt): R
        fun visit(stmt: ArkSwitchStmt): R

        interface Default<out R> : Visitor<R> {
            override fun visit(stmt: ArkNopStmt): R = defaultVisit(stmt)
            override fun visit(stmt: ArkAssignStmt): R = defaultVisit(stmt)
            override fun visit(stmt: ArkCallStmt): R = defaultVisit(stmt)
            override fun visit(stmt: ArkDeleteStmt): R = defaultVisit(stmt)
            override fun visit(stmt: ArkReturnStmt): R = defaultVisit(stmt)
            override fun visit(stmt: ArkThrowStmt): R = defaultVisit(stmt)
            override fun visit(stmt: ArkGotoStmt): R = defaultVisit(stmt)
            override fun visit(stmt: ArkIfStmt): R = defaultVisit(stmt)
            override fun visit(stmt: ArkSwitchStmt): R = defaultVisit(stmt)

            fun defaultVisit(stmt: ArkStmt): R
        }
    }

    fun <R> accept(visitor: Visitor<R>): R
}

data class ArkNopStmt(
    override val location: ArkInstLocation,
) : ArkStmt {
    override fun toString(): String = "nop"

    override fun <R> accept(visitor: ArkStmt.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class ArkAssignStmt(
    override val location: ArkInstLocation,
    override val lhv: ArkLValue,
    override val rhv: ArkEntity,
) : ArkStmt, CommonAssignInst {
    override fun toString(): String {
        return "$lhv := $rhv"
    }

    override fun <R> accept(visitor: ArkStmt.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class ArkCallStmt(
    override val location: ArkInstLocation,
    val expr: ArkCallExpr,
) : ArkStmt {
    override fun toString(): String {
        return expr.toString()
    }

    override fun <R> accept(visitor: ArkStmt.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class ArkDeleteStmt(
    override val location: ArkInstLocation,
    val arg: ArkFieldRef,
) : ArkStmt {
    override fun toString(): String {
        return "delete $arg"
    }

    override fun <R> accept(visitor: ArkStmt.Visitor<R>): R {
        return visitor.visit(this)
    }
}

interface ArkTerminatingStmt : ArkStmt

data class ArkReturnStmt(
    override val location: ArkInstLocation,
    override val returnValue: ArkValue?,
) : ArkTerminatingStmt, CommonReturnInst {
    override fun toString(): String {
        return if (returnValue != null) {
            "return $returnValue"
        } else {
            "return"
        }
    }

    override fun <R> accept(visitor: ArkStmt.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class ArkThrowStmt(
    override val location: ArkInstLocation,
    val arg: ArkEntity,
) : ArkTerminatingStmt {
    override fun toString(): String {
        return "throw $arg"
    }

    override fun <R> accept(visitor: ArkStmt.Visitor<R>): R {
        return visitor.visit(this)
    }
}

interface ArkBranchingStmt : ArkStmt

class ArkGotoStmt(
    override val location: ArkInstLocation,
) : ArkBranchingStmt {
    override fun toString(): String = "goto"

    override fun <R> accept(visitor: ArkStmt.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class ArkIfStmt(
    override val location: ArkInstLocation,
    val condition: ArkConditionExpr,
) : ArkBranchingStmt {
    override fun toString(): String {
        return "if ($condition)"
    }

    override fun <R> accept(visitor: ArkStmt.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class ArkSwitchStmt(
    override val location: ArkInstLocation,
    val arg: ArkEntity,
    val cases: List<ArkEntity>,
) : ArkBranchingStmt {
    override fun toString(): String {
        return "switch ($arg)"
    }

    override fun <R> accept(visitor: ArkStmt.Visitor<R>): R {
        return visitor.visit(this)
    }
}
