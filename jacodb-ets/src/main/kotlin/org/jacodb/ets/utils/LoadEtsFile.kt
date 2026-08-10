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
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipInputStream
import kotlin.io.path.Path
import kotlin.io.path.PathWalkOption
import kotlin.io.path.absolute
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.createTempFile
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.inputStream
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.outputStream
import kotlin.io.path.pathString
import kotlin.io.path.walk
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}

/**
 * Which frontend generates the EtsIR JSON.
 *
 * - [TS_FRONTEND] — the native TypeScript frontend bundled with its standard
 *   library declarations in the `jacodb-ets` JAR. Default.
 * - [ARKANALYZER] — the external ArkAnalyzer `serializeArkIR` script
 *   (requires the `ARKANALYZER_DIR` environment variable).
 *
 * The default can be overridden with the `ETS_IR_PROVIDER` environment variable
 * (`ts-frontend` or `arkanalyzer`).
 */
enum class EtsIrProvider {
    TS_FRONTEND,
    ARKANALYZER;

    companion object {
        private const val ENV_VAR_ETS_IR_PROVIDER = "ETS_IR_PROVIDER"

        fun default(): EtsIrProvider =
            when (System.getenv(ENV_VAR_ETS_IR_PROVIDER)?.trim()?.lowercase()) {
                null, "", "ts-frontend", "ts_frontend", "tsfrontend" -> TS_FRONTEND
                "arkanalyzer", "ark-analyzer", "ark_analyzer" -> ARKANALYZER
                else -> {
                    logger.warn { "Unknown $ENV_VAR_ETS_IR_PROVIDER value, falling back to TS_FRONTEND" }
                    TS_FRONTEND
                }
            }
    }
}

// ArkAnalyzer provider configuration:
private const val ENV_VAR_ARK_ANALYZER_DIR = "ARKANALYZER_DIR"
private const val DEFAULT_ARK_ANALYZER_DIR = "arkanalyzer"

private const val ENV_VAR_SERIALIZE_SCRIPT_PATH = "SERIALIZE_SCRIPT_PATH"
private const val DEFAULT_SERIALIZE_SCRIPT_PATH = "out/src/save/serializeArkIR.js"

// TS frontend provider configuration:
private const val ENV_VAR_ETS_FRONTEND_DIR = "ETS_FRONTEND_DIR"
private const val PROPERTY_ETS_FRONTEND_DIR = "ets.frontend.dir"
private const val DEFAULT_ETS_FRONTEND_DIR = "ts-frontend"

private const val ENV_VAR_ETS_FRONTEND_SCRIPT = "ETS_FRONTEND_SCRIPT"
private const val DEFAULT_ETS_FRONTEND_SCRIPT = "dist/index.js"
private const val BUNDLED_ETS_FRONTEND_RUNTIME = "/ets-frontend/runtime.zip"

private const val ENV_VAR_NODE_EXECUTABLE = "NODE_EXECUTABLE"
private const val DEFAULT_NODE_EXECUTABLE = "node"

private val extractedBundledFrontend: Path? by lazy {
    EtsIrProvider::class.java.getResourceAsStream(BUNDLED_ETS_FRONTEND_RUNTIME)?.use { input ->
        val runtimeDir = createTempDirectory("jacodb-ets-frontend-")
        runtimeDir.toFile().deleteOnExit()
        // `deleteOnExit` does not run on SIGKILL and cannot remove non-empty directories,
        // so drop the whole tree on a normal shutdown as well.
        Runtime.getRuntime().addShutdownHook(Thread { runtimeDir.toFile().deleteRecursively() })
        ZipInputStream(input).use { archive ->
            var entry = archive.nextEntry
            while (entry != null) {
                val target = runtimeDir.resolve(entry.name).normalize()
                require(target.startsWith(runtimeDir)) {
                    "Unsafe entry in bundled ts-frontend runtime: '${entry.name}'"
                }
                if (entry.isDirectory) {
                    target.createDirectories()
                    target.toFile().deleteOnExit()
                } else {
                    target.parent.createDirectories()
                    target.outputStream().use(archive::copyTo)
                    target.toFile().deleteOnExit()
                }
                archive.closeEntry()
                entry = archive.nextEntry
            }
        }
        runtimeDir.resolve("index.js").takeIf(Path::exists)
    }
}

