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

import org.jacodb.api.core.cfg.*
import org.jacodb.panda.staticvm.classpath.MethodNode
import org.jacodb.panda.staticvm.classpath.TypeNode

class PandaInstLocation(
    override val method: MethodNode,
    override val index: Int
) : CoreInstLocation<MethodNode> {
    // TODO: expand like JcInstLocation

    override fun toString() = "method.$index"

    override val lineNumber: Int
        get() = 0 // TODO("Not yet implemented")
}

class PandaInstRef(
    val index: Int
) : Comparable<PandaInstRef> {

    override fun compareTo(other: PandaInstRef): Int {
        return this.index.compareTo(other.index)
    }

    override fun toString() = index.toString()
}

sealed interface PandaInst : CoreInst<PandaInstLocation, MethodNode, PandaExpr> {
    override val location: PandaInstLocation
    override val operands: List<PandaExpr>
    override val lineNumber: Int
        get() = location.lineNumber

    override fun <T> accept(visitor: InstVisitor<T>): T {
        TODO("Not yet implemented")
    }
}


// TODO: придумать, как убрать этот костыль (нужно корректно обрабатывать ссылки на пустой basicBlock)
class PandaDoNothingInst(
    override val location: PandaInstLocation
) : PandaInst {
    override val operands: List<PandaExpr>
        get() = emptyList()

    override fun toString() = "NOP"
}

class PandaParameterInst(
    override val location: PandaInstLocation,
    val lhv: PandaValue,
    val index: Int
) : PandaInst {
    override val operands: List<PandaExpr>
        get() = emptyList()

    override fun toString() = "$lhv = arg$index"
}

class PandaAssignInst(
    override val location: PandaInstLocation,
    override val lhv: PandaValue,
    override val rhv: PandaExpr
) : PandaInst, CoreAssignInst<PandaInstLocation, MethodNode, PandaValue, PandaExpr, TypeNode> {
    override val operands: List<PandaExpr>
        get() = listOf(rhv)

    override fun toString(): String = "$lhv = $rhv"
}

sealed interface PandaBranchingInst : PandaInst {
    val successors: List<PandaInstRef>
}

class PandaIfInst(
    override val location: PandaInstLocation,
    val condition: PandaConditionExpr,
    val trueBranch: PandaInstRef,
    val falseBranch: PandaInstRef
) : PandaBranchingInst, CoreIfInst<PandaInstLocation, MethodNode, PandaExpr> {
    override val operands: List<PandaExpr>
        get() = listOf(condition)
    override val successors: List<PandaInstRef>
        get() = listOf(trueBranch, falseBranch)

    override fun toString() = "if ($condition) goto ${trueBranch.index} else goto ${falseBranch.index}"
}

class PandaGotoInst(
    override val location: PandaInstLocation,
    val target: PandaInstRef
) : PandaBranchingInst, CoreGotoInst<PandaInstLocation, MethodNode, PandaExpr> {
    override val successors: List<PandaInstRef>
        get() = listOf(target)

    override val operands: List<PandaExpr>
        get() = emptyList()

    override fun toString() = "goto ${target.index}"
}

sealed interface PandaTerminatingInst : PandaInst

class PandaReturnInst(
    override val location: PandaInstLocation,
    val value: PandaValue?
) : PandaTerminatingInst, CoreReturnInst<PandaInstLocation, MethodNode, PandaExpr> {
    override val operands: List<PandaExpr>
        get() = emptyList()

    override fun toString() = "return " + (value ?: "")
}
