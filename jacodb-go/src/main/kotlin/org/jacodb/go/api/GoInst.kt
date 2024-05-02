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

import org.jacodb.api.common.CommonClass
import org.jacodb.api.common.CommonMethodParameter
import org.jacodb.api.common.CommonTypeName
import org.jacodb.api.common.cfg.*

interface GoInst : CommonInst<GoMethod, GoInst> {
    override val location: GoInstLocation
    override val operands: List<GoExpr>

    override val lineNumber: Int get() = location.lineNumber

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
) : AbstractGoInst(location), GoBranchingInst, CommonGotoInst<GoMethod, GoInst> {
    override val operands: List<GoExpr>
        get() = emptyList()

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
    override val operands: List<GoExpr>
        get() = emptyList()

    override fun toString(): String = "run defers"

    override fun <T> accept(visitor: GoInstVisitor<T>): T {
        return visitor.visitGoRunDefersInst(this)
    }
}

class GoSendInst(
    location: GoInstLocation,
    val chan: GoValue,
    val message: GoExpr
) : AbstractGoInst(location) {
    override val operands: List<GoExpr>
        get() = listOf(chan, message)

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
    override val operands: List<GoExpr>
        get() = listOf(lhv, rhv)

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
) : AbstractGoInst(location), GoAssignableInst, CommonCallInst<GoMethod, GoInst> {
    override val operands: List<GoExpr>
        get() = listOf(callExpr)

    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoFreeVar(
                index = location.index,
                name = name,
                type = type
            ),
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
    val retValue: List<GoValue>
) : AbstractGoInst(location), GoTerminatingInst, CommonReturnInst<GoMethod, GoInst> {
    override val returnValue: GoValue? =
        if (retValue.isEmpty()) {
            null
        } else {
            GoFreeVar(
                location.index,
                this.toString(),
                TupleType(
                    names = retValue.map { it.typeName }
                )
            )
        }

    override val operands: List<GoExpr>
        get() = retValue

    override fun toString(): String = "return" + (retValue.let { " $it" })

    override fun <T> accept(visitor: GoInstVisitor<T>): T {
        return visitor.visitGoReturnInst(this)
    }
}

class GoPanicInst(
    location: GoInstLocation,
    val throwable: GoValue
) : AbstractGoInst(location), GoTerminatingInst {
    override val operands: List<GoExpr>
        get() = listOf(throwable)

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
    override val operands: List<GoExpr>
        get() = args

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
    override val operands: List<GoExpr>
        get() = args

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
) : AbstractGoInst(location), GoBranchingInst, CommonIfInst<GoMethod, GoInst>{
    override val operands: List<GoExpr>
        get() = listOf(condition)

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
    override val operands: List<GoExpr>
        get() = listOf(map, key, value)

    override fun toString(): String = "$map[$key] = $value"

    override fun <T> accept(visitor: GoInstVisitor<T>): T {
        return visitor.visitGoMapUpdateInst(this)
    }
}

class GoDebugRefInst(
    location: GoInstLocation,
) : AbstractGoInst(location) {
    override val operands: List<GoExpr>
        get() = emptyList()

    override fun toString(): String = "debug ref"

    override fun <T> accept(visitor: GoInstVisitor<T>): T {
        return visitor.visitGoDebugRefInst(this)
    }
}

class GoNullInst(val parent: GoMethod) : AbstractGoInst(
    GoInstLocationImpl(
    -1, -1, parent
)) {
    override val operands: List<GoExpr>
        get() = emptyList()

    override fun toString(): String = "null"

    override fun <T> accept(visitor: GoInstVisitor<T>): T {
        return visitor.visitGoNullInst(this)
    }
}

class GoAssignInst(
    override val location: GoInstLocation,
    override val lhv: GoValue,
    override val rhv: GoExpr,
) : AbstractGoInst(location), CommonAssignInst<GoMethod, GoInst> {
    override val operands: List<GoExpr>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv := $rhv"

    override fun <T> accept(visitor: GoInstVisitor<T>): T {
        return visitor.visitGoAssignInst(this)
    }

}

interface GoExpr : CommonExpr {
    val type: GoType
    override val operands: List<GoValue>

    fun <T> accept(visitor: GoExprVisitor<T>): T // TODO visitor for CoreExpr?
}

interface GoBinaryExpr : GoExpr, GoValue, GoAssignableInst {
    val lhv: GoValue
    val rhv: GoValue
}

interface GoConditionExpr : GoBinaryExpr

