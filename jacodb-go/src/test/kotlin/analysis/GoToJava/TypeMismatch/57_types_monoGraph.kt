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
class types_monoGraph {

	var vertices: List<types_monoVertex>? = null
	var edges: List<types_monoEdge>? = null
	var canon: Map<types_TypeParam, types_TypeParam>? = null
	var nameIdx: Map<types_TypeName, Long>? = null
}

fun read_types_monoGraph(buffReader: BufferedReader, id: Int): types_monoGraph {
	val res = types_monoGraph()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id] as types_monoGraph
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
    res.vertices = mapDec[readType]?.invoke(buffReader, id) as List<types_monoVertex>?

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
    res.edges = mapDec[readType]?.invoke(buffReader, id) as List<types_monoEdge>?

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
    res.canon = mapDec[readType]?.invoke(buffReader, id) as Map<types_TypeParam, types_TypeParam>?

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
    res.nameIdx = mapDec[readType]?.invoke(buffReader, id) as Map<types_TypeName, Long>?

	buffReader.readLine()
	return res
}
