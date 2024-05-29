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
class ssa_Phi : ssaToJacoExpr, ssaToJacoValue {

	var register: ssa_register? = null
	var Comment: String? = null
	var Edges: List<Any>? = null

	override fun createJacoDBExpr(parent: GoMethod): GoPhiExpr {
		if (structToPtrMap.containsKey(this) && ptrToJacoMap.containsKey(structToPtrMap[this])) {
            return ptrToJacoMap[structToPtrMap[this]] as GoPhiExpr
        }


        val res = GoPhiExpr(
			GoInstLocationImpl(
                register!!.anInstruction!!.block!!.Index!!.toInt(),
                register!!.pos!!.toInt(),
                parent
            ),
            (register!!.typ!! as ssaToJacoType).createJacoDBType(),
			listOf(),
            "t${register!!.num!!.toInt()}"
        )
        if (structToPtrMap.containsKey(this)) {
            ptrToJacoMap[structToPtrMap[this]!!] = res
        }
        
        res.edges = Edges!!.map { (it as ssaToJacoValue).createJacoDBValue(parent) }
		return res
    }
	override fun createJacoDBValue(parent: GoMethod): GoValue {
		if (structToPtrMap.containsKey(this) && ptrToJacoMap.containsKey(structToPtrMap[this])) {
            return ptrToJacoMap[structToPtrMap[this]] as GoPhiExpr
        }
        return createJacoDBExpr(parent)
    }

}

fun read_ssa_Phi(buffReader: BufferedReader, id: Int): ssa_Phi {
	val res = ssa_Phi()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id] as ssa_Phi
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
    res.Comment = mapDec[readType]?.invoke(buffReader, id) as String?

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
    res.Edges = mapDec[readType]?.invoke(buffReader, id) as List<Any>?

	buffReader.readLine()
	return res
}
