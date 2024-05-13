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
class ssa_RunDefers : ssaToJacoInst {

	var anInstruction: generatedInlineStruct_000? = null

	override fun createJacoDBInst(parent: GoMethod): GoRunDefersInst {
        return GoRunDefersInst(
            GoInstLocationImpl(
                anInstruction!!.block!!.Index!!.toInt(),
                0,
                parent,
            ),
        )
    }
}

fun read_ssa_RunDefers(buffReader: BufferedReader, id: Int): ssa_RunDefers {
	val res = ssa_RunDefers()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id] as ssa_RunDefers
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
    res.anInstruction = mapDec[readType]?.invoke(buffReader, id) as generatedInlineStruct_000?

	buffReader.readLine()
	return res
}
