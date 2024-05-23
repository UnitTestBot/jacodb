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
class ssa_Function : ssaToJacoExpr, ssaToJacoValue, ssaToJacoMethod {

	var name: String? = null
	var Object: types_Func? = null
	var method: ssa_selection? = null
	var Signature: types_Signature? = null
	var pos: Long? = null
	var Synthetic: String? = null
	var syntax: Any? = null
	var info: types_Info? = null
	var goversion: String? = null
	var parent: ssa_Function? = null
	var Pkg: ssa_Package? = null
	var Prog: ssa_Program? = null
	var Params: List<ssa_Parameter>? = null
	var FreeVars: List<ssa_FreeVar>? = null
	var Locals: List<ssa_Alloc>? = null
	var Blocks: List<ssa_BasicBlock>? = null
	var Recover: ssa_BasicBlock? = null
	var AnonFuncs: List<ssa_Function>? = null
	var referrers: List<Any>? = null
	var anonIdx: Long? = null
	var typeparams: types_TypeParamList? = null
	var typeargs: List<Any>? = null
	var topLevelOrigin: ssa_Function? = null
	var generic: ssa_generic? = null
	var currentBlock: ssa_BasicBlock? = null
	var vars: Map<types_Var, Any>? = null
	var namedResults: List<ssa_Alloc>? = null
	var targets: ssa_targets? = null
	var lblocks: Map<types_Label, ssa_lblock>? = null
	var subst: ssa_subster? = null

	override fun createJacoDBMethod(fileSet: FileSet): GoFunction {
		if (structToPtrMap.containsKey(this) && ptrToJacoMap.containsKey(structToPtrMap[this])) {
            return ptrToJacoMap[structToPtrMap[this]] as GoFunction
        }


        val returns = mutableListOf<GoType>()

        if (Signature!!.results!!.vars != null) {
            for (ret in Signature!!.results!!.vars!!) {
                returns.add((ret.Object!!.typ!! as ssaToJacoType).createJacoDBType())
            }
        }

        val res =
            GoFunction(
                Signature!!.createJacoDBType(),
                listOf(),
                name!!,
                listOf(),
                returns, //TODO
                Pkg?.Pkg?.name ?: "null",
				fileSet,
            )

		if (structToPtrMap.containsKey(this)) {
            ptrToJacoMap[structToPtrMap[this]!!] = res
        }

        res.operands = Params!!.map { it.createJacoDBExpr(res) } // TODO
        res.blocks = Blocks!!.map { it.createJacoDBBasicBlock(res) }
        res.blocks.forEach { b ->
            b.insts = b.insts.map { i ->
                if (i is GoAssignableInst) {
                    i.toAssignInst()
                } else {
                    i
                }
            }
        }

		return res
    }
	
	override fun createJacoDBValue(parent: GoMethod): GoValue {
		if (structToPtrMap.containsKey(this) && ptrToJacoMap.containsKey(structToPtrMap[this])) {
            return ptrToJacoMap[structToPtrMap[this]] as GoFunction
        }
        return createJacoDBMethod(parent.fileSet)
    }

	override fun createJacoDBExpr(parent: GoMethod): GoExpr {
        return createJacoDBValue(parent)
    }
}

fun read_ssa_Function(buffReader: BufferedReader, id: Int): ssa_Function {
	val res = ssa_Function()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id] as ssa_Function
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
    res.name = mapDec[readType]?.invoke(buffReader, id) as String?

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
    res.Object = mapDec[readType]?.invoke(buffReader, id) as types_Func?

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
    res.method = mapDec[readType]?.invoke(buffReader, id) as ssa_selection?

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
    res.Signature = mapDec[readType]?.invoke(buffReader, id) as types_Signature?

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
    res.pos = mapDec[readType]?.invoke(buffReader, id) as Long?

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
    res.Synthetic = mapDec[readType]?.invoke(buffReader, id) as String?

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
    res.syntax = mapDec[readType]?.invoke(buffReader, id) as Any?

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
    res.info = mapDec[readType]?.invoke(buffReader, id) as types_Info?

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
    res.goversion = mapDec[readType]?.invoke(buffReader, id) as String?

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
    res.Pkg = mapDec[readType]?.invoke(buffReader, id) as ssa_Package?

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
    res.Prog = mapDec[readType]?.invoke(buffReader, id) as ssa_Program?

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
    res.Params = mapDec[readType]?.invoke(buffReader, id) as List<ssa_Parameter>?

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
    res.FreeVars = mapDec[readType]?.invoke(buffReader, id) as List<ssa_FreeVar>?

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
    res.Locals = mapDec[readType]?.invoke(buffReader, id) as List<ssa_Alloc>?

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
    res.Blocks = mapDec[readType]?.invoke(buffReader, id) as List<ssa_BasicBlock>?

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
    res.Recover = mapDec[readType]?.invoke(buffReader, id) as ssa_BasicBlock?

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
    res.AnonFuncs = mapDec[readType]?.invoke(buffReader, id) as List<ssa_Function>?

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
    res.referrers = mapDec[readType]?.invoke(buffReader, id) as List<Any>?

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
    res.anonIdx = mapDec[readType]?.invoke(buffReader, id) as Long?

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
    res.typeparams = mapDec[readType]?.invoke(buffReader, id) as types_TypeParamList?

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
    res.typeargs = mapDec[readType]?.invoke(buffReader, id) as List<Any>?

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
    res.topLevelOrigin = mapDec[readType]?.invoke(buffReader, id) as ssa_Function?

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
    res.generic = mapDec[readType]?.invoke(buffReader, id) as ssa_generic?

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
    res.currentBlock = mapDec[readType]?.invoke(buffReader, id) as ssa_BasicBlock?

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
    res.vars = mapDec[readType]?.invoke(buffReader, id) as Map<types_Var, Any>?

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
    res.namedResults = mapDec[readType]?.invoke(buffReader, id) as List<ssa_Alloc>?

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
    res.targets = mapDec[readType]?.invoke(buffReader, id) as ssa_targets?

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
    res.lblocks = mapDec[readType]?.invoke(buffReader, id) as Map<types_Label, ssa_lblock>?

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
    res.subst = mapDec[readType]?.invoke(buffReader, id) as ssa_subster?

	buffReader.readLine()
	return res
}
