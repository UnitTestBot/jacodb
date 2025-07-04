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
import org.jacodb.ets.dto.FileDto
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
import kotlin.io.path.relativeTo
import kotlin.io.path.walk
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}

private const val ENV_VAR_ARK_ANALYZER_DIR = "ARKANALYZER_DIR"
private const val DEFAULT_ARK_ANALYZER_DIR = "arkanalyzer"

private const val ENV_VAR_SERIALIZE_SCRIPT_PATH = "SERIALIZE_SCRIPT_PATH"
private const val DEFAULT_SERIALIZE_SCRIPT_PATH = "out/src/save/serializeArkIR.js"

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

    logger.info { "Generating IR for '$projectPath'..." }
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
        useArkAnalyzerTypeInference = useArkAnalyzerTypeInference,
    )
    irFilePath.inputStream().use { stream ->
        val fileDto = FileDto.loadFromJson(stream)
        return fileDto.toEtsFile()
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
                val fileDto = FileDto.loadFromJson(stream)
                fileDto.toEtsFile()
            }
        }
        .toList()
}

/**
 * Load an [FileDto] from a resource file.
 *
 * For example, `resources/ets/sample.json` can be loaded with:
 * ```
 * val dto: FileDto = loadFileDtoFromResource("/ets/sample.json")
 * ```
 */
fun loadFileDtoFromResource(jsonPath: String): FileDto {
    logger.debug { "Loading EtsIR from resource: '$jsonPath'" }
    require(jsonPath.endsWith(".json")) { "File must have a '.json' extension: '$jsonPath'" }
    getResourceStream(jsonPath).use { stream ->
        return FileDto.loadFromJson(stream)
    }
}

/**
 * Load an [EtsFile] from a resource file.
 *
 * For example, `resources/ets/sample.json` can be loaded with:
 * ```
 * val file: EtsFile = loadEtsFileFromResource("/ets/sample.json")
 * ```
 */
fun loadEtsFileFromResource(jsonPath: String): EtsFile {
    val fileDto = loadFileDtoFromResource(jsonPath)
    return fileDto.toEtsFile()
}

/**
 * Load multiple [EtsFile]s from a resource directory.
 *
 * For example, all files in `resources/project/` can be loaded with:
 * ```
 * val files: Sequence<EtsFile> = loadMultipleEtsFilesFromResourceDirectory("/project")
 * ```
 */
fun loadMultipleEtsFilesFromResourceDirectory(dirPath: String): Sequence<EtsFile> {
    val rootPath = getResourcePath(dirPath)
    return rootPath.walk().filter { it.extension == "json" }.map { path ->
        loadEtsFileFromResource("$dirPath/${path.relativeTo(rootPath)}")
    }
}

fun loadMultipleEtsFilesFromMultipleResourceDirectories(
    dirPaths: List<String>,
): Sequence<EtsFile> {
    return dirPaths.asSequence().flatMap { loadMultipleEtsFilesFromResourceDirectory(it) }
}

fun loadEtsProjectFromResources(
    modules: List<String>,
    prefix: String,
): EtsScene {
    logger.info { "Loading project with ${modules.size} modules $modules from '$prefix/<module>'" }
    val dirPaths = modules.map { "$prefix/$it" }
    val files = loadMultipleEtsFilesFromMultipleResourceDirectories(dirPaths).toList()
    logger.info { "Loaded ${files.size} files" }
    return EtsScene(files, sdkFiles = emptyList())
}

/**
 * Load an [FileDto] from a file.
 *
 * For example, `data/sample.json` can be loaded with:
 * ```
 * val dto: FileDto = loadFileDto(Path("data/sample.json"))
 * ```
 */
fun loadFileDto(path: Path): FileDto {
    require(path.extension == "json") { "File must have a '.json' extension: $path" }
    path.inputStream().use { stream ->
        return FileDto.loadFromJson(stream)
    }
}

/**
 * Load an [EtsFile] from a file.
 *
 * For example, `data/sample.json` can be loaded with:
 * ```
 * val file: EtsFile = loadEtsFile(Path("data/sample.json"))
 * ```
 */
fun loadEtsFile(path: Path): EtsFile {
    val fileDto = loadFileDto(path)
    return fileDto.toEtsFile()
}

/**
 * Load multiple [EtsFile]s from a directory.
 *
 * For example, all files in `data` can be loaded with:
 * ```
 * val files: Sequence<EtsFile> = loadMultipleEtsFilesFromDirectory(Path("data"))
 * ```
 */
fun loadMultipleEtsFilesFromDirectory(dirPath: Path): Sequence<EtsFile> {
    return dirPath.walk().filter { it.extension == "json" }.map { loadEtsFile(it) }
}
