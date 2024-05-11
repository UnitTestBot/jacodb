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

package org.jacodb.panda.dynamic.api

import org.jacodb.api.common.cfg.CommonAssignInst
import org.jacodb.api.common.cfg.CommonCallInst
import org.jacodb.api.common.cfg.CommonGotoInst
import org.jacodb.api.common.cfg.CommonIfInst
import org.jacodb.api.common.cfg.CommonInst
import org.jacodb.api.common.cfg.CommonInstLocation
import org.jacodb.api.common.cfg.CommonReturnInst

interface Mappable

class PandaInstLocation(
    override val method: PandaMethod,
    private var _index: Int,
    override val lineNumber: Int,
) : CommonInstLocation<PandaMethod, PandaInst> {
    // TODO: expand like JcInstLocation

    internal fun decLocationIndex(count: Int) {
        _index -= count
    }

    override val index: Int
        get() = _index

    override fun toString(): String = "method.$index"
}

data class PandaInstRef(
    val index: Int,
) : Comparable<PandaInstRef> {
    // TODO: consider enabling this check
    // init {
    //     require(index >= 0) { "index must be non-negative" }
    // }

    override fun compareTo(other: PandaInstRef): Int {
        return this.index.compareTo(other.index)
    }

    override fun toString(): String = index.toString()
}

abstract class PandaInst : CommonInst<PandaMethod, PandaInst>, Mappable {
    abstract override val location: PandaInstLocation
    abstract override val operands: List<PandaExpr>

    abstract fun <T> accept(visitor: PandaInstVisitor<T>): T

    /**
     * Decrements
     */
    internal open fun decLocationIndex(idxList: List<Int>) {
        val count = idxList.count { gotoIdx -> gotoIdx < location.index }
        location.decLocationIndex(count)
    }
}

/**
 * Mocks PandaInst for WIP purposes.
 *
 * Map all unknown Panda IR instructions to this.
 */
class TODOInst(
    val opcode: String,
    override val location: PandaInstLocation,
    override val operands: List<PandaExpr>,
) : PandaInst() {

    override fun toString(): String = "$opcode(${operands.joinToString(separator = ", ")})"

    override fun <T> accept(visitor: PandaInstVisitor<T>): T {
        return visitor.visitTODOInst(this)
    }
}

class PandaEmptyBBPlaceholderInst(
    override val location: PandaInstLocation,
    private val bbId: Int
) : PandaTerminatingInst() {

    override val operands: List<PandaExpr>
        get() = emptyList()

    override fun <T> accept(visitor: PandaInstVisitor<T>): T {
        return visitor.visitPandaEmptyBBPlaceholderInst(this)
    }

    override fun toString(): String = "Empty [$bbId]"
}

class PandaAssignInst(
    override val location: PandaInstLocation,
    override val lhv: PandaValue,
    override val rhv: PandaExpr,
) : PandaInst(), CommonAssignInst<PandaMethod, PandaInst> {

    override val operands: List<PandaExpr>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv = $rhv"

    override fun <T> accept(visitor: PandaInstVisitor<T>): T {
        return visitor.visitPandaAssignInst(this)
    }
}

abstract class PandaBranchingInst : PandaInst() {
    abstract val successors: List<PandaInstRef>
}

class PandaNewLexenvInst(
    override val location: PandaInstLocation
) : PandaInst() {
    override val operands: List<PandaExpr>
        get() = emptyList()

    override fun <T> accept(visitor: PandaInstVisitor<T>): T {
        TODO("Not yet implemented")
    }

    override fun toString(): String = "newlexenv"
}

class PandaPopLexenvInst(
    override val location: PandaInstLocation
) : PandaInst() {
    override val operands: List<PandaExpr>
        get() = emptyList()

    override fun <T> accept(visitor: PandaInstVisitor<T>): T {
        TODO("Not yet implemented")
    }

    override fun toString(): String = "poplexenv"
}

class PandaGotoInst(
    override val location: PandaInstLocation,
) : PandaBranchingInst(), CommonGotoInst<PandaMethod, PandaInst> {

    var target: PandaInstRef = PandaInstRef(-1)
        private set

    override val operands: List<PandaExpr>
        get() = emptyList()

    override val successors: List<PandaInstRef>
        get() = listOf(target)

    override fun <T> accept(visitor: PandaInstVisitor<T>): T {
        return visitor.visitPandaGotoInst(this)
    }

    override fun toString(): String = "goto $target"

    internal fun setTarget(newTarget: PandaInstRef) {
        target = newTarget
    }

    override fun decLocationIndex(idxList: List<Int>) {
        super.decLocationIndex(idxList)
        val diff = idxList.count { gotoIdx -> gotoIdx < target.index }
        target = PandaInstRef(target.index - diff)
    }
}

