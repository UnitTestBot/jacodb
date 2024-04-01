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

package org.jacodb.panda.staticvm.cfg

import org.jacodb.api.common.cfg.CommonAssignInst
import org.jacodb.api.common.cfg.CommonGotoInst
import org.jacodb.api.common.cfg.CommonIfInst
import org.jacodb.api.common.cfg.CommonInst
import org.jacodb.api.common.cfg.CommonInstLocation
import org.jacodb.api.common.cfg.CommonReturnInst
import org.jacodb.panda.staticvm.classpath.PandaMethod

class PandaInstLocation(
    override val method: PandaMethod,
    override val index: Int,
) : CommonInstLocation<PandaMethod, PandaInst> {
    // TODO: expand like JcInstLocation

    override fun toString(): String = "method.$index"

    override val lineNumber: Int
        get() = 0 // TODO("Not yet implemented")
}

class PandaInstRef(
    val index: Int,
) : Comparable<PandaInstRef> {

    override fun compareTo(other: PandaInstRef): Int {
        return this.index.compareTo(other.index)
    }

    override fun toString(): String = index.toString()
}

sealed interface PandaInst : CommonInst<PandaMethod, PandaInst> {
    override val location: PandaInstLocation
    override val operands: List<PandaExpr>
    override val lineNumber: Int
        get() = location.lineNumber

    override fun <T> accept(visitor: CommonInst.Visitor<T>): T {
        return visitor.visitExternalCommonInst(this)
    }
}

// TODO: придумать, как убрать этот костыль (нужно корректно обрабатывать ссылки на пустой basicBlock)
class PandaDoNothingInst(
    override val location: PandaInstLocation,
) : PandaInst {
    override val operands: List<PandaExpr>
        get() = emptyList()

    override fun toString(): String = "NOP"
}

class PandaParameterInst(
    override val location: PandaInstLocation,
    val lhv: PandaValue,
    val index: Int,
) : PandaInst {
    override val operands: List<PandaExpr>
        get() = emptyList()

    override fun toString(): String = "$lhv = arg$index"
}

class PandaAssignInst(
    override val location: PandaInstLocation,
    override val lhv: PandaValue,
    override val rhv: PandaExpr,
) : PandaInst, CommonAssignInst<PandaMethod, PandaInst> {
    override val operands: List<PandaExpr>
        get() = listOf(rhv)

    override fun toString(): String = "$lhv = $rhv"

    override fun <T> accept(visitor: CommonInst.Visitor<T>): T {
        return visitor.visitCommonAssignInst(this)
    }
}

sealed interface PandaBranchingInst : PandaInst {
    val successors: List<PandaInstRef>
}

class PandaIfInst(
    override val location: PandaInstLocation,
    val condition: PandaConditionExpr,
    val trueBranch: PandaInstRef,
    val falseBranch: PandaInstRef,
) : PandaBranchingInst, CommonIfInst<PandaMethod, PandaInst> {
    override val operands: List<PandaExpr>
        get() = listOf(condition)
    override val successors: List<PandaInstRef>
        get() = listOf(trueBranch, falseBranch)

    override fun toString(): String = "if ($condition) goto ${trueBranch.index} else goto ${falseBranch.index}"

    override fun <T> accept(visitor: CommonInst.Visitor<T>): T {
        return visitor.visitCommonIfInst(this)
    }
}

class PandaGotoInst(
    override val location: PandaInstLocation,
    val target: PandaInstRef,
) : PandaBranchingInst, CommonGotoInst<PandaMethod, PandaInst> {
    override val successors: List<PandaInstRef>
        get() = listOf(target)

    override val operands: List<PandaExpr>
        get() = emptyList()

    override fun toString(): String = "goto ${target.index}"

    override fun <T> accept(visitor: CommonInst.Visitor<T>): T {
        return visitor.visitCommonGotoInst(this)
    }
}

sealed interface PandaTerminatingInst : PandaInst

class PandaReturnInst(
    override val location: PandaInstLocation,
    override val returnValue: PandaValue?,
) : PandaTerminatingInst, CommonReturnInst<PandaMethod, PandaInst> {
    override val operands: List<PandaExpr>
        get() = emptyList()

    override fun toString(): String = "return " + (returnValue ?: "")

    override fun <T> accept(visitor: CommonInst.Visitor<T>): T {
        return visitor.visitCommonReturnInst(this)
    }
}

class PandaThrowInst(
    override val location: PandaInstLocation,
    val error: PandaValue,
    val catchers: List<PandaInstRef>
) : PandaBranchingInst, PandaTerminatingInst {
    override val operands: List<PandaExpr>
        get() = listOf(error)

    override val successors: List<PandaInstRef>
        get() = catchers

    override fun toString(): String = "throw $error"
}

class PandaCatchInst(
    override val location: PandaInstLocation,
    val throwers: List<PandaInstRef>
) : PandaInst {
    override val operands: List<PandaExpr>
        get() = emptyList()

    override fun toString(): String = "catch <- ${throwers.joinToString()}"
}