data class GoAllocExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    override val name: String,
) : GoExpr, GoValue, GoAssignableInst {
    override val operands: List<GoValue>
        get() = emptyList()

    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoFreeVar(
                index = location.index,
                name = name,
                type = type
            ),
            rhv = this
        )
    }

    override fun toString(): String = "new ${type.typeName}"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoAllocExpr(this)
    }
}

data class GoAddExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    override val lhv: GoValue,
    override val rhv: GoValue,
    override val name: String,
) : GoBinaryExpr {
    override val operands: List<GoValue>
        get() = listOf(lhv, rhv)

    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoFreeVar(
                index = location.index,
                name = name,
                type = type
            ),
            rhv = this
        )
    }

    override fun toString(): String = "$lhv + $rhv"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoAddExpr(this)
    }
}

data class GoSubExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    override val lhv: GoValue,
    override val rhv: GoValue,
    override val name: String,
) : GoBinaryExpr {
    override val operands: List<GoValue>
        get() = listOf(lhv, rhv)

    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoFreeVar(
                index = location.index,
                name = name,
                type = type
            ),
            rhv = this
        )
    }

    override fun toString(): String = "$lhv - $rhv"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoSubExpr(this)
    }
}

data class GoMulExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    override val lhv: GoValue,
    override val rhv: GoValue,
    override val name: String,
) : GoBinaryExpr {
    override val operands: List<GoValue>
        get() = listOf(lhv, rhv)

    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoFreeVar(
                index = location.index,
                name = name,
                type = type
            ),
            rhv = this
        )
    }

    override fun toString(): String = "$lhv * $rhv"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoMulExpr(this)
    }
}

data class GoDivExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    override val lhv: GoValue,
    override val rhv: GoValue,
    override val name: String,
) : GoBinaryExpr {
    override val operands: List<GoValue>
        get() = listOf(lhv, rhv)

    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoFreeVar(
                index = location.index,
                name = name,
                type = type
            ),
            rhv = this
        )
    }

    override fun toString(): String = "$lhv / $rhv"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoDivExpr(this)
    }
}

data class GoModExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    override val lhv: GoValue,
    override val rhv: GoValue,
    override val name: String,
) : GoBinaryExpr {
    override val operands: List<GoValue>
        get() = listOf(lhv, rhv)

    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoFreeVar(
                index = location.index,
                name = name,
                type = type
            ),
            rhv = this
        )
    }

    override fun toString(): String = "$lhv % $rhv"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoModExpr(this)
    }
}

data class GoAndExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    override val lhv: GoValue,
    override val rhv: GoValue,
    override val name: String,
) : GoBinaryExpr {
    override val operands: List<GoValue>
        get() = listOf(lhv, rhv)

    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoFreeVar(
                index = location.index,
                name = name,
                type = type
            ),
            rhv = this
        )
    }

    override fun toString(): String = "$lhv & $rhv"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoAndExpr(this)
    }
}

data class GoOrExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    override val lhv: GoValue,
    override val rhv: GoValue,
    override val name: String,
) : GoBinaryExpr {
    override val operands: List<GoValue>
        get() = listOf(lhv, rhv)

    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoFreeVar(
                index = location.index,
                name = name,
                type = type
            ),
            rhv = this
        )
    }

    override fun toString(): String = "$lhv | $rhv"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoOrExpr(this)
    }
}

data class GoXorExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    override val lhv: GoValue,
    override val rhv: GoValue,
    override val name: String,
) : GoBinaryExpr {
    override val operands: List<GoValue>
        get() = listOf(lhv, rhv)

    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoFreeVar(
                index = location.index,
                name = name,
                type = type
            ),
            rhv = this
        )
    }

    override fun toString(): String = "$lhv ^ $rhv"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoXorExpr(this)
    }
}

data class GoShlExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    override val lhv: GoValue,
    override val rhv: GoValue,
    override val name: String,
) : GoBinaryExpr {
    override val operands: List<GoValue>
        get() = listOf(lhv, rhv)

    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoFreeVar(
                index = location.index,
                name = name,
                type = type
            ),
            rhv = this
        )
    }

    override fun toString(): String = "$lhv << $rhv"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoShlExpr(this)
    }
}

data class GoShrExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    override val lhv: GoValue,
    override val rhv: GoValue,
    override val name: String,
) : GoBinaryExpr {
    override val operands: List<GoValue>
        get() = listOf(lhv, rhv)

    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoFreeVar(
                index = location.index,
                name = name,
                type = type
            ),
            rhv = this
        )
    }

    override fun toString(): String = "$lhv >> $rhv"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoShrExpr(this)
    }
}

