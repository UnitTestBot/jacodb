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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

private val logger = mu.KotlinLogging.logger {}

class IRParserSamplesTest {

    companion object {
        private const val SAMPLE_NAME = "MethodCollision"

        private fun load(name: String): IRParser {
            return loadIr(
                filePath = "/samples/$name.json",
            )
        }
    }

    @Test
    fun getProject() {
        val parser = load(SAMPLE_NAME)
        val project = parser.getProject()
        assertNotNull(project)
    }

    @Test
    fun getProgramIR() {
        val parser = load(SAMPLE_NAME)
        val program = parser.getProgram()
        val classes = program.classes
        logger.info { "Classes name: ${classes.joinToString { it.name }}" }
        logger.info {
            "Methods name: ${
                classes
                    .flatMap { it.properties }
                    .joinToString { it.name }
            }"
        }
        assertNotNull(program)
    }

    @Test
    fun getPandaMethods() {
        val parser = load(SAMPLE_NAME)
        val program = parser.getProgram()
        program.classes.forEach { cls ->
            cls.properties.forEach { property ->
                val pandaMethod = property.method.pandaMethod
                assertNotNull(pandaMethod.name)
                assertNotNull(pandaMethod.instructions)
                logger.info { "Panda method '$pandaMethod', instructions: ${pandaMethod.instructions}" }

            }
        }
    }

    @Test
    fun printPandaInstructions() {
        val parser = load(SAMPLE_NAME)
        val project = parser.getProject()
        project.classes.forEach { cls ->
            cls.methods.forEach { pandaMethod ->
                assertNotNull(pandaMethod.name)
                assertNotNull(pandaMethod.instructions)
                logger.info { "Panda method '$pandaMethod'" }
                pandaMethod.instructions.forEach { inst ->
                    logger.info { "${inst.location.index}. $inst" }
                }
                logger.info { "-------------------------------------" }
            }
        }
    }

    @Test
    fun getSetOfProgramOpcodes() {
        val parser = load(SAMPLE_NAME)
        val program = parser.getProgram()
        val opcodes = program.classes.asSequence()
            .flatMap { it.properties }
            .flatMap { it.method.basicBlocks }
            .flatMap { it.insts }
            .map { it.opcode }
            .toSortedSet()
        assertNotNull(opcodes)
    }

    @Test
    fun printMethodsInstructions() {
        val parser = load(SAMPLE_NAME)
        val program = parser.getProgram()
        program.classes.forEach { cls ->
            cls.properties.forEach { property ->
                println(property)
            }
        }
    }

    @Test
    fun `test parser on TypeMismatch`() {
        val parser = load("TypeMismatch")
        val program = parser.getProgram()
        program.classes.forEach { cls ->
            cls.properties.forEach { property ->
                val pandaMethod = property.method.pandaMethod
                assertNotNull(pandaMethod.name)
                assertNotNull(pandaMethod.instructions)
                when (pandaMethod.name) {
                    "add" -> {
                        assertEquals(10, pandaMethod.instructions.size)
                        assertEquals(5, pandaMethod.blocks.size)
                    }

                    "main" -> {
                        assertEquals(6, pandaMethod.instructions.size)
                        assertEquals(3, pandaMethod.blocks.size)
                    }
                }
            }
        }
    }
}
