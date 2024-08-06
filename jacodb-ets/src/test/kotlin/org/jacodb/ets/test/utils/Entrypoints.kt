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

package org.jacodb.ets.test.utils

import org.jacodb.ets.dto.EtsFileDto
import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.utils.dumpContentTo
import org.jacodb.ets.utils.dumpDot
import org.jacodb.ets.utils.render
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.relativeTo
import kotlin.io.path.toPath
import kotlin.io.path.walk

private val logger = mu.KotlinLogging.logger {}

/**
 * Visualize classes and methods in [EtsFileDto].
 */
object DumpEtsFileDtoToDot {
    private const val PATH = "/etsir/samples/basic.ts.json"
    private const val DOT_PATH = "basicDto.dot"

    @JvmStatic
    fun main(args: Array<String>) {
        val etsFileDto: EtsFileDto = loadEtsFileDtoFromResource(PATH)

        val output = System.out.bufferedWriter()
        etsFileDto.dumpContentTo(output)

        etsFileDto.dumpDot(DOT_PATH)
        render(DOT_PATH)
    }
}

/**
 * Visualize classes and methods in [EtsFile].
 */
object DumpEtsFileToDot {
    private const val PATH = "/etsir/samples/basic.ts.json"
    private const val DOT_PATH = "basic.dot"

    @JvmStatic
    fun main(args: Array<String>) {
        val etsFile = loadEtsFileFromResource(PATH)

        val output = System.out.bufferedWriter()
        etsFile.dumpContentTo(output)

        etsFile.dumpDot(DOT_PATH)
        render(DOT_PATH)
    }
}

/**
 * Visualize classes and methods in [EtsFileDto] and [EtsFile] from directory.
 */
@OptIn(ExperimentalPathApi::class)
object DumpEtsFilesToDot {
    private const val ETSIR_DIR = "/etsir/samples"
    private const val DOT_DIR = "dot/samples"

    @JvmStatic
    fun main(args: Array<String>) {
        val baseDir = object {}::class.java.getResource(ETSIR_DIR)?.toURI()?.toPath()
            ?: error("Resource not found: '$ETSIR_DIR'")
        logger.info { "baseDir = $baseDir" }

        val dotDir = Path(DOT_DIR)
        dotDir.createDirectories()
        logger.info { "dotDir = $dotDir" }

        baseDir.walk()
            .filter { it.name.endsWith(".json") }
            .forEach { path ->
                val relative = path.relativeTo(baseDir)
                val name = path.nameWithoutExtension

                process(relative, name, dotDir, "_DTO") {
                    loadEtsFileDtoFromResource(it)
                }
                process(relative, name, dotDir, "") {
                    loadEtsFileFromResource(it)
                }

                logger.info { "Processed: $path" }
            }
    }

    private fun <T> process(
        path: Path,
        name: String,
        dotDir: Path,
        suffix: String,
        load: (String) -> T,
    ) {
        val resourcePath = "$ETSIR_DIR/$path"
        val dotPath = dotDir.resolve("${name}${suffix}.dot")
        when (val f = load(resourcePath)) {
            is EtsFileDto -> f.dumpDot(dotPath)
            is EtsFile -> f.dumpDot(dotPath)
            else -> error("Unknown type: $f")
        }
        render(dotPath)
    }
}