data class GoAndNotExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    override val lhv: GoValue,
    override val rhv: GoValue,
    override val name: String,
) : GoBinaryExpr {
    override val operands: List<GoValue>
        get() = listOf(lhv, rhv)

    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoFreeVar(
                index = location.index,
                name = name,
                type = type
            ),
            rhv = this
        )
    }

    override fun toString(): String = "$lhv &^ $rhv"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoAndNotExpr(this)
    }
}

data class GoEqlExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    override val lhv: GoValue,
    override val rhv: GoValue,
    override val name: String,
) : GoConditionExpr {
    override val operands: List<GoValue>
        get() = listOf(lhv, rhv)

    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoFreeVar(
                index = location.index,
                name = name,
                type = type
            ),
            rhv = this
        )
    }

    override fun toString(): String = "$lhv == $rhv"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoEqlExpr(this)
    }
}

data class GoNeqExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    override val lhv: GoValue,
    override val rhv: GoValue,
    override val name: String,
) : GoConditionExpr {
    override val operands: List<GoValue>
        get() = listOf(lhv, rhv)

    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoFreeVar(
                index = location.index,
                name = name,
                type = type
            ),
            rhv = this
        )
    }

    override fun toString(): String = "$lhv != $rhv"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoNeqExpr(this)
    }
}

data class GoLssExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    override val lhv: GoValue,
    override val rhv: GoValue,
    override val name: String,
) : GoConditionExpr {
    override val operands: List<GoValue>
        get() = listOf(lhv, rhv)

    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoFreeVar(
                index = location.index,
                name = name,
                type = type
            ),
            rhv = this
        )
    }

    override fun toString(): String = "$lhv < $rhv"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoLssExpr(this)
    }
}

data class GoLeqExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    override val lhv: GoValue,
    override val rhv: GoValue,
    override val name: String,
) : GoConditionExpr {
    override val operands: List<GoValue>
        get() = listOf(lhv, rhv)

    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoFreeVar(
                index = location.index,
                name = name,
                type = type
            ),
            rhv = this
        )
    }

    override fun toString(): String = "$lhv <= $rhv"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoLeqExpr(this)
    }
}

data class GoGtrExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    override val lhv: GoValue,
    override val rhv: GoValue,
    override val name: String,
) : GoConditionExpr {
    override val operands: List<GoValue>
        get() = listOf(lhv, rhv)

    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoFreeVar(
                index = location.index,
                name = name,
                type = type
            ),
            rhv = this
        )
    }

    override fun toString(): String = "$lhv > $rhv"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoGtrExpr(this)
    }
}

data class GoGeqExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    override val lhv: GoValue,
    override val rhv: GoValue,
    override val name: String,
) : GoConditionExpr {
    override val operands: List<GoValue>
        get() = listOf(lhv, rhv)

    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoFreeVar(
                index = location.index,
                name = name,
                type = type
            ),
            rhv = this
        )
    }

    override fun toString(): String = "$lhv >= $rhv"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoGeqExpr(this)
    }
}

interface GoUnaryExpr : GoExpr, GoValue, GoAssignableInst {
    val value: GoValue
}

data class GoUnNotExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    override val value: GoValue,
    override val name: String,
) : GoUnaryExpr {
    override val operands: List<GoValue>
        get() = listOf(value)

    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoFreeVar(
                index = location.index,
                name = name,
                type = type
            ),
            rhv = this
        )
    }

    override fun toString(): String = "!$value"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoUnNotExpr(this)
    }
}

data class GoUnSubExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    override val value: GoValue,
    override val name: String,
) : GoUnaryExpr {
    override val operands: List<GoValue>
        get() = listOf(value)

    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoFreeVar(
                index = location.index,
                name = name,
                type = type
            ),
            rhv = this
        )
    }

    override fun toString(): String = "-$value"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoUnSubExpr(this)
    }
}

data class GoUnArrowExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    override val value: GoValue,
    val commaOk: Boolean,
    override val name: String,
) : GoUnaryExpr {
    override val operands: List<GoValue>
        get() = listOf(value)

    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoFreeVar(
                index = location.index,
                name = name,
                type = type
            ),
            rhv = this
        )
    }

    override fun toString(): String = "<-$value${if (commaOk) ",ok" else ""}"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoUnArrowExpr(this)
    }
}

data class GoUnMulExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    override val value: GoValue,
    override val name: String,
) : GoUnaryExpr {
    override val operands: List<GoValue>
        get() = listOf(value)

    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoFreeVar(
                index = location.index,
                name = name,
                type = type
            ),
            rhv = this
        )
    }

    override fun toString(): String = "*$value"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoUnMulExpr(this)
    }
}

