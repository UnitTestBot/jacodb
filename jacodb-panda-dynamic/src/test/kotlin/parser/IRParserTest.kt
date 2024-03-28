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
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

private val logger = mu.KotlinLogging.logger {}

class IRParserTest {
    private fun loadIR(fileName: String = "TypeMismatch"): IRParser {
        val sampleFilePath = javaClass.getResource("/samples/$fileName.json")?.path ?: ""
        return IRParser(sampleFilePath)
    }

    @Test
    fun getProject() {
        val irParser = loadIR()
        val pandaProject = irParser.getProject()
        assertNotNull(pandaProject)
    }

    @Test
    fun getProgramIR() {
        val irParser = loadIR()
        val programIR = irParser.getProgramIR()
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
        val irParser = loadIR()
        val programIR = irParser.getProgramIR()
        programIR.classes.forEach { cls ->
            cls.properties.forEach { property ->
                val pandaMethod = property.method.pandaMethod
                assertNotNull(pandaMethod.name)
                assertNotNull(pandaMethod.instructions)
                logger.info { "Panda method '${pandaMethod.name}', instructions: ${pandaMethod.instructions}" }

            }
        }
    }

    @Test
    fun getSetOfProgramOpcodes() {
        val irParser = loadIR()
        val programIR = irParser.getProgramIR()
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
        val irParser = loadIR()
        val programIR = irParser.getProgramIR()
        programIR.classes.forEach { cls ->
            cls.properties.forEach { property ->
                println(property)
            }
        }
    }

    @Test
    fun `test parser on TypeMismatch`() {
        val irParser = loadIR("TypeMismatch")
        val programIR = irParser.getProgramIR()
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
}
