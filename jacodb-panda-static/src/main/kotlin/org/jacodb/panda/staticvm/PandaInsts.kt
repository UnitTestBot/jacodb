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

package org.jacodb.panda.staticvm

import org.jacodb.api.core.cfg.CoreInst
import org.jacodb.api.core.cfg.CoreInstLocation
import org.jacodb.api.core.cfg.InstVisitor

class PandaInstLocation(
    override val method: PandaMethod,
    override val index: Int
) : CoreInstLocation<PandaMethod> {
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

sealed interface PandaInst : CoreInst<PandaInstLocation, PandaMethod, PandaExpr> {
    override val location: PandaInstLocation
    override val operands: List<PandaExpr>
    override val lineNumber: Int
        get() = location.lineNumber

    override fun <T> accept(visitor: InstVisitor<T>): T {
        TODO("Not yet implemented")
    }
}

class PandaAssignInst(
    override val location: PandaInstLocation,
    val lhv: PandaValue,
    val rhv: PandaExpr
) : PandaInst {
    override val operands: List<PandaExpr>
        get() = listOf(rhv)
}

sealed interface PandaBranchingInst : PandaInst {
    val successors: List<PandaInstRef>
}

class PandaIfInst(
    override val location: PandaInstLocation,
    val condition: PandaConditionExpr,
    val trueBranch: PandaInstRef,
    val falseBranch: PandaInstRef
) : PandaBranchingInst {
    override val operands: List<PandaExpr>
        get() = listOf(condition)
    override val successors: List<PandaInstRef>
        get() = listOf(trueBranch, falseBranch)
}

class PandaGotoInst(
    override val location: PandaInstLocation,
    val target: PandaInstRef
) : PandaBranchingInst {
    override val successors: List<PandaInstRef>
        get() = listOf(target)

    override val operands: List<PandaExpr>
        get() = emptyList()
}

sealed interface PandaTerminatingInst : PandaInst

class PandaReturnInst(
    override val location: PandaInstLocation,
    val value: PandaValue?
) : PandaTerminatingInst {
    override val operands: List<PandaExpr>
        get() = emptyList()
}

