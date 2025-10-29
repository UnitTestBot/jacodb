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
import org.jacodb.ets.dto.toEtsFile
import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.model.EtsScene
import java.io.FileNotFoundException
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.PathWalkOption
import kotlin.io.path.absolute
import kotlin.io.path.createTempDirectory
import kotlin.io.path.createTempFile
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.inputStream
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.pathString
import kotlin.io.path.walk
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}

private const val ENV_VAR_ARK_ANALYZER_DIR = "ARKANALYZER_DIR"
private const val DEFAULT_ARK_ANALYZER_DIR = "arkanalyzer"

private const val ENV_VAR_SERIALIZE_SCRIPT_PATH = "SERIALIZE_SCRIPT_PATH"
private const val DEFAULT_SERIALIZE_SCRIPT_PATH = "out/src/save/json/serializeArkIR.js"

private const val ENV_VAR_NODE_EXECUTABLE = "NODE_EXECUTABLE"
private const val DEFAULT_NODE_EXECUTABLE = "node"

fun generateEtsIR(
    projectPath: Path,
    isProject: Boolean = false,
    loadEntrypoints: Boolean = true,
    useArkAnalyzerTypeInference: Int? = null,
    timeout: Duration? = 10.seconds,
): Path {
    val arkAnalyzerDir = Path(System.getenv(ENV_VAR_ARK_ANALYZER_DIR) ?: DEFAULT_ARK_ANALYZER_DIR)
    if (!arkAnalyzerDir.exists()) {
        throw FileNotFoundException(
            "ArkAnalyzer directory does not exist: '${arkAnalyzerDir.absolute()}'. " +
                "Did you forget to set the '$ENV_VAR_ARK_ANALYZER_DIR' environment variable? " +
                "Current value is '${System.getenv(ENV_VAR_ARK_ANALYZER_DIR)}', " +
                "current dir is '${Path("").toAbsolutePath()}'."
        )
    }

    val scriptPath = System.getenv(ENV_VAR_SERIALIZE_SCRIPT_PATH) ?: DEFAULT_SERIALIZE_SCRIPT_PATH
    val script = arkAnalyzerDir.resolve(scriptPath)
    if (!script.exists()) {
        throw FileNotFoundException(
            "Script file not found: '$script'. " +
                "Did you forget to execute 'npm run build' in the arkanalyzer project?"
        )
    }

    val node = System.getenv(ENV_VAR_NODE_EXECUTABLE) ?: DEFAULT_NODE_EXECUTABLE
    val output = if (isProject) {
        createTempDirectory(projectPath.nameWithoutExtension)
    } else {
        createTempFile(projectPath.nameWithoutExtension, suffix = ".json")
    }

    val cmd = listOfNotNull(
        node,
        script.pathString,
        if (isProject) "-p" else null,
        if (loadEntrypoints) "-e" else null,
        useArkAnalyzerTypeInference?.let { "-t $it" },
        projectPath.pathString,
        output.pathString,
        "-v",
    )
    val res = ProcessUtil.run(cmd, timeout = timeout)
    if (res.exitCode != 0) {
        logger.error { "ARKANALYZER failed with exit code ${res.exitCode}" }
        logger.error { "STDOUT:\n${res.stdout}" }
        logger.error { "STDERR:\n${res.stderr}" }
    } else if (res.isTimeout) {
        logger.error { "ARKANALYZER timed out after $timeout" }
        logger.error { "STDOUT:\n${res.stdout}" }
        logger.error { "STDERR:\n${res.stderr}" }
    }
    return output
}

fun generateSdkIR(sdkPath: Path): Path = generateEtsIR(
    sdkPath,
    isProject = true,
    loadEntrypoints = false,
    useArkAnalyzerTypeInference = 0,
)

fun loadEtsFileAutoConvert(
    path: Path,
    useArkAnalyzerTypeInference: Int? = 1,
): EtsFile {
    val irFilePath = generateEtsIR(
        path,
        isProject = false,
        loadEntrypoints = false,
        useArkAnalyzerTypeInference = useArkAnalyzerTypeInference,
    )
    irFilePath.inputStream().use { stream ->
        val etsFileDto = EtsFileDto.loadFromJson(stream)
        return etsFileDto.toEtsFile()
    }
}

fun loadEtsProjectAutoConvert(
    projectPath: Path,
    sdkIRPath: Path? = null,
    loadEntrypoints: Boolean = false,
    useArkAnalyzerTypeInference: Int? = 1,
): EtsScene {
    val irFolderPath = generateEtsIR(
        projectPath,
        isProject = true,
        loadEntrypoints = loadEntrypoints,
        useArkAnalyzerTypeInference = useArkAnalyzerTypeInference,
    )

    return loadEtsProjectFromIR(irFolderPath, sdkIRPath)
}

fun loadEtsProjectFromIR(
    projectFilesPath: Path,
    sdkFilesPath: Path?,
): EtsScene {
    val projectFiles = walker(projectFilesPath)
    val sdkFiles = sdkFilesPath?.let { walker(it) }.orEmpty()

    return EtsScene(projectFiles, sdkFiles)
}

fun loadEtsProjectFromMultipleIR(input: List<Path>, sdkPaths: List<Path>): EtsScene {
    val projectFiles = input.flatMap(walker)
    val sdkFiles = sdkPaths.flatMap(walker)

    return EtsScene(projectFiles, sdkFiles)
}

private val walker = { dir: Path ->
    dir.walk(PathWalkOption.BREADTH_FIRST)
        .filter { it.extension == "json" }
        .map {
            it.inputStream().use { stream ->
                val etsFileDto = EtsFileDto.loadFromJson(stream)
                etsFileDto.toEtsFile()
            }
        }
        .toList()
}
