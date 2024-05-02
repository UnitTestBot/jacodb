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
class ssa_BasicBlock {

	var Index: Long? = null
	var Comment: String? = null
	var parent: ssa_Function? = null
	var Instrs: List<Any>? = null
	var Preds: List<ssa_BasicBlock>? = null
	var Succs: List<ssa_BasicBlock>? = null
	var succs2: List<ssa_BasicBlock>? = null
	var dom: ssa_domInfo? = null
	var gaps: Long? = null
	var rundefers: Long? = null

	fun createJacoDBBasicBlock(method: GoMethod): GoBasicBlock {
		if (structToPtrMap.containsKey(this) && ptrToJacoMap.containsKey(structToPtrMap[this])) {
            return ptrToJacoMap[structToPtrMap[this]] as GoBasicBlock
        }


        val inst = mutableListOf<GoInst>()

        for (value in Instrs!!) {
            if (value is ssaToJacoInst) {
                inst.add(value.createJacoDBInst(method))
            }
        }

        val res = GoBasicBlock(
            Index!!.toInt(),
            Preds!!.map { it.Index!!.toInt() },
            Succs!!.map { it.Index!!.toInt() },
            inst
        )
		if (structToPtrMap.containsKey(this)) {
            ptrToJacoMap[structToPtrMap[this]!!] = res
        }
        return res
    }
}

fun read_ssa_BasicBlock(buffReader: BufferedReader, id: Int): ssa_BasicBlock {
	val res = ssa_BasicBlock()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id] as ssa_BasicBlock
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
    res.Index = mapDec[readType]?.invoke(buffReader, id) as Long?

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
    res.parent = mapDec[readType]?.invoke(buffReader, id) as ssa_Function?

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
    res.Instrs = mapDec[readType]?.invoke(buffReader, id) as List<Any>?

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
    res.Preds = mapDec[readType]?.invoke(buffReader, id) as List<ssa_BasicBlock>?

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
    res.Succs = mapDec[readType]?.invoke(buffReader, id) as List<ssa_BasicBlock>?

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
    res.succs2 = mapDec[readType]?.invoke(buffReader, id) as List<ssa_BasicBlock>?

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
    res.dom = mapDec[readType]?.invoke(buffReader, id) as ssa_domInfo?

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
    res.gaps = mapDec[readType]?.invoke(buffReader, id) as Long?

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
    res.rundefers = mapDec[readType]?.invoke(buffReader, id) as Long?

	buffReader.readLine()
	return res
}