data class GoUnXorExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    override val value: GoValue,
    override val name: String,
) : GoUnaryExpr {
    override val operands: List<GoValue>
        get() = listOf(value)

    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoFreeVar(
                index = location.index,
                name = name,
                type = type
            ),
            rhv = this
        )
    }

    override fun toString(): String = "^$value"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoUnXorExpr(this)
    }
}

data class GoCallExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    val value: GoValue,
    override val args: List<GoValue>,
    val callee: GoMethod?,
    override val name: String,
) : GoExpr, GoValue, CommonCallExpr, GoAssignableInst {
    override val operands: List<GoValue>
        get() = listOf(value) + args

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoCallExpr(this)
    }

    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoFreeVar(
                index = location.index,
                name = name,
                type = type
            ),
            rhv = this
        )
    }
}

data class GoPhiExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    var edges: List<GoValue>,
    override val name: String,
) : GoExpr, GoValue, GoAssignableInst {

    override val operands: List<GoValue>
        get() = edges

    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoFreeVar(
                index = location.index,
                name = name,
                type = type
            ),
            rhv = this
        )
    }

    override fun toString(): String = "phi [$edges]"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoPhiExpr(this)
    }
}

data class GoChangeTypeExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    val operand: GoValue,
    override val name: String,
) : GoExpr, GoValue, GoAssignableInst {
    override val operands: List<GoValue>
        get() = listOf(operand)

    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoFreeVar(
                index = location.index,
                name = name,
                type = type
            ),
            rhv = this
        )
    }

    override fun toString(): String = "${type.typeName} -> $operand"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoChangeTypeExpr(this)
    }
}

data class GoConvertExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    val operand: GoValue,
    override val name: String,
) : GoExpr, GoValue, GoAssignableInst {
    override val operands: List<GoValue>
        get() = listOf(operand)

    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoFreeVar(
                index = location.index,
                name = name,
                type = type
            ),
            rhv = this
        )
    }

    override fun toString(): String = "(${type.typeName}) $operand"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoConvertExpr(this)
    }
}

data class GoMultiConvertExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    val operand: GoValue,
    override val name: String,
) : GoExpr, GoValue, GoAssignableInst {
    override val operands: List<GoValue>
        get() = listOf(operand)

    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoFreeVar(
                index = location.index,
                name = name,
                type = type
            ),
            rhv = this
        )
    }

    override fun toString(): String = "multi (${type.typeName}) $operand"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoMultiConvertExpr(this)
    }
}

data class GoChangeInterfaceExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    val operand: GoValue,
    override val name: String,
) : GoExpr, GoValue, GoAssignableInst {
    override val operands: List<GoValue>
        get() = listOf(operand)

    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoFreeVar(
                index = location.index,
                name = name,
                type = type
            ),
            rhv = this
        )
    }

    override fun toString(): String = "change interface (${type.typeName}) $operand"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoChangeInterfaceExpr(this)
    }
}

data class GoSliceToArrayPointerExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    val operand: GoValue,
    override val name: String,
) : GoExpr, GoValue, GoAssignableInst {
    override val operands: List<GoValue>
        get() = listOf(operand)

    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoFreeVar(
                index = location.index,
                name = name,
                type = type
            ),
            rhv = this
        )
    }

    override fun toString(): String = "slice to array pointer (${type.typeName}) $operand"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoSliceToArrayPointerExpr(this)
    }
}

data class GoMakeInterfaceExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    val value: GoValue,
    override val name: String,
) : GoExpr, GoValue, GoAssignableInst {

    override val operands: List<GoValue>
        get() = listOf(value)

    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoFreeVar(
                index = location.index,
                name = name,
                type = type
            ),
            rhv = this
        )
    }

    override fun toString(): String = "interface{} <- ${type.typeName} ($value)"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoMakeInterfaceExpr(this)
    }
}

data class GoMakeClosureExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    val func: GoMethod,
    val bindings: List<GoValue>,
    override val name: String,
) : GoExpr, GoValue, GoAssignableInst {

    override val operands: List<GoValue>
        get() = listOf(func) + bindings

    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoFreeVar(
                index = location.index,
                name = name,
                type = type
            ),
            rhv = this
        )
    }

    override fun toString(): String = "make closure $func [$bindings]"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoMakeClosureExpr(this)
    }
}

