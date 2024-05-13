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
class ssa_UnOp : ssaToJacoExpr, ssaToJacoValue {

	var register: ssa_register? = null
	var Op: Long? = null
	var X: Any? = null
	var CommaOk: Boolean? = null

	override fun createJacoDBExpr(parent: GoMethod): GoUnaryExpr {
        val type = (register!!.typ!! as ssaToJacoType).createJacoDBType()
        val name = "t${register!!.num!!.toInt()}"
        val location = GoInstLocationImpl(
            register!!.anInstruction!!.block!!.Index!!.toInt(),
            register!!.pos!!.toInt(),
            parent,
        )

        when (Op!!) {
            43L -> return GoUnNotExpr(
                value = (X!! as ssaToJacoValue).createJacoDBValue(parent),
                type = type,
                name = name,
                location = location,
            )
            13L -> return GoUnSubExpr(
                value = (X!! as ssaToJacoValue).createJacoDBValue(parent),
                name = name,
                type = type,
                location = location,
            )
            36L -> return GoUnArrowExpr(
                value = (X!! as ssaToJacoValue).createJacoDBValue(parent),
                type = type,
                commaOk = CommaOk ?: false,
                name = name,
                location = location,
            )
            14L -> return GoUnMulExpr(
                value = (X!! as ssaToJacoValue).createJacoDBValue(parent),
                type = type,
                name = name,
                location = location,
            )
            19L -> return GoUnXorExpr(
                value = (X!! as ssaToJacoValue).createJacoDBValue(parent),
                type = type,
                name = name,
                location = location,
            )
            else -> error("unexpected UnOp ${Op!!}")
        }
    }
	override fun createJacoDBValue(parent: GoMethod): GoValue {
		if (structToPtrMap.containsKey(this) && ptrToJacoMap.containsKey(structToPtrMap[this])) {
            return ptrToJacoMap[structToPtrMap[this]] as GoUnaryExpr
        }
        return createJacoDBExpr(parent)
    }

}

fun read_ssa_UnOp(buffReader: BufferedReader, id: Int): ssa_UnOp {
	val res = ssa_UnOp()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id] as ssa_UnOp
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
    res.CommaOk = mapDec[readType]?.invoke(buffReader, id) as Boolean?

	buffReader.readLine()
	return res
}
