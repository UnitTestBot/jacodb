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

package org.jacodb.ets.base

import org.jacodb.api.common.cfg.CommonAssignInst
import org.jacodb.api.common.cfg.CommonInst
import org.jacodb.api.common.cfg.CommonInstLocation
import org.jacodb.api.common.cfg.CommonReturnInst
import org.jacodb.ets.model.EtsMethod

data class EtsInstLocation(
    override val method: EtsMethod,
    val index: Int,
) : CommonInstLocation

interface EtsStmt : CommonInst {
    override val location: EtsInstLocation

    override val method: EtsMethod
        get() = location.method

    interface Visitor<out R> {
        fun visit(stmt: EtsNopStmt): R
        fun visit(stmt: EtsAssignStmt): R
        fun visit(stmt: EtsCallStmt): R
        fun visit(stmt: EtsReturnStmt): R
        fun visit(stmt: EtsThrowStmt): R
        fun visit(stmt: EtsGotoStmt): R
        fun visit(stmt: EtsIfStmt): R
        fun visit(stmt: EtsSwitchStmt): R

        interface Default<out R> : Visitor<R> {
            override fun visit(stmt: EtsNopStmt): R = defaultVisit(stmt)
            override fun visit(stmt: EtsAssignStmt): R = defaultVisit(stmt)
            override fun visit(stmt: EtsCallStmt): R = defaultVisit(stmt)
            override fun visit(stmt: EtsReturnStmt): R = defaultVisit(stmt)
            override fun visit(stmt: EtsThrowStmt): R = defaultVisit(stmt)
            override fun visit(stmt: EtsGotoStmt): R = defaultVisit(stmt)
            override fun visit(stmt: EtsIfStmt): R = defaultVisit(stmt)
            override fun visit(stmt: EtsSwitchStmt): R = defaultVisit(stmt)

            fun defaultVisit(stmt: EtsStmt): R
        }
    }

    fun <R> accept(visitor: Visitor<R>): R
}

data class EtsNopStmt(
    override val location: EtsInstLocation,
) : EtsStmt {
    override fun toString(): String = "nop"

    override fun <R> accept(visitor: EtsStmt.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class EtsAssignStmt(
    override val location: EtsInstLocation,
    override val lhv: EtsValue,
    override val rhv: EtsEntity,
) : EtsStmt, CommonAssignInst {
    override fun toString(): String {
        return "$lhv := $rhv"
    }

    override fun <R> accept(visitor: EtsStmt.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class EtsCallStmt(
    override val location: EtsInstLocation,
    val expr: EtsCallExpr,
) : EtsStmt {
    override fun toString(): String {
        return expr.toString()
    }

    override fun <R> accept(visitor: EtsStmt.Visitor<R>): R {
        return visitor.visit(this)
    }
}

interface EtsTerminatingStmt : EtsStmt

data class EtsReturnStmt(
    override val location: EtsInstLocation,
    override val returnValue: EtsValue?,
) : EtsTerminatingStmt, CommonReturnInst {
    override fun toString(): String {
        return if (returnValue != null) {
            "return $returnValue"
        } else {
            "return"
        }
    }

    override fun <R> accept(visitor: EtsStmt.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class EtsThrowStmt(
    override val location: EtsInstLocation,
    val arg: EtsEntity,
) : EtsTerminatingStmt {
    override fun toString(): String {
        return "throw $arg"
    }

    override fun <R> accept(visitor: EtsStmt.Visitor<R>): R {
        return visitor.visit(this)
    }
}

interface EtsBranchingStmt : EtsStmt

class EtsGotoStmt(
    override val location: EtsInstLocation,
) : EtsBranchingStmt {
    override fun toString(): String = "goto"

    override fun <R> accept(visitor: EtsStmt.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class EtsIfStmt(
    override val location: EtsInstLocation,
    val condition: EtsEntity,
) : EtsBranchingStmt {
    override fun toString(): String {
        return "if ($condition)"
    }

    override fun <R> accept(visitor: EtsStmt.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class EtsSwitchStmt(
    override val location: EtsInstLocation,
    val arg: EtsEntity,
    val cases: List<EtsEntity>,
) : EtsBranchingStmt {
    override fun toString(): String {
        return "switch ($arg)"
    }

    override fun <R> accept(visitor: EtsStmt.Visitor<R>): R {
        return visitor.visit(this)
    }
}