data class GoMakeSliceExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    val len: GoValue,
    val cap: GoValue,
    override val name: String,
) : GoExpr, GoValue, GoAssignableInst {

    override val operands: List<GoValue>
        get() = listOf(len, cap)

    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoFreeVar(
                index = location.index,
                name = name,
                type = type
            ),
            rhv = this
        )
    }

    override fun toString(): String = "new []${type.typeName}($len, $cap)"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoMakeSliceExpr(this)
    }
}

data class GoSliceExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    val array: GoValue,
    val low: GoValue,
    val high: GoValue,
    val max: GoValue,
    override val name: String,
) : GoExpr, GoValue, GoAssignableInst {

    override val operands: List<GoValue>
        get() = listOf(array, low, high, max)

    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoFreeVar(
                index = location.index,
                name = name,
                type = type
            ),
            rhv = this
        )
    }

    override fun toString(): String = "slice $array[$low:$high]:$max"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoSliceExpr(this)
    }
}

data class GoMakeMapExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    val reserve: GoValue,
    override val name: String,
) : GoExpr, GoValue, GoAssignableInst {

    override val operands: List<GoValue>
        get() = listOf(reserve)

    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoFreeVar(
                index = location.index,
                name = name,
                type = type
            ),
            rhv = this
        )
    }

    override fun toString(): String = "make map ${type.typeName} ($reserve)"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoMakeMapExpr(this)
    }
}

data class GoMakeChanExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    val size: GoValue,
    override val name: String,
) : GoExpr, GoValue, GoAssignableInst {

    override val operands: List<GoValue>
        get() = listOf(size)

    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoFreeVar(
                index = location.index,
                name = name,
                type = type
            ),
            rhv = this
        )
    }

    override fun toString(): String = "make chan ${type.typeName} ($size)"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoMakeChanExpr(this)
    }
}

data class GoFieldAddrExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    val instance: GoValue,
    val field: Int,
    override val name: String,
) : GoValue, GoAssignableInst {
    override val operands: List<GoValue>
        get() = listOf(instance)

    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoFreeVar(
                index = location.index,
                name = name,
                type = type
            ),
            rhv = this
        )
    }

    override fun toString(): String = "addr $instance.[${field}]"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoFieldAddrExpr(this)
    }
}

data class GoFieldExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    val instance: GoValue,
    val field: Int,
    override val name: String,
) : GoValue, GoAssignableInst {
    override val operands: List<GoValue>
        get() = listOf(instance)

    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoFreeVar(
                index = location.index,
                name = name,
                type = type
            ),
            rhv = this
        )
    }

    override fun toString(): String = "$instance.[${field}]"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoFieldExpr(this)
    }
}

data class GoIndexAddrExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    val instance: GoValue,
    val index: GoValue,
    override val name: String,
) : GoValue, GoAssignableInst {
    override val operands: List<GoValue>
        get() = listOf(instance, index)

    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoFreeVar(
                index = location.index,
                name = name,
                type = type
            ),
            rhv = this
        )
    }

    override fun toString(): String = "addr ${instance}[${index}]"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoIndexAddrExpr(this)
    }
}

data class GoIndexExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    val instance: GoValue,
    val index: GoValue,
    override val name: String,
) : GoValue, GoAssignableInst {
    override val operands: List<GoValue>
        get() = listOf(instance, index)

    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoFreeVar(
                index = location.index,
                name = name,
                type = type
            ),
            rhv = this
        )
    }

    override fun toString(): String = "${instance}[${index}]"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoIndexExpr(this)
    }
}

data class GoLookupExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    val instance: GoValue,
    val index: GoValue,
    override val name: String,
) : GoValue, GoAssignableInst {
    override val operands: List<GoValue>
        get() = listOf(instance, index)

    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoFreeVar(
                index = location.index,
                name = name,
                type = type
            ),
            rhv = this
        )
    }

    override fun toString(): String = "lookup ${instance}[${index}]"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoLookupExpr(this)
    }
}

data class GoSelectExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    val chans: List<GoValue>,
    val sends: List<GoValue?>,
    val blocking: Boolean,
    override val name: String,
) : GoValue, GoAssignableInst {
    override val operands: List<GoValue>
        get(): List<GoValue> {
            val res = mutableListOf<GoValue>()
            for (i in 0..chans.size) {
                res.add(chans[i])
                sends[i]?.let { res.add(it) }
            }
            return res
        }

    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoFreeVar(
                index = location.index,
                name = name,
                type = type
            ),
            rhv = this
        )
    }

    override fun toString(): String = "select ${if (blocking) "blocking" else "nonblocking"} [$chans, $sends]"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoSelectExpr(this)
    }
}

