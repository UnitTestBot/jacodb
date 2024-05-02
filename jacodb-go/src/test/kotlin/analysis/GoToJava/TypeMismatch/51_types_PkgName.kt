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
class types_PkgName {

	var Object: analysis.GoToJava.TypeMismatch.types_object? = null
	var imported: analysis.GoToJava.TypeMismatch.types_Package? = null
	var used: Boolean? = null
}

fun read_types_PkgName(buffReader: BufferedReader, id: Int): analysis.GoToJava.TypeMismatch.types_PkgName {
	val res = analysis.GoToJava.TypeMismatch.types_PkgName()
    if (id != -1) {
        if (analysis.GoToJava.TypeMismatch.ptrMap.containsKey(id)) {
            return analysis.GoToJava.TypeMismatch.ptrMap[id] as analysis.GoToJava.TypeMismatch.types_PkgName
        }
        analysis.GoToJava.TypeMismatch.ptrMap[id] = res
		analysis.GoToJava.TypeMismatch.structToPtrMap[res] = id
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
    res.Object = analysis.GoToJava.TypeMismatch.mapDec[readType]?.invoke(buffReader, id) as analysis.GoToJava.TypeMismatch.types_object?

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
    res.imported = analysis.GoToJava.TypeMismatch.mapDec[readType]?.invoke(buffReader, id) as analysis.GoToJava.TypeMismatch.types_Package?

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
    res.used = analysis.GoToJava.TypeMismatch.mapDec[readType]?.invoke(buffReader, id) as Boolean?

	buffReader.readLine()
	return res
}
