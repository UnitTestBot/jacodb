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

package org.jacodb.ets.utils

import mu.KotlinLogging
import org.jacodb.ets.dto.EtsFileDto
import org.jacodb.ets.dto.convertToEtsFile
import org.jacodb.ets.model.EtsFile
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.notExists
import kotlin.io.path.pathString

private val logger = KotlinLogging.logger {}

private const val ENV_VAR_ARK_ANALYZER_DIR = "ARKANALYZER_DIR"
private const val DEFAULT_ARK_ANALYZER_DIR = "arkanalyzer"

private const val ENV_VAR_SERIALIZE_SCRIPT_PATH = "SERIALIZE_SCRIPT_PATH"
private const val DEFAULT_SERIALIZE_SCRIPT_PATH = "out/src/save/serializeArkIR.js"

private const val ENV_VAR_NODE_EXECUTABLE = "NODE_EXECUTABLE"
private const val DEFAULT_NODE_EXECUTABLE = "node"

fun loadEtsFileAutoConvert(tsPath: String): EtsFile {
    val arkAnalyzerDir = Paths.get(System.getenv(ENV_VAR_ARK_ANALYZER_DIR) ?: DEFAULT_ARK_ANALYZER_DIR)
    if (!arkAnalyzerDir.exists()) {
        throw FileNotFoundException("ArkAnalyzer directory does not exist: '$arkAnalyzerDir'. Did you forget to set the '$ENV_VAR_ARK_ANALYZER_DIR' environment variable? Current value is '${System.getenv(ENV_VAR_ARK_ANALYZER_DIR)}'.")
    }

    val scriptPath = System.getenv(ENV_VAR_SERIALIZE_SCRIPT_PATH) ?: DEFAULT_SERIALIZE_SCRIPT_PATH
    val script = arkAnalyzerDir.resolve(scriptPath)
    if (!script.exists()) {
        throw FileNotFoundException("Script file not found: '$script'. Did you forget to execute 'npm run build' in the arkanalyzer project?")
    }

    val node = System.getenv(ENV_VAR_NODE_EXECUTABLE) ?: DEFAULT_NODE_EXECUTABLE
    val output = kotlin.io.path.createTempFile(prefix = File(tsPath).nameWithoutExtension + "_", suffix = ".json")
    val cmd: List<String> = listOf(
        node,
        script.pathString,
        tsPath,
        output.pathString,
    )
    logger.info { "Running: '${cmd.joinToString(" ")}'" }
    val process = ProcessBuilder(cmd).start()
    val ok = process.waitFor(1, TimeUnit.MINUTES)

    val stdout = process.inputStream.bufferedReader().readText().trim()
    if (stdout.isNotBlank()) {
        logger.info { "STDOUT:\n$stdout" }
    }
    val stderr = process.errorStream.bufferedReader().readText().trim()
    if (stderr.isNotBlank()) {
        logger.info { "STDERR:\n$stderr" }
    }

    if (!ok) {
        logger.info { "Timeout!" }
        process.destroy()
    }

    output.inputStream().use { stream ->
        val etsFileDto = EtsFileDto.loadFromJson(stream)
        return convertToEtsFile(etsFileDto)
    }
}