data class GoRangeExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    val instance: GoValue,
    override val name: String,
) : GoValue, GoAssignableInst {
    override val operands: List<GoValue>
        get() = listOf(instance)

    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoFreeVar(
                index = location.index,
                name = name,
                type = type
            ),
            rhv = this
        )
    }

    override fun toString(): String = "range ${instance}:${type.typeName}"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoRangeExpr(this)
    }
}

data class GoNextExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    val instance: GoValue,
    override val name: String,
) : GoValue, GoAssignableInst {
    override val operands: List<GoValue>
        get() = listOf(instance)

    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoFreeVar(
                index = location.index,
                name = name,
                type = type
            ),
            rhv = this
        )
    }

    override fun toString(): String = "next $instance"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoNextExpr(this)
    }
}

data class GoTypeAssertExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    val instance: GoValue,
    val assertType: GoType,
    override val name: String,
) : GoValue, GoAssignableInst {
    override val operands: List<GoValue>
        get() = listOf(instance)

    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoFreeVar(
                index = location.index,
                name = name,
                type = type
            ),
            rhv = this
        )
    }

    override fun toString(): String = "typeassert $instance.($assertType)"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoTypeAssertExpr(this)
    }
}

data class GoExtractExpr(
    override val location: GoInstLocation,
    override val type: GoType,
    val instance: GoValue,
    val index: Int,
    override val name: String,
) : GoValue, GoAssignableInst {
    override val operands: List<GoValue>
        get() = listOf(instance)

    override fun toAssignInst(): GoAssignInst {
        return GoAssignInst(
            location = location,
            lhv = GoFreeVar(
                index = location.index,
                name = name,
                type = type
            ),
            rhv = this
        )
    }

    override fun toString(): String = "extract $instance [$index]"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoExtractExpr(this)
    }
}

interface GoSimpleValue : GoValue {

    override val operands: List<GoValue>
        get() = emptyList()

}

interface GoLocal : GoSimpleValue {
    val name: String
}

data class GoFreeVar(val index: Int, override val name: String, override val type: GoType) : GoLocal {
    override fun toString(): String = name

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoFreeVar(this)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GoFreeVar

        if (index != other.index) return false
        if (type != other.type) return false

        return true
    }

    override fun hashCode(): Int {
        var result = index
        result = 31 * result + type.hashCode()
        return result
    }
}

data class GoConst(val index: Int, override val name: String, override val type: GoType) : GoLocal {
    override fun toString(): String = "const $name"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoConst(this)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GoConst

        if (index != other.index) return false
        if (type != other.type) return false

        return true
    }

    override fun hashCode(): Int {
        var result = index
        result = 31 * result + type.hashCode()
        return result
    }
}

data class GoGlobal(val index: Int, override val name: String, override val type: GoType) : GoLocal {
    override fun toString(): String = "global $name"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoGlobal(this)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GoGlobal

        if (index != other.index) return false
        if (type != other.type) return false

        return true
    }

    override fun hashCode(): Int {
        var result = index
        result = 31 * result + type.hashCode()
        return result
    }
}

data class GoBuiltin(val index: Int, override val name: String, override val type: GoType) : GoLocal {
    override fun toString(): String = "builtin $name"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoBuiltin(this)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GoBuiltin

        if (index != other.index) return false
        if (type != other.type) return false

        return true
    }

    override fun hashCode(): Int {
        var result = index
        result = 31 * result + type.hashCode()
        return result
    }
}

data class GoFunction(
    override val type: GoType,
    override var operands: List<GoValue>,
    override val metName: String,
    override var blocks: List<GoBasicBlock>,
    val returnTypes: List<GoType>,
    val packageName: String
) : GoMethod {

    private var flowGraph: GoGraph? = null

    override fun toString(): String = "${packageName}::${metName}${
        operands.joinToString(
            prefix = "(",
            postfix = ")",
            separator = ", "
        )
    }:${returnTypes.joinToString( 
        prefix = "(",
        postfix = ")",
        separator = ", "
    )}"

    override fun flowGraph(): GoGraph {
        if (flowGraph == null) {
            flowGraph = GoBlockGraph(
                blocks,
                blocks.flatMap { it.insts }
            ).graph
        }
        return flowGraph!!
    }

    override fun hashCode(): Int {
        return packageName.hashCode() * 31 + metName.hashCode()
    }

    override val enclosingClass: CommonClass
        get() = TODO("Not yet implemented")
    override val name: String
        get() = metName
    override val parameters: List<CommonMethodParameter>
        get() = TODO("Not yet implemented")
    override val returnType: CommonTypeName
        get() = TODO("Not yet implemented")

    override fun equals(other: Any?): Boolean {
        return super.equals(other)
    }

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoFunction(this)
    }
}

