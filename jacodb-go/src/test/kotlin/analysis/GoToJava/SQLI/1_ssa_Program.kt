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
class ssa_Program : ssaToJacoProject {

	var Fset: token_FileSet? = null
	var imported: Map<String, ssa_Package>? = null
	var packages: Map<types_Package, ssa_Package>? = null
	var mode: ULong? = null
	var MethodSets: typeutil_MethodSetCache? = null
	var canon: ssa_canonizer? = null
	var ctxt: types_Context? = null
	var methodsMu: sync_Mutex? = null
	var methodSets: typeutil_Map? = null
	var parameterized: ssa_tpWalker? = null
	var runtimeTypesMu: sync_Mutex? = null
	var runtimeTypes: typeutil_Map? = null
	var objectMethodsMu: sync_Mutex? = null
	var objectMethods: Map<types_Func, ssa_Function>? = null

	override fun createJacoDBProject(): GoProject {
		if (structToPtrMap.containsKey(this) && ptrToJacoMap.containsKey(structToPtrMap[this])) {
            return ptrToJacoMap[structToPtrMap[this]] as GoProject
        }


        val methods = mutableListOf<GoMethod>()
        val used = hashMapOf<ssa_Function, Boolean>()
        for (pkg in packages!!) {
            for (member in pkg.value.Members!!) {
                val method = member.value
                if (method is ssa_Function && used[method] == null) {
                    methods.add(method.createJacoDBMethod())
                    used[method] = true
                    for (block in method.Blocks!!) {
                        for (inst in block.Instrs!!) {
                            if (inst is ssa_Call) {
                                val value = inst.Call!!.Value!!
                                if (value is ssa_Function && used[value] == null) {
                                    methods.add(value.createJacoDBMethod())
                                    used[value] = true
                                }
                            }
                        }
                    }
                }
            }
        }

        val res = GoProject(
            methods.toList()
        )
		if (structToPtrMap.containsKey(this)) {
            ptrToJacoMap[structToPtrMap[this]!!] = res
        }
        return res
    }
}

fun read_ssa_Program(buffReader: BufferedReader, id: Int): ssa_Program {
	val res = ssa_Program()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id] as ssa_Program
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
    res.Fset = mapDec[readType]?.invoke(buffReader, id) as token_FileSet?

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
    res.imported = mapDec[readType]?.invoke(buffReader, id) as Map<String, ssa_Package>?

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
    res.packages = mapDec[readType]?.invoke(buffReader, id) as Map<types_Package, ssa_Package>?

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
    res.mode = mapDec[readType]?.invoke(buffReader, id) as ULong?

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
    res.MethodSets = mapDec[readType]?.invoke(buffReader, id) as typeutil_MethodSetCache?

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
    res.canon = mapDec[readType]?.invoke(buffReader, id) as ssa_canonizer?

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
    res.ctxt = mapDec[readType]?.invoke(buffReader, id) as types_Context?

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
    res.methodsMu = mapDec[readType]?.invoke(buffReader, id) as sync_Mutex?

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
    res.methodSets = mapDec[readType]?.invoke(buffReader, id) as typeutil_Map?

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
    res.parameterized = mapDec[readType]?.invoke(buffReader, id) as ssa_tpWalker?

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
    res.runtimeTypesMu = mapDec[readType]?.invoke(buffReader, id) as sync_Mutex?

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
    res.runtimeTypes = mapDec[readType]?.invoke(buffReader, id) as typeutil_Map?

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
    res.objectMethodsMu = mapDec[readType]?.invoke(buffReader, id) as sync_Mutex?

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
    res.objectMethods = mapDec[readType]?.invoke(buffReader, id) as Map<types_Func, ssa_Function>?

	buffReader.readLine()
	return res
}
