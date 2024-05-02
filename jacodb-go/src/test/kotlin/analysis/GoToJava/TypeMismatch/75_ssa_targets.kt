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

package analysis.GoToJava.TypeMismatch

import java.io.BufferedReader
import org.jacodb.go.api.*
class ssa_targets {

	var tail: ssa_targets? = null
	var _break: ssa_BasicBlock? = null
	var _continue: ssa_BasicBlock? = null
	var _fallthrough: ssa_BasicBlock? = null
}

fun read_ssa_targets(buffReader: BufferedReader, id: Int): ssa_targets {
	val res = ssa_targets()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id] as ssa_targets
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
    res.tail = mapDec[readType]?.invoke(buffReader, id) as ssa_targets?

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
    res._break = mapDec[readType]?.invoke(buffReader, id) as ssa_BasicBlock?

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
    res._continue = mapDec[readType]?.invoke(buffReader, id) as ssa_BasicBlock?

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
    res._fallthrough = mapDec[readType]?.invoke(buffReader, id) as ssa_BasicBlock?

	buffReader.readLine()
	return res
}