data class GoParameter(val index: Int, override val name: String, override val type: GoType) : GoLocal {
    companion object {
        @JvmStatic
        fun of(index: Int, name: String?, type: GoType): GoParameter {
            return GoParameter(index, name ?: "arg$$index", type)
        }
    }

    override fun toString(): String = name

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoParameter(this)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GoParameter

        if (index != other.index) return false
        if (type != other.type) return false

        return true
    }

    override fun hashCode(): Int {
        var result = index
        result = 31 * result + type.hashCode()
        return result
    }
}

interface GoConstant : GoSimpleValue

interface GoNumericConstant : GoConstant {

    val value: Number

    fun isEqual(c: GoNumericConstant): Boolean = c.value == value

    fun isNotEqual(c: GoNumericConstant): Boolean = !isEqual(c)

    fun isLessThan(c: GoNumericConstant): Boolean

    fun isLessThanOrEqual(c: GoNumericConstant): Boolean = isLessThan(c) || isEqual(c)

    fun isGreaterThan(c: GoNumericConstant): Boolean

    fun isGreaterThanOrEqual(c: GoNumericConstant): Boolean = isGreaterThan(c) || isEqual(c)

    operator fun plus(c: GoNumericConstant): GoNumericConstant

    operator fun minus(c: GoNumericConstant): GoNumericConstant

    operator fun times(c: GoNumericConstant): GoNumericConstant

    operator fun div(c: GoNumericConstant): GoNumericConstant

    operator fun rem(c: GoNumericConstant): GoNumericConstant

    operator fun unaryMinus(): GoNumericConstant

}


data class GoBool(val value: Boolean, override val type: GoType) : GoConstant {
    override fun toString(): String = "$value"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoBool(this)
    }
}

data class GoByte(override val value: Byte, override val type: GoType) : GoNumericConstant {
    override fun toString(): String = "$value"

    override fun plus(c: GoNumericConstant): GoNumericConstant {
        return GoInt(value + c.value.toByte(), type)
    }

    override fun minus(c: GoNumericConstant): GoNumericConstant {
        return GoInt(value - c.value.toByte(), type)
    }

    override fun times(c: GoNumericConstant): GoNumericConstant {
        return GoInt(value * c.value.toByte(), type)
    }

    override fun div(c: GoNumericConstant): GoNumericConstant {
        return GoInt(value / c.value.toByte(), type)
    }

    override fun rem(c: GoNumericConstant): GoNumericConstant {
        return GoInt(value % c.value.toByte(), type)
    }

    override fun unaryMinus(): GoNumericConstant {
        return GoInt(-value, type)
    }

    override fun isLessThan(c: GoNumericConstant): Boolean {
        return value < c.value.toByte()
    }

    override fun isGreaterThan(c: GoNumericConstant): Boolean {
        return value > c.value.toByte()
    }

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoByte(this)
    }
}

data class GoChar(val value: Char, override val type: GoType) : GoConstant {
    override fun toString(): String = "$value"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoChar(this)
    }
}

data class GoShort(override val value: Short, override val type: GoType) : GoNumericConstant {
    override fun toString(): String = "$value"

    override fun plus(c: GoNumericConstant): GoNumericConstant {
        return GoInt(value + c.value.toShort(), type)
    }

    override fun minus(c: GoNumericConstant): GoNumericConstant {
        return GoInt(value - c.value.toShort(), type)
    }

    override fun times(c: GoNumericConstant): GoNumericConstant {
        return GoInt(value * c.value.toInt(), type)
    }

    override fun div(c: GoNumericConstant): GoNumericConstant {
        return GoInt(value / c.value.toShort(), type)
    }

    override fun rem(c: GoNumericConstant): GoNumericConstant {
        return GoInt(value % c.value.toShort(), type)
    }

    override fun unaryMinus(): GoNumericConstant {
        return GoInt(-value, type)
    }

    override fun isLessThan(c: GoNumericConstant): Boolean {
        return value < c.value.toShort()
    }

    override fun isGreaterThan(c: GoNumericConstant): Boolean {
        return value > c.value.toShort()
    }

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoShort(this)
    }
}

data class GoInt(override val value: Int, override val type: GoType) : GoNumericConstant {
    override fun toString(): String = "$value"

    override fun plus(c: GoNumericConstant): GoNumericConstant {
        return GoInt(value + c.value.toInt(), type)
    }

    override fun minus(c: GoNumericConstant): GoNumericConstant {
        return GoInt(value - c.value.toInt(), type)
    }

