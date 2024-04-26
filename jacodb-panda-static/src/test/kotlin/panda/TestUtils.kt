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

package panda

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeFromStream
import org.jacodb.panda.staticvm.ir.PandaProgramIr
import org.jacodb.panda.staticvm.ir.dumpDot
import java.io.File

@OptIn(ExperimentalSerializationApi::class)
fun loadProgram(path: String): PandaProgramIr {
    val input = object {}::class.java.getResourceAsStream(path)
        ?: error("Could not find resource: $path")
    val program = PandaProgramIr.json.decodeFromStream<PandaProgramIr>(input)
    return program
}

object TestDot {
    @JvmStatic
    fun main(args: Array<String>) {
        val filePath = "sample.ir"
        val program = loadProgram("/$filePath")

        val path = "dump"
        val dotFile = File("$path.dot")
        program.dumpDot(dotFile)
        println("Generated DOT file: ${dotFile.absolutePath}")
        for (format in listOf("pdf")) {
            val formatFile = File("$path.$format")
            val p = Runtime.getRuntime().exec("dot -T$format $dotFile -o $formatFile")
            p.waitFor()
            print(p.inputStream.bufferedReader().readText())
            print(p.errorStream.bufferedReader().readText())
            println("Generated ${format.uppercase()} file: ${formatFile.absolutePath}")
        }
    }
}
