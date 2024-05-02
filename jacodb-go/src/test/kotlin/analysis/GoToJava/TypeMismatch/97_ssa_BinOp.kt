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
class ssa_BinOp : ssaToJacoExpr, ssaToJacoValue {

	var register: ssa_register? = null
	var Op: Long? = null
	var X: Any? = null
	var Y: Any? = null

	override fun createJacoDBExpr(parent: GoMethod): GoBinaryExpr {
        val type = (register!!.typ!! as ssaToJacoType).createJacoDBType()
        val name = "t${register!!.num!!.toInt()}"
        val location = GoInstLocationImpl(
            register!!.anInstruction!!.block!!.Index!!.toInt(),
            register!!.pos!!.toInt(),
            parent,
        )

        when (Op!!) {
            12L -> return GoAddExpr(
                lhv = (X!! as ssaToJacoValue).createJacoDBValue(parent),
                rhv = (Y!! as ssaToJacoValue).createJacoDBValue(parent),
                type = type,
                name = name,
                location = location,
            )
            13L -> return GoSubExpr(
                lhv = (X!! as ssaToJacoValue).createJacoDBValue(parent),
                rhv = (Y!! as ssaToJacoValue).createJacoDBValue(parent),
                type = type,
                name = name,
                location = location,
            )
            14L -> return GoMulExpr(
                lhv = (X!! as ssaToJacoValue).createJacoDBValue(parent),
                rhv = (Y!! as ssaToJacoValue).createJacoDBValue(parent),
                type = type,
                name = name,
                location = location,
            )
            15L -> return GoDivExpr(
                lhv = (X!! as ssaToJacoValue).createJacoDBValue(parent),
                rhv = (Y!! as ssaToJacoValue).createJacoDBValue(parent),
                type = type,
                name = name,
                location = location,
            )
            16L -> return GoModExpr(
                lhv = (X!! as ssaToJacoValue).createJacoDBValue(parent),
                rhv = (Y!! as ssaToJacoValue).createJacoDBValue(parent),
                type = type,
                name = name,
                location = location,
            )
            17L -> return GoAndExpr(
                lhv = (X!! as ssaToJacoValue).createJacoDBValue(parent),
                rhv = (Y!! as ssaToJacoValue).createJacoDBValue(parent),
                type = type,
                name = name,
                location = location,
            )
            18L -> return GoOrExpr(
                lhv = (X!! as ssaToJacoValue).createJacoDBValue(parent),
                rhv = (Y!! as ssaToJacoValue).createJacoDBValue(parent),
                type = type,
                name = name,
                location = location,
            )
            19L -> return GoXorExpr(
                lhv = (X!! as ssaToJacoValue).createJacoDBValue(parent),
                rhv = (Y!! as ssaToJacoValue).createJacoDBValue(parent),
                type = type,
                name = name,
                location = location,
            )
            20L -> return GoShlExpr(
                lhv = (X!! as ssaToJacoValue).createJacoDBValue(parent),
                rhv = (Y!! as ssaToJacoValue).createJacoDBValue(parent),
                type = type,
                name = name,
                location = location,
            )
            21L -> return GoShrExpr(
                lhv = (X!! as ssaToJacoValue).createJacoDBValue(parent),
                rhv = (Y!! as ssaToJacoValue).createJacoDBValue(parent),
                type = type,
                name = name,
                location = location,
            )
            22L -> return GoAndNotExpr(
                lhv = (X!! as ssaToJacoValue).createJacoDBValue(parent),
                rhv = (Y!! as ssaToJacoValue).createJacoDBValue(parent),
                type = type,
                name = name,
                location = location,
            )
            39L -> return GoEqlExpr(
                lhv = (X!! as ssaToJacoValue).createJacoDBValue(parent),
                rhv = (Y!! as ssaToJacoValue).createJacoDBValue(parent),
                type = type,
                name = name,
                location = location,
            )
            44L -> return GoNeqExpr(
                lhv = (X!! as ssaToJacoValue).createJacoDBValue(parent),
                rhv = (Y!! as ssaToJacoValue).createJacoDBValue(parent),
                type = type,
                name = name,
                location = location,
            )
            40L -> return GoLssExpr(
                lhv = (X!! as ssaToJacoValue).createJacoDBValue(parent),
                rhv = (Y!! as ssaToJacoValue).createJacoDBValue(parent),
                type = type,
                name = name,
                location = location,
            )
            45L -> return GoLeqExpr(
                lhv = (X!! as ssaToJacoValue).createJacoDBValue(parent),
                rhv = (Y!! as ssaToJacoValue).createJacoDBValue(parent),
                type = type,
                name = name,
                location = location,
            )
            41L -> return GoGtrExpr(
                lhv = (X!! as ssaToJacoValue).createJacoDBValue(parent),
                rhv = (Y!! as ssaToJacoValue).createJacoDBValue(parent),
                type = type,
                name = name,
                location = location,
            )
            46L -> return GoGeqExpr(
                lhv = (X!! as ssaToJacoValue).createJacoDBValue(parent),
                rhv = (Y!! as ssaToJacoValue).createJacoDBValue(parent),
                type = type,
                name = name,
                location = location,
            )
            else -> error("unexpected BinOp ${Op!!}")
        }
    }

	override fun createJacoDBValue(parent: GoMethod): GoValue {
       return createJacoDBExpr(parent)
    }
}

fun read_ssa_BinOp(buffReader: BufferedReader, id: Int): ssa_BinOp {
	val res = ssa_BinOp()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id] as ssa_BinOp
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
    res.Op = mapDec[readType]?.invoke(buffReader, id) as Long?

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
    res.Y = mapDec[readType]?.invoke(buffReader, id) as Any?

	buffReader.readLine()
	return res
}
