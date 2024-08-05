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
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.pathString
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}

private const val ENV_VAR_ARK_ANALYZER_DIR = "ARKANALYZER_DIR"
private const val DEFAULT_ARK_ANALYZER_DIR = "arkanalyzer"

private const val ENV_VAR_SERIALIZE_SCRIPT_PATH = "SERIALIZE_SCRIPT_PATH"
private const val DEFAULT_SERIALIZE_SCRIPT_PATH = "out/src/save/serializeArkIR.js"

private const val ENV_VAR_NODE_EXECUTABLE = "NODE_EXECUTABLE"
private const val DEFAULT_NODE_EXECUTABLE = "node"

fun generateEtsFileIR(tsPath: String): Path {
    val arkAnalyzerDir = Paths.get(System.getenv(ENV_VAR_ARK_ANALYZER_DIR) ?: DEFAULT_ARK_ANALYZER_DIR)
    if (!arkAnalyzerDir.exists()) {
        throw FileNotFoundException("ArkAnalyzer directory does not exist: '$arkAnalyzerDir'. Did you forget to set the '$ENV_VAR_ARK_ANALYZER_DIR' environment variable? Current value is '${System.getenv(ENV_VAR_ARK_ANALYZER_DIR)}', current dir is '${Paths.get("").toAbsolutePath()}'.")
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
    runProcess(cmd, 60.seconds)
    return output
}

fun loadEtsFileAutoConvert(tsPath: String): EtsFile {
    val irFilePath = generateEtsFileIR(tsPath)
    irFilePath.inputStream().use { stream ->
        val etsFileDto = EtsFileDto.loadFromJson(stream)
        val etsFile = convertToEtsFile(etsFileDto)
        return etsFile
    }
}

fun loadEtsFileAutoConvertWithDot(tsPath: String, dotDir: String): EtsFile {
    val irFilePath = generateEtsFileIR(tsPath)
    irFilePath.inputStream().use { stream ->
        val etsFileDto = EtsFileDto.loadFromJson(stream)
        val etsFile = convertToEtsFile(etsFileDto)
        val etsFileDtoDotFileName = "$dotDir/${tsPath.substringAfterLast("/")}".replace(".ts", "Dto.dot")
        val etsFileDotFileName = "$dotDir/${tsPath.substringAfterLast("/")}".replace(".ts", ".dot")
        render(etsFileDtoDotFileName) { file ->
            etsFileDto.dumpDot(file)
        }
        render(etsFileDotFileName) { file ->
            etsFile.dumpDot(file)
        }
        return etsFile
    }
}