// Walking a large project tree (node_modules included) costs seconds of pure I/O,
// and the same path is probed repeatedly by the `loadEts*AutoConvert` helpers.
//
// NB: the cache lives for the whole JVM lifetime and is never evicted, so adding an `.ets`
// file to an already-probed tree keeps the previously chosen provider. Pass `provider`
// explicitly (or call [clearDefaultProviderCache]) when a tree changes under a long-lived host.
private val defaultProviderCache = ConcurrentHashMap<Pair<String, Boolean>, EtsIrProvider>()

/** Drops the memoized [defaultProviderFor] decisions; intended for tests and long-lived hosts. */
fun clearDefaultProviderCache() {
    defaultProviderCache.clear()
}

/** ArkTS remains on the legacy provider; the native frontend owns TS/JS only. */
internal fun defaultProviderFor(path: Path, isProject: Boolean): EtsIrProvider =
    defaultProviderCache.computeIfAbsent(path.absolute().normalize().pathString to isProject) {
        val containsEts = if (isProject) {
            val projectRoot = path.toFile()
            path.exists() && projectRoot.walkTopDown()
                .onEnter { directory ->
                    directory == projectRoot ||
                        (directory.name != "node_modules" && !directory.name.startsWith("."))
                }
                .any { file -> file.isFile && file.extension.equals("ets", ignoreCase = true) }
        } else {
            path.extension.equals("ets", ignoreCase = true)
        }
        if (containsEts) {
            EtsIrProvider.ARKANALYZER
        } else {
            EtsIrProvider.default()
        }
    }

/** Location of the serializer script for the chosen [provider]. */
fun etsIrSerializerScript(provider: EtsIrProvider = EtsIrProvider.default()): Path =
    when (provider) {
        EtsIrProvider.ARKANALYZER -> {
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
            script
        }

        EtsIrProvider.TS_FRONTEND -> {
            val configuredDir = System.getenv(ENV_VAR_ETS_FRONTEND_DIR)
                ?: System.getProperty(PROPERTY_ETS_FRONTEND_DIR)
            val configuredScript = System.getenv(ENV_VAR_ETS_FRONTEND_SCRIPT)
            if (configuredDir != null || configuredScript != null) {
                resolveFrontendScript(
                    Path(configuredDir ?: DEFAULT_ETS_FRONTEND_DIR),
                    configuredScript ?: DEFAULT_ETS_FRONTEND_SCRIPT,
                )
            } else {
                extractedBundledFrontend
                    ?: resolveFrontendScript(Path(DEFAULT_ETS_FRONTEND_DIR), DEFAULT_ETS_FRONTEND_SCRIPT)
            }
        }
    }

private fun resolveFrontendScript(frontendDir: Path, scriptPath: String): Path {
    if (!frontendDir.exists()) {
        throw FileNotFoundException(
            "ts-frontend directory does not exist: '${frontendDir.absolute()}'. " +
                "The bundled frontend resource '$BUNDLED_ETS_FRONTEND_RUNTIME' is unavailable. " +
                "Set the '$ENV_VAR_ETS_FRONTEND_DIR' environment variable " +
                "(or the '$PROPERTY_ETS_FRONTEND_DIR' system property) to a frontend checkout."
        )
    }
    val script = frontendDir.resolve(scriptPath)
    if (!script.exists()) {
        throw FileNotFoundException(
            "Script file not found: '$script'. Did you forget to execute 'npm run build' in ts-frontend?"
        )
    }
    return script
}

class EtsIrGenerationException(message: String) : IllegalStateException(message)

/** Cap on the amount of process output embedded into [EtsIrGenerationException]. */
private const val MAX_REPORTED_OUTPUT_CHARS = 16 * 1024

private const val ENV_VAR_ETS_IR_GENERATION_TIMEOUT_SEC = "ETS_IR_GENERATION_TIMEOUT_SEC"

/**
 * Default generation timeout. Ten seconds is only enough for a single file;
 * project mode on a real project needs minutes, hence the larger default and
 * the `ETS_IR_GENERATION_TIMEOUT_SEC` override.
 */
fun defaultEtsIrGenerationTimeout(isProject: Boolean): Duration {
    val configured = System.getenv(ENV_VAR_ETS_IR_GENERATION_TIMEOUT_SEC)?.trim()?.toLongOrNull()
    if (configured != null && configured > 0) {
        return configured.seconds
    }
    return if (isProject) 10.minutes else 60.seconds
}

