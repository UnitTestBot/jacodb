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

package parser

import org.jacodb.panda.dynamic.parser.IRParser
import org.jacodb.panda.dynamic.parser.TSParser
import org.jacodb.panda.dynamic.parser.dumpDot
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals

private val logger = mu.KotlinLogging.logger {}

class IRParserSamplesTest {

    companion object {
        private fun loadIR(fileName: String): IRParser {
            val sampleFilePath = this::class.java.getResource("/samples/$fileName.json")?.path ?: ""
            val sampleTSPath = this::class.java.getResource("/samples/$fileName.ts")?.toURI()
                ?: throw IllegalStateException()
            val tsParser = TSParser(sampleTSPath)
            val tsFunctions = tsParser.collectFunctions()
            return IRParser(sampleFilePath, tsFunctions)
        }
    }

    @Test
    fun getProject() {
        val irParser = loadIR("DataFlowSecurity")
        val pandaProject = irParser.getProject()
        assertNotNull(pandaProject)
    }

    @Test
    fun getProgramIR() {
        val irParser = loadIR("DataFlowSecurity")
        val programIR = irParser.getProgram()
        val classes = programIR.classes
        logger.info { "Classes name: ${classes.joinToString(separator = ", ") { it.name }}" }
        logger.info {
            "Methods name: ${
                classes
                    .flatMap { it.properties }
                    .joinToString(separator = ", ") { it.name }
            }"
        }
        assertNotNull(programIR)
    }

    @Test
    fun getPandaMethods() {
        val irParser = loadIR("DataFlowSecurity")
        val programIR = irParser.getProgram()
        programIR.classes.forEach { cls ->
            cls.properties.forEach { property ->
                val pandaMethod = property.method.pandaMethod
                assertNotNull(pandaMethod.name)
                assertNotNull(pandaMethod.instructions)
                logger.info { "Panda method '$pandaMethod', instructions: ${pandaMethod.instructions}" }

            }
        }
    }

    @Test
    fun getSetOfProgramOpcodes() {
        val irParser = loadIR("DataFlowSecurity")
        val programIR = irParser.getProgram()
        val opcodes = programIR.classes.asSequence()
            .flatMap { it.properties }
            .flatMap { it.method.basicBlocks }
            .flatMap { it.insts }
            .map { it.opcode }
            .toSortedSet()
        assertNotNull(opcodes)
    }

    @Test
    fun printMethodsInstructions() {
        val irParser = loadIR("DataFlowSecurity")
        val programIR = irParser.getProgram()
        programIR.classes.forEach { cls ->
            cls.properties.forEach { property ->
                println(property)
            }
        }
    }

    @Test
    fun `test parser on TypeMismatch`() {
        val irParser = loadIR("TypeMismatch")
        val programIR = irParser.getProgram()
        programIR.classes.forEach { cls ->
            cls.properties.forEach { property ->
                val pandaMethod = property.method.pandaMethod
                assertNotNull(pandaMethod.name)
                assertNotNull(pandaMethod.instructions)
                when (pandaMethod.name) {
                    "add" -> {
                        assertEquals(9, pandaMethod.instructions.size)
                        assertEquals(4, pandaMethod.blocks.size)
                    }

                    "main" -> {
                        assertEquals(3, pandaMethod.instructions.size)
                        assertEquals(2, pandaMethod.blocks.size)
                    }
                }
            }
        }
    }

    object TestDot {
        @JvmStatic
        fun main(args: Array<String>) {
            val parser = loadIR("x")
            val program = parser.getProgram()
            val project = parser.getProject()
            println(program)
            println(project)

            val dotFile = File("x")
            program.dumpDot(dotFile)
            for (format in listOf("pdf", "png")) {
                val p = Runtime.getRuntime().exec("dot -T$format -O $dotFile")
                p.waitFor()
                print(p.inputStream.bufferedReader().readText())
                print(p.errorStream.bufferedReader().readText())
            }
            dotFile.renameTo(dotFile.resolveSibling(dotFile.name + ".dot"))
            println("Generated dot file: ${dotFile.absolutePath}")
        }
    }
}
