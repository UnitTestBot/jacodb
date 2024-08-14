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
import org.jacodb.ets.dto.convertToEtsFile
import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.utils.dumpDot
import org.jacodb.ets.utils.render
import org.jacodb.ets.utils.toText
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.div
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
    private const val NAME = "basic"
    private const val PATH = "/etsir/samples/$NAME.ts.json"
    private val DOT_DIR = Path("dot")
    private val DOT_PATH = Path("$NAME.dto.dot") // relative to DOT_DIR

    @JvmStatic
    fun main(args: Array<String>) {
        val etsFileDto: EtsFileDto = loadEtsFileDtoFromResource(PATH)

        val text = etsFileDto.toText()
        logger.info { "Text representation of EtsFileDto:\n$text" }

        etsFileDto.dumpDot(DOT_DIR / DOT_PATH)
        render(DOT_DIR, DOT_PATH)
    }
}

/**
 * Visualize classes and methods in [EtsFile].
 */
object DumpEtsFileToDot {
    private const val NAME = "basic"
    private const val PATH = "/etsir/samples/$NAME.ts.json"
    private val DOT_DIR = Path("dot")
    private val DOT_PATH = Path("$NAME.dto.dot") // relative to DOT_DIR

    @JvmStatic
    fun main(args: Array<String>) {
        val etsFile = loadEtsFileFromResource(PATH)

        val text = etsFile.toText()
        logger.info { "Text representation of EtsFile:\n$text" }

        etsFile.dumpDot(DOT_DIR / DOT_PATH)
        render(DOT_DIR, DOT_PATH)
    }
}

/**
 * Visualize classes and methods in [EtsFileDto] and [EtsFile] from directory.
 */
@OptIn(ExperimentalPathApi::class)
object DumpEtsFilesToDot {
    // private const val ETSIR = "/projects/applications_app_samples/etsir/ast/ArkTSDistributedCalc"
    // private val DOT_DIR = Path("generated/projects/applications_app_samples/Calc/dot")

    private const val ETSIR = "/projects/applications_settings_data/etsir/ast"
    private val DOT_DIR = Path("generated/projects/applications_settings_data/dot")

    // private const val ETSIR = "/samples/etsir/ast"
    // private val DOT_DIR = Path("generated/samples/dot")

    @JvmStatic
    fun main(args: Array<String>) {
        val res = ETSIR
        val etsirDir = object {}::class.java.getResource(res)?.toURI()?.toPath()
            ?: error("Resource not found: '$res'")
        logger.info { "etsirDir = $etsirDir" }

        etsirDir.walk()
            .filter { it.name.endsWith(".json") }
            .map { it.relativeTo(etsirDir) }
            .forEach { path ->
                logger.info { "Processing: $path" }

                val etsFileDto = loadEtsFileDtoFromResource("$ETSIR/$path")
                run {
                    val dotPath = DOT_DIR / path.resolveSibling(path.nameWithoutExtension + ".dto.dot")
                    etsFileDto.dumpDot(dotPath)
                    render(DOT_DIR, dotPath.relativeTo(DOT_DIR))
                }

                val etsFile = convertToEtsFile(etsFileDto)
                run {
                    val dotPath = DOT_DIR / path.resolveSibling(path.nameWithoutExtension + ".dot")
                    etsFile.dumpDot(dotPath)
                    render(DOT_DIR, dotPath.relativeTo(DOT_DIR))
                }
            }
    }
}
