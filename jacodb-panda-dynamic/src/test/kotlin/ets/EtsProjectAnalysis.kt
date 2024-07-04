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

package ets

import ets.utils.getConfigForMethod
import ets.utils.loadRules
import org.jacodb.analysis.ifds.SingletonUnit
import org.jacodb.analysis.ifds.UnitResolver
import org.jacodb.analysis.taint.TaintManager
import org.jacodb.analysis.taint.TaintVulnerability
import org.jacodb.analysis.util.EtsTraits
import org.jacodb.analysis.util.getPathEdges
import org.jacodb.panda.dynamic.ets.base.EtsStmt
import org.jacodb.panda.dynamic.ets.dto.EtsFileDto
import org.jacodb.panda.dynamic.ets.dto.convertToEtsFile
import org.jacodb.panda.dynamic.ets.graph.EtsApplicationGraph
import org.jacodb.panda.dynamic.ets.model.EtsFile
import org.jacodb.panda.dynamic.ets.model.EtsMethod
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private val logger = mu.KotlinLogging.logger {}

class EtsProjectAnalysis {
    private var tsLinesSuccess = 0L
    private var tsLinesFailed = 0L
    private var analysisTime: Duration = Duration.ZERO
    private var totalPathEdges = 0
    private var totalSinks: MutableList<TaintVulnerability<EtsStmt>> = mutableListOf()

    companion object : EtsTraits {
        private const val SOURCE_PROJECT_PATH = "/source/project1"
        private const val START_PATH = "/entry/src/main/ets/"
        const val PROJECT_PATH = "/ir/project1"
        const val BASE_PATH = PROJECT_PATH + START_PATH
        const val SOURCE_BASE_PATH = SOURCE_PROJECT_PATH + START_PATH

        private fun loadProjectForSample(programName: String): EtsFile {
            val jsonFile = "$BASE_PATH$programName.json"
            val stream = object {}::class.java.getResourceAsStream(jsonFile)
                ?: error("Resource not found: $jsonFile")
            val etsFileDto = EtsFileDto.loadFromJson(stream)
            return convertToEtsFile(etsFileDto)
        }

        private fun countFileLines(path: String): Long {
            val stream = object {}::class.java.getResourceAsStream(path) ?: error("Resource not found: $path")
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
        logger.info { "Total files processed: ${tsLinesSuccess + tsLinesFailed}" }
        logger.info { "Successfully processed lines: $tsLinesSuccess" }
        logger.info { "Failed lines: $tsLinesFailed" }
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
        val fileLines = countFileLines("$SOURCE_BASE_PATH$filename.ts")
        try {
            logger.info { "Processing '$filename'" }
            val project = loadProjectForSample(filename)
            val startTime = System.currentTimeMillis()
            runAnalysis(project)
            val endTime = System.currentTimeMillis()
            analysisTime += (endTime - startTime).milliseconds
            tsLinesSuccess += fileLines
        } catch (e: Exception) {
            logger.warn { "Failed to process '$filename': $e" }
            logger.warn { e.stackTraceToString() }
            tsLinesFailed += fileLines
        }
    }

    private fun runAnalysis(project: EtsFile) {
        val graph = EtsApplicationGraph(project)
        val unitResolver = UnitResolver<EtsMethod> { SingletonUnit }
        val manager = TaintManager(
            graph = graph,
            unitResolver = unitResolver,
            getConfigForMethod = { method -> getConfigForMethod(method, rules) },
        )
        val methods = project.classes.flatMap { it.methods }
        val sinks = manager.analyze(methods, timeout = 10.seconds)
        totalPathEdges += manager.runnerForUnit.values.sumOf { it.getPathEdges().size }
        totalSinks += sinks
    }
}
