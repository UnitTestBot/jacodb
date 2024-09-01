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

package org.jacodb.go.api

import org.jacodb.api.common.cfg.CommonAssignInst
import org.jacodb.api.common.cfg.CommonCallInst
import org.jacodb.api.common.cfg.CommonGotoInst
import org.jacodb.api.common.cfg.CommonIfInst
import org.jacodb.api.common.cfg.CommonInst
import org.jacodb.api.common.cfg.CommonReturnInst

interface GoInst : CommonInst {
    override val location: GoInstLocation

    fun <T> accept(visitor: GoInstVisitor<T>): T
}

abstract class AbstractGoInst(override val location: GoInstLocation) : GoInst {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AbstractGoInst

        return location == other.location
    }

    override fun hashCode(): Int {
        return location.hashCode()
    }
}

data class GoInstRef(
    val index: Int
)

interface GoAssignableInst {
    val name: String
    val location: GoInstLocation
    fun toAssignInst(): GoAssignInst
}

class GoJumpInst(
    location: GoInstLocation,
    val target: GoInstRef
) : AbstractGoInst(location), GoBranchingInst, CommonGotoInst {
    override val successors: List<GoInstRef>
        get() = listOf(target)

    override fun toString(): String = "jump $target"

    override fun <T> accept(visitor: GoInstVisitor<T>): T {
        return visitor.visitGoJumpInst(this)
    }
}

class GoRunDefersInst(
    location: GoInstLocation,
) : AbstractGoInst(location) {
    override fun toString(): String = "run defers"

    override fun <T> accept(visitor: GoInstVisitor<T>): T {
        return visitor.visitGoRunDefersInst(this)
    }
}

class GoSendInst(
    location: GoInstLocation,
    private val chan: GoValue,
    val message: GoExpr
) : AbstractGoInst(location) {
    override fun toString(): String = "$chan <- $message"

    override fun <T> accept(visitor: GoInstVisitor<T>): T {
        return visitor.visitGoSendInst(this)
    }
}

class GoStoreInst(
    location: GoInstLocation,
    val lhv: GoValue,
    val rhv: GoExpr
) : AbstractGoInst(location) {
    override fun toString(): String = "*$lhv = $rhv"

    override fun <T> accept(visitor: GoInstVisitor<T>): T {
        return visitor.visitGoStoreInst(this)
    }
}

class GoCallInst(
    location: GoInstLocation,
    val callExpr: GoCallExpr,
    override val name: String,
    val type: GoType
) : AbstractGoInst(location), GoAssignableInst, CommonCallInst {
    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoVar(name, type),
            rhv = callExpr
        )
    }

    override fun toString(): String = "$callExpr"

    override fun <T> accept(visitor: GoInstVisitor<T>): T {
        return visitor.visitGoCallInst(this)
    }
}

interface GoTerminatingInst : GoInst

class GoReturnInst(
    location: GoInstLocation,
    val returnValues: List<GoValue>
) : AbstractGoInst(location), GoTerminatingInst, CommonReturnInst {
    override val returnValue: GoValue? =
        if (returnValues.isEmpty()) {
            null
        } else {
            GoVar(this.toString(), TupleType(returnValues.map { it.typeName }))
        }

    override fun toString(): String = "return" + (returnValues.let { " $it" })

    override fun <T> accept(visitor: GoInstVisitor<T>): T {
        return visitor.visitGoReturnInst(this)
    }
}

class GoPanicInst(
    location: GoInstLocation,
    val throwable: GoValue
) : AbstractGoInst(location), GoTerminatingInst {
    override fun toString(): String = "throw $throwable"

    override fun <T> accept(visitor: GoInstVisitor<T>): T {
        return visitor.visitGoPanicInst(this)
    }
}

class GoGoInst(
    location: GoInstLocation,
    val func: GoValue,
    val args: List<GoValue>
) : AbstractGoInst(location) {
    override fun toString(): String = "go $func"

    override fun <T> accept(visitor: GoInstVisitor<T>): T {
        return visitor.visitGoGoInst(this)
    }
}

class GoDeferInst(
    location: GoInstLocation,
    val func: GoValue,
    val args: List<GoValue>
) : AbstractGoInst(location) {
    override fun toString(): String = "defer $func"

    override fun <T> accept(visitor: GoInstVisitor<T>): T {
        return visitor.visitGoDeferInst(this)
    }
}

interface GoBranchingInst : GoInst {
    val successors: List<GoInstRef>
}

class GoIfInst(
    location: GoInstLocation,
    val condition: GoConditionExpr,
    val trueBranch: GoInstRef,
    val falseBranch: GoInstRef
) : AbstractGoInst(location), GoBranchingInst, CommonIfInst {
    override val successors: List<GoInstRef>
        get() = listOf(trueBranch, falseBranch)

    override fun toString(): String = "if ($condition) then $trueBranch else $falseBranch"

    override fun <T> accept(visitor: GoInstVisitor<T>): T {
        return visitor.visitGoIfInst(this)
    }
}

class GoMapUpdateInst(
    location: GoInstLocation,
    val map: GoValue,
    val key: GoExpr,
    val value: GoExpr
) : AbstractGoInst(location) {
    override fun toString(): String = "$map[$key] = $value"

    override fun <T> accept(visitor: GoInstVisitor<T>): T {
        return visitor.visitGoMapUpdateInst(this)
    }
}

class GoDebugRefInst(
    location: GoInstLocation,
) : AbstractGoInst(location) {
    override fun toString(): String = "debug ref"

    override fun <T> accept(visitor: GoInstVisitor<T>): T {
        return visitor.visitGoDebugRefInst(this)
    }
}

class GoAssignInst(
    override val location: GoInstLocation,
    override val lhv: GoValue,
    override val rhv: GoExpr,
) : AbstractGoInst(location), CommonAssignInst {
    override fun toString(): String = "$lhv := $rhv"

    override fun <T> accept(visitor: GoInstVisitor<T>): T {
        return visitor.visitGoAssignInst(this)
    }
}

class GoNullInst(val parent: GoMethod) : AbstractGoInst(GoInstLocationImpl(parent,-1, -1)) {
    override fun toString(): String = "null"

    override fun <T> accept(visitor: GoInstVisitor<T>): T {
        error("NULL inst")
    }
}
