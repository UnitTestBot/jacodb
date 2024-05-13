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

package org.usvm.jacodb.gen

import org.jacodb.go.api.*

class ssa_CallExpr(init: ssa_Call, val callee: GoMethod? = null) : ssaToJacoExpr, ssaToJacoValue {
    val type = (init.register!!.typ!! as ssaToJacoType).createJacoDBType()
    val value = (init.Call!!.Value!! as ssaToJacoValue).createJacoDBValue(callee!!)
    val operands = init.Call!!.Args!!.map { (it as ssaToJacoValue).createJacoDBValue(callee!!) }.map { i ->
        if (i is GoAssignableInst) {
            GoFreeVar(
                i.location.index,
                i.name,
                i.type
            )
        } else {
            i
        }
    }
    val name = "t${init.register!!.num!!}"
    val location = GoInstLocationImpl(
        init.register!!.anInstruction!!.block!!.Index!!.toInt(),
        init.Call!!.pos!!.toInt(),
        callee!!,
    )

    override fun createJacoDBExpr(parent: GoMethod): GoCallExpr {
		if (structToPtrMap.containsKey(this) && ptrToJacoMap.containsKey(structToPtrMap[this])) {
            return ptrToJacoMap[structToPtrMap[this]] as GoCallExpr
        }

        val res = GoCallExpr(
            location,
            type,
            value,
            operands,
            callee,
            name,
        )
		if (structToPtrMap.containsKey(this)) {
            ptrToJacoMap[structToPtrMap[this]!!] = res
        }
        return res
    }
	override fun createJacoDBValue(parent: GoMethod): GoValue {
		if (structToPtrMap.containsKey(this) && ptrToJacoMap.containsKey(structToPtrMap[this])) {
            return ptrToJacoMap[structToPtrMap[this]] as GoCallExpr
        }
        return createJacoDBExpr(parent)
    }

}
