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

import org.jacodb.panda.staticvm.cfg.toDot
import org.jacodb.panda.staticvm.classpath.PandaProject
import org.jacodb.panda.staticvm.ir.EtsStdlib
import org.jacodb.panda.staticvm.ir.PandaProgramIr
import org.jacodb.panda.staticvm.ir.dumpDot
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

internal object EtsStdlib {
    val STDLIB_FILE_PATH = EtsStdlib::class.java.getResource("stdlib.ir")

    fun stdlibAvailable(): Boolean {
        val resource = STDLIB_FILE_PATH?.toURI()
        return resource != null && Files.exists(Paths.get(resource))
    }
}

fun loadProgram(path: String): PandaProgramIr {
    val input = object {}::class.java.getResourceAsStream(path)
        ?: error("Could not find resource: $path")
    val program = PandaProgramIr.from(input)
    return program
}

object DumpIrToDot {
    @JvmStatic
    fun main(args: Array<String>) {
        val filePath = "try_catch_finally.abc.ir"
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

object DumpCFGToDot {
    @JvmStatic
    fun main(args: Array<String>) {
        val filePath = "try_catch_finally.abc.ir"
        val program = loadProgram("/$filePath")
        val cfg = PandaProject.fromProgramIr(program, withStdLib = true)
            .findMethod("ETSGLOBAL.main:void;")
            .flowGraph()

        val path = "cfg_dump"
        val dotFile = File("$path.dot")
        dotFile.writer().use { it.write(cfg.toDot()) }
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