class PandaIfInst(
    override val location: PandaInstLocation,
    val condition: PandaConditionExpr,
    private var _trueBranch: Lazy<PandaInstRef>,
    private var _falseBranch: Lazy<PandaInstRef>,
) : PandaBranchingInst(), CommonIfInst<PandaMethod, PandaInst> {

    val trueBranch: PandaInstRef
        get() = minOf(_trueBranch.value, _falseBranch.value)

    val falseBranch: PandaInstRef
        get() = maxOf(_trueBranch.value, _falseBranch.value)

    override val successors: List<PandaInstRef>
        get() = listOf(trueBranch, falseBranch)

    override val operands: List<PandaExpr>
        get() = listOf(condition)

    override fun toString(): String = "if ($condition) then $trueBranch else $falseBranch"

    override fun <T> accept(visitor: PandaInstVisitor<T>): T {
        return visitor.visitPandaIfInst(this)
    }

    override fun decLocationIndex(idxList: List<Int>) {
        super.decLocationIndex(idxList)
        val trueBranchIndex = trueBranch.index
        val falseBranchIndex = falseBranch.index
        val trueBranchDiff = idxList.count { gotoIdx -> gotoIdx < trueBranchIndex }
        val falseBranchDiff = idxList.count { gotoIdx -> gotoIdx < falseBranchIndex }

        _trueBranch = lazy { PandaInstRef(trueBranchIndex - trueBranchDiff) }
        _falseBranch = lazy { PandaInstRef(falseBranchIndex - falseBranchDiff) }

    }
}

abstract class PandaTerminatingInst : PandaInst()

class PandaReturnInst(
    override val location: PandaInstLocation,
    override val returnValue: PandaValue?,
) : PandaTerminatingInst(), CommonReturnInst<PandaMethod, PandaInst> {

    override val operands: List<PandaExpr>
        get() = listOfNotNull(returnValue)

    override fun toString(): String = buildString {
        append("return")
        if (returnValue != null) {
            append(" ")
            append(returnValue)
        }
    }

    override fun <T> accept(visitor: PandaInstVisitor<T>): T {
        return visitor.visitPandaReturnInst(this)
    }
}

class PandaThrowInst(
    override val location: PandaInstLocation,
    val throwable: PandaValue,
) : PandaTerminatingInst() {
    override val operands: List<PandaExpr>
        get() = listOf(throwable)

    override fun toString(): String = "throw $throwable"

    override fun <T> accept(visitor: PandaInstVisitor<T>): T {
        return visitor.visitPandaThrowInst(this)
    }
}

class PandaCatchInst(
    override val location: PandaInstLocation,
    val throwable: PandaValue,
    private var _throwers: List<PandaInstRef>,
) : PandaInst() {
    override val operands: List<PandaExpr>
        get() = listOf(throwable)

    val throwers: List<PandaInstRef>
        get() = _throwers

    override fun <T> accept(visitor: PandaInstVisitor<T>): T {
        return visitor.visitPandaCatchInst(this)
    }

    override fun toString(): String = "catch$throwers ($throwable)"

    override fun decLocationIndex(idxList: List<Int>) {
        super.decLocationIndex(idxList)
        _throwers = _throwers.mapNotNull { inst ->
            if (idxList.find { gotoIdx -> gotoIdx == inst.index } != null) return@mapNotNull null

            val diff = idxList.count { gotoIdx -> gotoIdx < inst.index }
            PandaInstRef(inst.index - diff)
        }
    }
}

class PandaCallInst(
    override val location: PandaInstLocation,
    val callExpr: PandaCallExpr,
) : PandaInst(), CommonCallInst<PandaMethod, PandaInst> {

    override val operands: List<PandaExpr>
        get() = listOf(callExpr)

    override fun toString(): String = callExpr.toString()

    override fun <T> accept(visitor: PandaInstVisitor<T>): T {
        return visitor.visitPandaCallInst(this)
    }
}

object CallExprVisitor :
    PandaInstVisitor<PandaCallExpr?>,
    CommonInst.Visitor.Default<PandaCallExpr?> {

    override fun defaultVisitCommonInst(inst: CommonInst<*, *>): PandaCallExpr? {
        TODO("Not yet implemented")
    }

    fun defaultVisitPandaInst(inst: PandaInst): PandaCallExpr? {
        return inst.operands.filterIsInstance<PandaCallExpr>().firstOrNull()
    }

    override fun visitTODOInst(inst: TODOInst): PandaCallExpr? = defaultVisitPandaInst(inst)
    override fun visitPandaThrowInst(inst: PandaThrowInst): PandaCallExpr? = defaultVisitPandaInst(inst)
    override fun visitPandaReturnInst(inst: PandaReturnInst): PandaCallExpr? = defaultVisitPandaInst(inst)
    override fun visitPandaAssignInst(inst: PandaAssignInst): PandaCallExpr? = defaultVisitPandaInst(inst)
    override fun visitPandaCallInst(inst: PandaCallInst): PandaCallExpr? = defaultVisitPandaInst(inst)
    override fun visitPandaIfInst(inst: PandaIfInst): PandaCallExpr? = defaultVisitPandaInst(inst)
    override fun visitPandaGotoInst(inst: PandaGotoInst): PandaCallExpr? = defaultVisitPandaInst(inst)
    override fun visitPandaCatchInst(inst: PandaCatchInst): PandaCallExpr? = defaultVisitPandaInst(inst)
    override fun visitPandaEmptyBBPlaceholderInst(inst: PandaEmptyBBPlaceholderInst): PandaCallExpr? =
        defaultVisitPandaInst(inst)
}

val PandaInst.callExpr: PandaCallExpr?
    get() = accept(CallExprVisitor)
