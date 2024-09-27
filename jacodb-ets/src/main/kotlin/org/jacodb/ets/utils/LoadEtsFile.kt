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

import org.jacodb.ets.dto.EtsFileDto
import org.jacodb.ets.dto.convertToEtsFile
import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.model.EtsScene
import java.io.FileNotFoundException
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.absolute
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.pathString
import kotlin.io.path.walk
import kotlin.time.Duration.Companion.seconds

private const val ENV_VAR_ARK_ANALYZER_DIR = "ARKANALYZER_DIR"
private const val DEFAULT_ARK_ANALYZER_DIR = "arkanalyzer"

private const val ENV_VAR_SERIALIZE_SCRIPT_PATH = "SERIALIZE_SCRIPT_PATH"
private const val DEFAULT_SERIALIZE_SCRIPT_PATH = "out/src/save/serializeArkIR.js"

private const val ENV_VAR_NODE_EXECUTABLE = "NODE_EXECUTABLE"
private const val DEFAULT_NODE_EXECUTABLE = "node"

fun generateEtsIR(path: Path, isProject: Boolean = false): Path {
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
        createTempDirectory(prefix = path.nameWithoutExtension)
    } else {
        kotlin.io.path.createTempFile(prefix = path.nameWithoutExtension, suffix = ".json")
    }

    val cmd = listOfNotNull(
        node,
        script.pathString,
        if (isProject) "-p" else null,
        path.pathString,
        output.pathString,
    )
    runProcess(cmd, 10.seconds)
    return output
}

fun loadEtsFileAutoConvert(path: Path): EtsFile {
    val irFilePath = generateEtsIR(path, isProject = false)

    irFilePath.inputStream().use { stream ->
        val etsFileDto = EtsFileDto.loadFromJson(stream)
        val etsFile = convertToEtsFile(etsFileDto)
        return etsFile
    }
}

@OptIn(ExperimentalPathApi::class)
fun loadEtsProjectAutoConvert(path: Path): EtsScene {
    val irFolderPath = generateEtsIR(path, isProject = true)

    val files = irFolderPath
        .walk()
        .filter { it.toFile().extension == "json" }
        .mapTo(mutableListOf()) {
            val etsFileDto = EtsFileDto.loadFromJson(it.inputStream())
            convertToEtsFile(etsFileDto)
        }

    return EtsScene(files)
}