    override fun times(c: GoNumericConstant): GoNumericConstant {
        return GoInt(value * c.value.toInt(), type)
    }

    override fun div(c: GoNumericConstant): GoNumericConstant {
        return GoInt(value / c.value.toInt(), type)
    }

    override fun rem(c: GoNumericConstant): GoNumericConstant {
        return GoInt(value % c.value.toInt(), type)
    }

    override fun unaryMinus(): GoNumericConstant {
        return GoInt(-value, type)
    }

    override fun isLessThan(c: GoNumericConstant): Boolean {
        return value < c.value.toInt()
    }

    override fun isGreaterThan(c: GoNumericConstant): Boolean {
        return value > c.value.toInt()
    }

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoInt(this)
    }
}

data class GoLong(override val value: Long, override val type: GoType) : GoNumericConstant {
    override fun toString(): String = "$value"

    override fun plus(c: GoNumericConstant): GoNumericConstant {
        return GoLong(value + c.value.toLong(), type)
    }

    override fun minus(c: GoNumericConstant): GoNumericConstant {
        return GoLong(value - c.value.toLong(), type)
    }

    override fun times(c: GoNumericConstant): GoNumericConstant {
        return GoLong(value * c.value.toLong(), type)
    }

    override fun div(c: GoNumericConstant): GoNumericConstant {
        return GoLong(value / c.value.toLong(), type)
    }

    override fun rem(c: GoNumericConstant): GoNumericConstant {
        return GoLong(value % c.value.toLong(), type)
    }

    override fun unaryMinus(): GoNumericConstant {
        return GoLong(-value, type)
    }

    override fun isLessThan(c: GoNumericConstant): Boolean {
        return value < c.value.toLong()
    }

    override fun isGreaterThan(c: GoNumericConstant): Boolean {
        return value > c.value.toLong()
    }

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoLong(this)
    }
}

data class GoFloat(override val value: Float, override val type: GoType) : GoNumericConstant {
    override fun toString(): String = "$value"

    override fun plus(c: GoNumericConstant): GoNumericConstant {
        return GoFloat(value + c.value.toFloat(), type)
    }

    override fun minus(c: GoNumericConstant): GoNumericConstant {
        return GoFloat(value - c.value.toFloat(), type)
    }

    override fun times(c: GoNumericConstant): GoNumericConstant {
        return GoFloat(value * c.value.toFloat(), type)
    }

    override fun div(c: GoNumericConstant): GoNumericConstant {
        return GoFloat(value / c.value.toFloat(), type)
    }

    override fun rem(c: GoNumericConstant): GoNumericConstant {
        return GoFloat(value % c.value.toFloat(), type)
    }

    override fun unaryMinus(): GoNumericConstant {
        return GoFloat(-value, type)
    }

    override fun isLessThan(c: GoNumericConstant): Boolean {
        return value < c.value.toFloat()
    }

    override fun isGreaterThan(c: GoNumericConstant): Boolean {
        return value > c.value.toFloat()
    }

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoFloat(this)
    }
}

data class GoDouble(override val value: Double, override val type: GoType) : GoNumericConstant {
    override fun toString(): String = "$value"

    override fun plus(c: GoNumericConstant): GoNumericConstant {
        return GoDouble(value + c.value.toDouble(), type)
    }

    override fun minus(c: GoNumericConstant): GoNumericConstant {
        return GoDouble(value.div(c.value.toDouble()), type)
    }

    override fun times(c: GoNumericConstant): GoNumericConstant {
        return GoDouble(value * c.value.toDouble(), type)
    }

    override fun div(c: GoNumericConstant): GoNumericConstant {
        return GoDouble(value.div(c.value.toDouble()), type)
    }

    override fun rem(c: GoNumericConstant): GoNumericConstant {
        return GoDouble(value.rem(c.value.toDouble()), type)
    }

    override fun unaryMinus(): GoNumericConstant {
        return GoDouble(-value, type)
    }

    override fun isLessThan(c: GoNumericConstant): Boolean {
        return value < c.value.toDouble()
    }

    override fun isGreaterThan(c: GoNumericConstant): Boolean {
        return value > c.value.toDouble()
    }

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoDouble(this)
    }
}

class GoNullConstant() : GoConstant {
    override val type: GoType = NullType()

    override fun toString(): String = "null"

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoNullConstant(this)
    }
}

data class GoStringConstant(val value: String, override val type: GoType) : GoConstant {
    override fun toString(): String = "\"$value\""

    override fun <T> accept(visitor: GoExprVisitor<T>): T {
        return visitor.visitGoStringConstant(this)
    }
}