private fun String.truncateForReport(): String =
    if (length <= MAX_REPORTED_OUTPUT_CHARS) {
        this
    } else {
        take(MAX_REPORTED_OUTPUT_CHARS) + "\n... (truncated, ${length - MAX_REPORTED_OUTPUT_CHARS} more chars)"
    }

fun generateEtsIR(
    projectPath: Path,
    isProject: Boolean = false,
    loadEntrypoints: Boolean = true,
    useArkAnalyzerTypeInference: Int? = null,
    timeout: Duration? = defaultEtsIrGenerationTimeout(isProject),
    provider: EtsIrProvider = defaultProviderFor(projectPath, isProject),
): Path {
    val script = etsIrSerializerScript(provider)
    val node = System.getenv(ENV_VAR_NODE_EXECUTABLE) ?: DEFAULT_NODE_EXECUTABLE
    val output = if (isProject) {
        createTempDirectory(projectPath.nameWithoutExtension)
    } else {
        createTempFile(projectPath.nameWithoutExtension, suffix = ".json")
    }

    val cmd: List<String> = buildList {
        add(node)
        add(script.pathString)
        if (isProject) add("-p")
        if (loadEntrypoints) add("-e")
        if (useArkAnalyzerTypeInference != null) {
            // The legacy `serializeArkIR.js` lives outside this repository and its `--help`
            // does not even document `-t`, so the historical single-token form is kept for it;
            // the native frontend accepts both.
            if (provider == EtsIrProvider.ARKANALYZER) {
                add("-t $useArkAnalyzerTypeInference")
            } else {
                add("-t")
                add(useArkAnalyzerTypeInference.toString())
            }
        }
        add(projectPath.pathString)
        add(output.pathString)
        // Verbose mode logs a line per file; only ask for it when it can actually be seen.
        if (logger.isDebugEnabled) add("-v")
    }
    logger.debug { "Running EtsIR generation ($provider): ${cmd.joinToString(" ")}" }
    val res = ProcessUtil.run(cmd, timeout = timeout)
    val failure = when {
        res.isTimeout -> "EtsIR generation ($provider) timed out after $timeout"
        res.exitCode != 0 -> "EtsIR generation ($provider) failed with exit code ${res.exitCode}"
        else -> null
    }
    if (failure != null) {
        // Keep whatever has already been generated: on a partial failure (or a timeout
        // on a large project) the produced files are still useful for diagnostics.
        logger.error { "$failure\nCommand: ${cmd.joinToString(" ")}" }
        logger.error { "STDOUT:\n${res.stdout}" }
        logger.error { "STDERR:\n${res.stderr}" }
        logger.error { "Partial output is kept at '$output'" }
        throw EtsIrGenerationException(
            "$failure\nOutput: '$output'" +
                "\nSTDOUT:\n${res.stdout.truncateForReport()}" +
                "\nSTDERR:\n${res.stderr.truncateForReport()}"
        )
    }
    return output
}

/**
 * Generates EtsIR for an SDK tree (e.g. the OpenHarmony SDK).
 *
 * An SDK consists of declaration files only, which the native TS frontend
 * deliberately skips, so the legacy ArkAnalyzer provider is forced here.
 */
fun generateSdkIR(sdkPath: Path): Path = generateEtsIR(
    sdkPath,
    isProject = true,
    loadEntrypoints = false,
    useArkAnalyzerTypeInference = 0,
    provider = EtsIrProvider.ARKANALYZER,
)

fun loadEtsFileAutoConvert(
    path: Path,
    useArkAnalyzerTypeInference: Int? = 1,
    provider: EtsIrProvider = defaultProviderFor(path, isProject = false),
): EtsFile {
    val irFilePath = generateEtsIR(
        path,
        isProject = false,
        useArkAnalyzerTypeInference = useArkAnalyzerTypeInference,
        provider = provider,
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
    provider: EtsIrProvider = defaultProviderFor(projectPath, isProject = true),
): EtsScene {
    val irFolderPath = generateEtsIR(
        projectPath,
        isProject = true,
        loadEntrypoints = loadEntrypoints,
        useArkAnalyzerTypeInference = useArkAnalyzerTypeInference,
        provider = provider,
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

/**
 * Loads a single [EtsScene] from several already generated EtsIR trees:
 * [input] holds the project IR directories, [sdkPaths] the SDK ones.
 */
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
