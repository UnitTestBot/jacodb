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
import org.jacodb.panda.dynamic.parser.Program
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.condition.EnabledIf
import java.nio.file.Files
import java.nio.file.Paths
import java.util.stream.Collectors

private val logger = mu.KotlinLogging.logger {}

class IRParserProjectTest {
    companion object {
        private const val PROJECT_PATH = "/samples/project1"
        private const val BASE_PATH = "$PROJECT_PATH/entry/src/main/ets/"

        private fun load(name: String): IRParser {
            return loadIr(filePath = "$BASE_PATH$name.json")
        }

        private fun countFileLines(path: String): Long {
            val stream = object {}::class.java.getResourceAsStream(path) ?: error("Resource not found")
            stream.bufferedReader().use { reader ->
                return reader.lines().count()
            }
        }
    }

    private var tsLinesSuccess = 0L
    private var tsLinesFailed = 0L

    private fun projectAvailable(): Boolean {
        val resource = object {}::class.java.getResource(PROJECT_PATH)?.toURI()
        return resource != null && Files.exists(Paths.get(resource))
    }

    @EnabledIf("projectAvailable")
    @TestFactory
    fun processAllFiles(): List<DynamicTest> {
        val baseDirUrl = object {}::class.java.getResource(BASE_PATH)
        val baseDir = Paths.get(baseDirUrl?.toURI() ?: error("Resource not found"))
        return Files.walk(baseDir)
            .filter { it.toString().endsWith(".json") }
            .map { baseDir.relativize(it).toString().replace("\\", "/").substringBeforeLast('.') }
            .map { filename ->
                DynamicTest.dynamicTest("Test getProject on $filename") {
                    handleFile(filename)
                }
            }
            .collect(Collectors.toList())
    }

    private fun printPandaInstructions(program: Program) {
        program.classes.forEach { cls ->
            cls.properties.forEach { property ->
                val pandaMethod = property.method.pandaMethod
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

    private fun handleFile(filename: String) {
        val currentFileLines = countFileLines("$BASE_PATH$filename.ts")
        try {
            val parser = load(filename)
            val project = parser.getProject()
            assertNotNull(project)
            tsLinesSuccess += currentFileLines
            printPandaInstructions(parser.getProgram())
        } catch (e: Exception) {
            tsLinesFailed += currentFileLines
            throw e
        } finally {
            logger.info { "Processed $filename.ts with $currentFileLines lines" }
            logger.info { "Total lines processed: $tsLinesSuccess" }
            logger.info { "Failed lines: $tsLinesFailed" }
        }
    }
}
