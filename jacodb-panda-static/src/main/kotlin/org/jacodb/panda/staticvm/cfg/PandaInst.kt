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

data class PandaInstLocation(
    override val method: PandaMethod,
    val index: Int,
) : CommonInstLocation {
    // TODO: expand like JcInstLocation

    // override val lineNumber: Int
    //     get() = 0 // TODO("Not yet implemented")

    override fun toString(): String = "${method.name}:$index"
}

data class PandaInstRef(
    val index: Int,
) {
    override fun toString(): String = index.toString()
}

sealed interface PandaInst : CommonInst {
    override val location: PandaInstLocation

    override val method: PandaMethod
        get() = location.method

    // TODO: remove 'operands'
    val operands: List<PandaExpr>
}

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
) : PandaInst, CommonAssignInst {
    override val operands: List<PandaExpr>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv = $rhv"
}

sealed interface PandaBranchingInst : PandaInst {
    val successors: List<PandaInstRef>
}

class PandaIfInst(
    override val location: PandaInstLocation,
    val condition: PandaConditionExpr,
    val trueBranch: PandaInstRef,
    val falseBranch: PandaInstRef,
) : PandaBranchingInst, CommonIfInst {
    override val operands: List<PandaExpr>
        get() = listOf(condition)

    override val successors: List<PandaInstRef>
        get() = listOf(trueBranch, falseBranch)

    override fun toString(): String = "if ($condition) goto ${trueBranch.index} else goto ${falseBranch.index}"
}

class PandaGotoInst(
    override val location: PandaInstLocation,
    val target: PandaInstRef,
) : PandaBranchingInst, CommonGotoInst {
    override val operands: List<PandaExpr>
        get() = emptyList()

    override val successors: List<PandaInstRef>
        get() = listOf(target)

    override fun toString(): String = "goto ${target.index}"
}

sealed interface PandaTerminatingInst : PandaInst

class PandaReturnInst(
    override val location: PandaInstLocation,
    override val returnValue: PandaValue?,
) : PandaTerminatingInst, CommonReturnInst {
    override val operands: List<PandaExpr>
        get() = emptyList()

    override fun toString(): String =
        if (returnValue != null) {
            "return $returnValue"
        } else {
            "return"
        }
}

class PandaThrowInst(
    override val location: PandaInstLocation,
    val error: PandaValue,
    val catchers: List<PandaInstRef>,
) : PandaTerminatingInst {
    override val operands: List<PandaExpr>
        get() = listOf(error)

    override fun toString(): String = "throw $error"
}

class PandaPhiInst(
    override val location: PandaInstLocation,
    val lhv: PandaValue,
    val phiInputs: List<PhiInput>,
) : PandaInst {
    data class PhiInput(val value: PandaValue, val cfgBranch: PandaInstRef)

    val inputs: List<PandaExpr>
        get() = phiInputs.map { it.value }

    override val operands: List<PandaExpr>
        get() = inputs

    override fun toString() = "$lhv = Phi(${
        phiInputs.joinToString { "${it.value} <- ${it.cfgBranch}" }
    })"
}

class PandaCatchInst(
    override val location: PandaInstLocation,
    val throwable: PandaValue,
    val throwers: List<PandaInstRef>,
) : PandaInst {
    override val operands: List<PandaExpr>
        get() = emptyList()

    override fun toString(): String = "catch($throwable: ${throwable.typeName})"
}

class PandaTryPseudoInst(
    override val location: PandaInstLocation,
    val catchers: List<PandaInstRef>,
) : PandaInst {
    override val operands: List<PandaExpr>
        get() = emptyList()

    override fun toString(): String = "try"
}

val PandaInst.callExpr: PandaCallExpr?
    get() = operands.filterIsInstance<PandaCallExpr>().firstOrNull()
