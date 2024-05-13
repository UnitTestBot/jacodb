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

import java.io.BufferedReader
import org.jacodb.go.api.*
class ssa_Call : ssaToJacoInst, ssaToJacoValue, ssaToJacoExpr {

	var register: ssa_register? = null
	var Call: ssa_CallCommon? = null

	var CallExpr: GoCallExpr? = null
	override fun createJacoDBInst(parent: GoMethod): GoCallInst {
        if (CallExpr == null) {
            val callee: GoMethod = if (Call!!.Value!! is ssaToJacoMethod) {
                (Call!!.Value!! as ssaToJacoMethod).createJacoDBMethod()
            } else if (Call!!.Value!! is ssaToJacoExpr) {
                val value = (Call!!.Value!! as ssaToJacoExpr).createJacoDBExpr(parent)
                if (value is GoMakeClosureExpr) {
                    value.func
                } else {
                    parent
                }
            } else {
                parent
            }
            CallExpr = ssa_CallExpr(this, callee).createJacoDBExpr(parent)
        }
        return GoCallInst(
            GoInstLocationImpl(
                register!!.anInstruction!!.block!!.Index!!.toInt(),
                Call!!.pos!!.toInt(),
                parent,
            ),
            CallExpr!!,
			"t${register!!.num!!}",
            (register!!.typ!! as ssaToJacoType).createJacoDBType()
        )
    }
	
	override fun createJacoDBValue(parent: GoMethod): GoValue {
        if (CallExpr == null) {
            CallExpr = ssa_CallExpr(this, parent).createJacoDBExpr(parent)
        }
        return CallExpr!!
    }

	override fun createJacoDBExpr(parent: GoMethod): GoExpr {
        return createJacoDBValue(parent)
    }
}

fun read_ssa_Call(buffReader: BufferedReader, id: Int): ssa_Call {
	val res = ssa_Call()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id] as ssa_Call
        }
        ptrMap[id] = res
		structToPtrMap[res] = id
    }
    var line: String
    var split: List<String>
    var id: Int
    var readType: String

	line = buffReader.readLine()
	if (line == "end") {
        return res
    }
    split = line.split(" ")
    readType = split[1]
    id = -1
    if (split.size > 2) {
        id = split[2].toInt()
    }
    res.register = mapDec[readType]?.invoke(buffReader, id) as ssa_register?

	line = buffReader.readLine()
	if (line == "end") {
        return res
    }
    split = line.split(" ")
    readType = split[1]
    id = -1
    if (split.size > 2) {
        id = split[2].toInt()
    }
    res.Call = mapDec[readType]?.invoke(buffReader, id) as ssa_CallCommon?

	buffReader.readLine()
	return res
}
