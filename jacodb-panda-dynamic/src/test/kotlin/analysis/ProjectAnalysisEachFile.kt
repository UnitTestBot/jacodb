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

package analysis

import io.mockk.mockk
import org.jacodb.analysis.ifds.SingletonUnit
import org.jacodb.analysis.ifds.UnitResolver
import org.jacodb.analysis.taint.ForwardTaintFlowFunctions
import org.jacodb.analysis.taint.TaintManager
import org.jacodb.analysis.util.PandaTraits
import org.jacodb.panda.dynamic.api.PandaApplicationGraphImpl
import org.jacodb.panda.dynamic.api.PandaInst
import org.jacodb.panda.dynamic.api.PandaMethod
import org.jacodb.panda.dynamic.api.PandaProject
import org.jacodb.panda.dynamic.parser.IRParser
import org.jacodb.panda.dynamic.parser.Program
import org.jacodb.taint.configuration.Argument
import org.jacodb.taint.configuration.AssignMark
import org.jacodb.taint.configuration.ConstantTrue
import org.jacodb.taint.configuration.ContainsMark
import org.jacodb.taint.configuration.Result
import org.jacodb.taint.configuration.TaintConfigurationItem
import org.jacodb.taint.configuration.TaintMark
import org.jacodb.taint.configuration.TaintMethodSink
import org.jacodb.taint.configuration.TaintMethodSource
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.condition.EnabledIf
import parser.loadIr
import java.nio.file.Files
import java.nio.file.Paths
import java.util.stream.Collectors
import kotlin.time.Duration.Companion.seconds

private val logger = mu.KotlinLogging.logger {}

class ProjectAnalysisEachFile {
    companion object : PandaTraits {
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

        private fun loadProjectForSample(name: String): PandaProject {
            return load(name).getProject()
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
            if (filename == "base/account/AccountManager")
                runAnalysisOnAccountManager(filename)
            else
                runAnalysis(filename)
        } catch (e: Exception) {
            tsLinesFailed += currentFileLines
            throw e
        } finally {
            logger.info { "Processed $filename.ts with $currentFileLines lines" }
            logger.info { "Total lines processed: $tsLinesSuccess" }
            logger.info { "Failed lines: $tsLinesFailed" }
        }
    }

    private fun runAnalysisOnAccountManager(filename: String) {
        val project = loadProjectForSample(filename)
        val graph = PandaApplicationGraphImpl(project)
        val unitResolver = UnitResolver<PandaMethod> { SingletonUnit }
        val getConfigForMethod: ForwardTaintFlowFunctions<PandaMethod, PandaInst>.(PandaMethod) -> List<TaintConfigurationItem>? =
            { method ->
                val rules = buildList {
                    if (method.name == "taintSink") add(
                        TaintMethodSink(
                            method = mockk(),
                            cwe = listOf(),
                            ruleNote = "SINK",
                            condition = ContainsMark(position = Argument(0), mark = TaintMark("TAINT")),
                        )
                    )
                    if (method.name == "requestGet") add(
                        TaintMethodSource(
                            method = mockk(),
                            condition = ConstantTrue,
                            actionsAfter = listOf(AssignMark(position = Result, mark = TaintMark("TAINT")))
                        )
                    )
                }
                rules.ifEmpty { null }
            }
        val manager = TaintManager(
            graph = graph,
            unitResolver = unitResolver,
            getConfigForMethod = getConfigForMethod,
        )

        val methodNames = setOf(
            "getDeviceIdListWithCursor",
            "requestGet",
            "taintRun",
            "taintSink"
        )

        val methods = project.classes.flatMap { it.methods }.filter { it.name in methodNames }
        val sinks = manager.analyze(methods, timeout = 10.seconds)
        logger.warn { "Found sink in file $filename: $sinks" }
        assertTrue(sinks.isNotEmpty())
    }

    private fun runAnalysis(fileName: String) {
        val project = loadProjectForSample(fileName)
        val graph = PandaApplicationGraphImpl(project)
        val unitResolver = UnitResolver<PandaMethod> { SingletonUnit }
        val getConfigForMethod: ForwardTaintFlowFunctions<PandaMethod, PandaInst>.(PandaMethod) -> List<TaintConfigurationItem>? =
            { method ->
                val rules = emptyList<TaintMethodSink>()
                rules.ifEmpty { null }
            }
        val manager = TaintManager(
            graph = graph,
            unitResolver = unitResolver,
            getConfigForMethod = getConfigForMethod,
        )

        val methods = project.classes.flatMap { it.methods }
        val sinks = manager.analyze(methods, timeout = 5.seconds)
        assertTrue(sinks.isEmpty())
    }
}
