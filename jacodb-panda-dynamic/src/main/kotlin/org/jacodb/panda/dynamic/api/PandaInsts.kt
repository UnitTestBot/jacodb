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

import org.jacodb.api.core.cfg.*

interface Mappable

class PandaInstLocation(
    override val method: PandaMethod,
    override val index: Int,
    override val lineNumber: Int
) : CoreInstLocation<PandaMethod> {
    // TODO: expand like JcInstLocation

    override fun toString() = "method.$index"
}

data class PandaInstRef(
    val index: Int
) : Comparable<PandaInstRef> {

    override fun compareTo(other: PandaInstRef): Int {
        return this.index.compareTo(other.index)
    }

    override fun toString() = index.toString()
}

interface PandaInst : CoreInst<PandaInstLocation, PandaMethod, PandaExpr>, Mappable {

    override val location: PandaInstLocation
    override val operands: List<PandaExpr>
    override val lineNumber: Int
        get() = location.lineNumber

    override fun <T> accept(visitor: InstVisitor<T>): T {
        TODO("Not yet implemented")
    }

    fun <T> accept(visitor: PandaInstVisitor<T>): T
}

interface PandaTerminatingInst : PandaInst

/**
Mock Inst for WIP purposes.

Maps all unknown Panda IR instructions to this.
 */
class TODOInst(
    val opcode: String,
    override val location: PandaInstLocation,
    override val operands: List<PandaExpr>
) : PandaInst {

    override fun <T> accept(visitor: PandaInstVisitor<T>): T {
        return visitor.visitTODOInst(this)
    }

    override fun toString() = "$opcode(${operands.joinToString(separator = ", ")})"
}

interface PandaBranchingInst : PandaInst {
    val successors: List<PandaInstRef>
}

class PandaIfInst(
    override val location: PandaInstLocation,
    val condition: PandaConditionExpr,
    private val _trueBranch: Lazy<PandaInstRef>,
    private val _falseBranch: Lazy<PandaInstRef>
) : CoreIfInst<PandaInstLocation, PandaMethod, PandaExpr>, PandaInst, PandaBranchingInst {

    val trueBranch: PandaInstRef
        get() = minOf(_trueBranch.value, _falseBranch.value)

    val falseBranch: PandaInstRef
        get() = maxOf(_trueBranch.value, _falseBranch.value)

    override val successors: List<PandaInstRef>
        get() = listOf(trueBranch, falseBranch)

    override val operands: List<PandaExpr> = listOf(condition)

    override fun toString(): String = "if ($condition) then $trueBranch else $falseBranch"

    override fun <T> accept(visitor: PandaInstVisitor<T>): T {
        return visitor.visitPandaIfInst(this)
    }
}

class PandaReturnInst(
    override val location: PandaInstLocation,
    returnValue: PandaValue?
) : PandaInst, PandaTerminatingInst, CoreReturnInst<PandaInstLocation, PandaMethod, PandaExpr> {

    override val operands: List<PandaExpr> = listOfNotNull(returnValue)

    override fun <T> accept(visitor: PandaInstVisitor<T>): T {
        return visitor.visitPandaReturnInst(this)
    }

    override fun toString(): String = "return ${operands.firstOrNull()}"
}

class PandaAssignInst(
    override val location: PandaInstLocation,
    override val lhv: PandaValue,
    override val rhv: PandaExpr
) : PandaInst, CoreAssignInst<PandaInstLocation, PandaMethod, PandaValue, PandaExpr, PandaType> {

    override val operands: List<PandaExpr> = listOf(lhv, rhv)

    override fun toString(): String = "$lhv = $rhv"

    override fun <T> accept(visitor: PandaInstVisitor<T>): T {
        return visitor.visitPandaAssignInst(this)
    }
}

class PandaCallInst(
    override val location: PandaInstLocation,
    val callExpr: PandaCallExpr
) : PandaInst, CoreCallInst<PandaInstLocation, PandaMethod, PandaExpr> {

    override val operands: List<PandaExpr> = listOf(callExpr)

    override fun <T> accept(visitor: PandaInstVisitor<T>): T {
        return visitor.visitPandaCallInst(this)
    }

    override fun toString(): String = callExpr.toString()
}
