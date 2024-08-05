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
import org.jacodb.ets.utils.dumpDot
import org.jacodb.ets.utils.dumpHuimpleTo
import org.jacodb.ets.utils.render
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension
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
        etsFileDto.dumpHuimpleTo(output)

        render(DOT_PATH) { file ->
            etsFileDto.dumpDot(file)
        }
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
        etsFile.dumpHuimpleTo(output)

        render(DOT_PATH) { file ->
            etsFile.dumpDot(file)
        }
    }
}

@OptIn(ExperimentalPathApi::class)
object DumpEtsFilesToDot {
    private const val DIR = "/etsir/samples/"
    private const val DOT_DIR = "dot"

    @JvmStatic
    fun main(args: Array<String>) {
        val baseDirUrl = object {}::class.java.getResource(DIR)
            ?: error("Resource not found: '$DIR'")
        val baseDir = Paths.get(baseDirUrl.toURI())

        val dotDir = baseDir.resolveSibling(DOT_DIR)
        if (!dotDir.exists()) {
            dotDir.createDirectories()
        }

        baseDir.walk()
            .filter { it.name.endsWith(".json") }
            .forEach { path ->
                val relativePath = baseDir.relativize(path)
                val fileName = path.nameWithoutExtension

                process(relativePath, fileName, dotDir, "Dto") {
                    loadEtsFileDtoFromResource(it)
                }
                process(relativePath, fileName, dotDir, "") {
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
        try {
            val resourcePath = DIR + path.toString()
            val fileData = load(resourcePath)
            val outputDir = dotDir.resolve(path.parent ?: dotDir)
            if (!outputDir.exists()) {
                outputDir.createDirectories()
            }
            val outputPath = outputDir.resolve("${name}${suffix}.dot").toString()
            render(outputPath) { file ->
                when (fileData) {
                    is EtsFileDto -> fileData.dumpDot(file)
                    is EtsFile -> fileData.dumpDot(file)
                }
            }
        } catch (e: Exception) {
            logger.error { e.stackTraceToString() }
        }
    }
}
