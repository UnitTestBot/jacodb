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

import org.jacodb.analysis.ifds.SingletonUnit
import org.jacodb.analysis.ifds.UnitResolver
import org.jacodb.analysis.taint.TaintManager
import org.jacodb.analysis.taint.TaintVulnerability
import org.jacodb.analysis.util.PandaTraits
import org.jacodb.analysis.util.getPathEdges
import org.jacodb.panda.dynamic.api.PandaApplicationGraphImpl
import org.jacodb.panda.dynamic.api.PandaInst
import org.jacodb.panda.dynamic.api.PandaMethod
import org.jacodb.panda.dynamic.api.PandaProject
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import parser.getConfigForMethod
import parser.loadIr
import parser.loadRules
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private val logger = mu.KotlinLogging.logger {}

class ProjectAnalysis {
    private var tsLinesSuccess = 0L
    private var tsLinesFailed = 0L
    private var analysisTime: Duration = Duration.ZERO
    private var totalPathEdges = 0
    private var totalSinks: MutableList<TaintVulnerability<PandaInst>> = mutableListOf()

    companion object : PandaTraits {
        const val PROJECT_PATH = "/samples/project1"
        const val BASE_PATH = "$PROJECT_PATH/entry/src/main/ets/"

        private fun loadProjectForSample(programName: String): PandaProject {
            val parser = loadIr("$BASE_PATH$programName.json")
            val project = parser.getProject()
            return project
        }

        private fun countFileLines(path: String): Long {
            val stream = object {}::class.java.getResourceAsStream(path) ?: error("Resource not found")
            stream.bufferedReader().use { reader ->
                return reader.lines().count()
            }
        }

        val rules = loadRules("config1.json")
    }

    private fun projectAvailable(): Boolean {
        val resource = object {}::class.java.getResource(PROJECT_PATH)?.toURI()
        return resource != null && Files.exists(Paths.get(resource))
    }

    @Disabled
    @EnabledIf("projectAvailable")
    @Test
    fun processAllFiles() {
        val baseDirUrl = object {}::class.java.getResource(BASE_PATH)
        val baseDir = Paths.get(baseDirUrl?.toURI() ?: error("Resource not found"))
        Files.walk(baseDir)
            .filter { it.toString().endsWith(".json") }
            .map { baseDir.relativize(it).toString().replace("\\", "/").substringBeforeLast('.') }
            .forEach { filename ->
                handleFile(filename)
            }
        makeReport()
    }

    private fun makeReport() {
        logger.info { "Analysis Report On $PROJECT_PATH" }
        logger.info { "====================" }
        // logger.info { "Total files processed: ${tsLinesSuccess + tsLinesFailed}" }
        logger.info { "Successfully processed lines: $tsLinesSuccess" }
        // logger.info { "Failed lines: $tsLinesFailed" }
        logger.info { "Total analysis time: $analysisTime" }
        logger.info { "Total path edges: $totalPathEdges" }
        logger.info { "Found sinks: ${totalSinks.size}" }

        if (totalSinks.isNotEmpty()) {
            totalSinks.forEachIndexed { idx, sink ->
                logger.info {
                    """Detailed Sink Information:
                |
                |Sink ID: $idx
                |Statement: ${sink.sink.statement}
                |Fact: ${sink.sink.fact}
                |Condition: ${sink.rule?.condition}
                |
                """.trimMargin()
                }
            }
        } else {
            logger.info { "No sinks found." }
        }
        logger.info { "====================" }
        logger.info { "End of report" }
    }

    private fun handleFile(filename: String) {
        // This files contain critical bugs
        val banFiles: List<String> = listOf(
            "base/sync/utils/SyncUtil"
        )
        val fileLines = countFileLines("$BASE_PATH$filename.ts")
        try {
            if (filename in banFiles) return
            logger.info { "Processing '$filename'" }
            val project = loadProjectForSample(filename)
            val startTime = System.currentTimeMillis()
            runAnalysis(project, filename)
            val endTime = System.currentTimeMillis()
            analysisTime += (endTime - startTime).milliseconds
            tsLinesSuccess += fileLines
        } catch (e: Exception) {
            // logger.info { "Failed to process '$filename': $e" }
            tsLinesFailed += fileLines
        }
    }

    private fun runAnalysis(project: PandaProject, filename: String) {
        val graph = PandaApplicationGraphImpl(project)
        val unitResolver = UnitResolver<PandaMethod> { SingletonUnit }
        val manager = TaintManager(
            graph = graph,
            unitResolver = unitResolver,
            getConfigForMethod = { method -> getConfigForMethod(method, rules) },
        )

        var methods = project.classes.flatMap { it.methods }
        if (filename == "base/account/AccountManager") {
            val methodNames = setOf(
                "getDeviceIdListWithCursor",
                "requestGet",
                "taintRun",
                "taintSink"
            )
            methods = methods.filter { it.name in methodNames }
        }
        val sinks = manager.analyze(methods, timeout = 10.seconds)
        totalPathEdges += manager.runnerForUnit.values.sumOf { it.getPathEdges().size }
        totalSinks += sinks
    }
}
