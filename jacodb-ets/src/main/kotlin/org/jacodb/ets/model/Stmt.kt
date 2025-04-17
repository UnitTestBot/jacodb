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

package org.jacodb.ets.model

import org.jacodb.api.common.cfg.CommonAssignInst
import org.jacodb.api.common.cfg.CommonCallInst
import org.jacodb.api.common.cfg.CommonIfInst
import org.jacodb.api.common.cfg.CommonInst
import org.jacodb.api.common.cfg.CommonReturnInst

interface EtsStmt : CommonInst {
    override val location: EtsStmtLocation

    override val method: EtsMethod
        get() = location.method

    interface Visitor<out R> {
        fun visit(stmt: EtsNopStmt): R
        fun visit(stmt: EtsAssignStmt): R
        fun visit(stmt: EtsReturnStmt): R
        fun visit(stmt: EtsThrowStmt): R
        fun visit(stmt: EtsIfStmt): R
        fun visit(stmt: EtsCallStmt): R

        fun visit(stmt: EtsRawStmt): R {
            if (this is Default) {
                return defaultVisit(stmt)
            }
            error("Cannot handle ${stmt::class.java.simpleName}: $stmt")
        }

        interface Default<out R> : Visitor<R> {
            override fun visit(stmt: EtsNopStmt): R = defaultVisit(stmt)
            override fun visit(stmt: EtsAssignStmt): R = defaultVisit(stmt)
            override fun visit(stmt: EtsReturnStmt): R = defaultVisit(stmt)
            override fun visit(stmt: EtsThrowStmt): R = defaultVisit(stmt)
            override fun visit(stmt: EtsIfStmt): R = defaultVisit(stmt)
            override fun visit(stmt: EtsCallStmt): R = defaultVisit(stmt)
            override fun visit(stmt: EtsRawStmt): R = defaultVisit(stmt)

            fun defaultVisit(stmt: EtsStmt): R
        }
    }

    fun <R> accept(visitor: Visitor<R>): R
}

data class EtsRawStmt(
    override val location: EtsStmtLocation,
    val kind: String,
    val extra: Map<String, Any> = emptyMap(),
) : EtsStmt {
    override fun toString(): String {
        return "$kind $extra"
    }

    override fun <R> accept(visitor: EtsStmt.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class EtsNopStmt(
    override val location: EtsStmtLocation,
) : EtsStmt {
    override fun toString(): String = "nop"

    override fun <R> accept(visitor: EtsStmt.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class EtsAssignStmt(
    override val location: EtsStmtLocation,
    override val lhv: EtsLValue,
    override val rhv: EtsEntity,
) : EtsStmt, CommonAssignInst {
    override fun toString(): String {
        return "$lhv := $rhv"
    }

    override fun <R> accept(visitor: EtsStmt.Visitor<R>): R {
        return visitor.visit(this)
    }
}

interface EtsTerminatingStmt : EtsStmt

data class EtsReturnStmt(
    override val location: EtsStmtLocation,
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
    override val location: EtsStmtLocation,
    val exception: EtsLocal,
) : EtsTerminatingStmt {
    override fun toString(): String {
        return "throw $exception"
    }

    override fun <R> accept(visitor: EtsStmt.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class EtsIfStmt(
    override val location: EtsStmtLocation,
    val condition: EtsLocal,
) : EtsStmt, CommonIfInst {
    override fun toString(): String {
        return "if ($condition)"
    }

    override fun <R> accept(visitor: EtsStmt.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class EtsCallStmt(
    override val location: EtsStmtLocation,
    val expr: EtsCallExpr,
) : EtsStmt, CommonCallInst {
    override fun toString(): String {
        return expr.toString()
    }

    override fun <R> accept(visitor: EtsStmt.Visitor<R>): R {
        return visitor.visit(this)
    }
}
