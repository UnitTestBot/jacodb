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
class ssa_Slice : ssaToJacoExpr, ssaToJacoValue {

	var register: ssa_register? = null
	var X: Any? = null
	var Low: Any? = null
	var High: Any? = null
	var Max: Any? = null

	override fun createJacoDBExpr(parent: GoMethod): GoSliceExpr {
		if (structToPtrMap.containsKey(this) && ptrToJacoMap.containsKey(structToPtrMap[this])) {
            return ptrToJacoMap[structToPtrMap[this]] as GoSliceExpr
        }


		val low: GoValue = if (Low == null) {
            GoNullConstant()
        } else {
            (Low!! as ssaToJacoValue).createJacoDBValue(parent)
        }
        val high: GoValue = if (High == null) {
            GoNullConstant()
        } else {
            (High!! as ssaToJacoValue).createJacoDBValue(parent)
        }
        val max: GoValue = if (Max == null) {
            GoNullConstant()
        } else {
            (Max!! as ssaToJacoValue).createJacoDBValue(parent)
        }

		val res = GoSliceExpr(
			GoInstLocationImpl(
                register!!.anInstruction!!.block!!.Index!!.toInt(),
                register!!.pos!!.toInt(),
                parent
            ),
			(register!!.typ!! as ssaToJacoType).createJacoDBType(),
            (X!! as ssaToJacoValue).createJacoDBValue(parent),
			low,
			high,
			max,
			"t${register!!.num!!.toInt()}",
        )
		if (structToPtrMap.containsKey(this)) {
            ptrToJacoMap[structToPtrMap[this]!!] = res
        }
        return res
    }
	override fun createJacoDBValue(parent: GoMethod): GoValue {
		if (structToPtrMap.containsKey(this) && ptrToJacoMap.containsKey(structToPtrMap[this])) {
            return ptrToJacoMap[structToPtrMap[this]] as GoSliceExpr
        }
        return createJacoDBExpr(parent)
    }

}

fun read_ssa_Slice(buffReader: BufferedReader, id: Int): ssa_Slice {
	val res = ssa_Slice()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id] as ssa_Slice
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
    res.X = mapDec[readType]?.invoke(buffReader, id) as Any?

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
    res.Low = mapDec[readType]?.invoke(buffReader, id) as Any?

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
    res.High = mapDec[readType]?.invoke(buffReader, id) as Any?

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
    res.Max = mapDec[readType]?.invoke(buffReader, id) as Any?

	buffReader.readLine()
	return res
}
