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

fun readInteger(buffReader: BufferedReader, id: Int): Any {
    return buffReader.readLine().toLong()
}

fun readULong(buffReader: BufferedReader, id: Int): Any {
    return buffReader.readLine().toULong()
}

fun readString(buffReader: BufferedReader, id: Int): Any {
    return buffReader.readLine().drop(1).dropLast(1)
}

fun readBoolean(buffReader: BufferedReader, id: Int): Any {
    return buffReader.readLine() == "true"
}

fun readReal(buffReader: BufferedReader, id: Int): Any {
    return buffReader.readLine().toDouble()
}

fun readNil(buffReader: BufferedReader, id: Int): Any? {
    return null
}

fun readArray(buffReader: BufferedReader, id: Int): Any? {
    val res: MutableList<Any?> = mutableListOf()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id]
        }
        ptrMap[id] = res
    }
	var line = buffReader.readLine()
    while (line != "end") {
        var split: List<String>
        var id: Int
        split = line.split(" ")
        id = -1
        if (split.size > 1) {
            id = split[1].toInt()
        }
        res.add(mapDec[split[0]]?.invoke(buffReader, id))
        line = buffReader.readLine()
    }
    return res
}

fun readMap(buffReader: BufferedReader, id: Int): Any? {
    val res: MutableMap<Any?, Any?> = mutableMapOf()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id]
        }
        ptrMap[id] = res
    }
    var line = buffReader.readLine()
    while (line != "end") {
        var split: List<String>
        var id: Int
        split = line.split(" ")
        id = -1
        if (split.size > 1) {
            id = split[1].toInt()
        }
        val key = mapDec[split[0]]?.invoke(buffReader, id)
        line = buffReader.readLine()
        split = line.split(" ")
        id = -1
        if (split.size > 1) {
            id = split[1].toInt()
        }
        val value = mapDec[split[0]]?.invoke(buffReader, id)
        res[key] = value
        line = buffReader.readLine()
    }
    return res
}
