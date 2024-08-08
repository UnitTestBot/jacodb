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
import org.jacodb.ets.utils.render
import org.jacodb.ets.utils.toText
import java.nio.file.Path
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
    private const val ETSIR_BASE = "/etsir"
    private const val ETSIR_DIR = "source" // relative to BASE
    private val DOT_DIR = Path("generated/dot")

    @JvmStatic
    fun main(args: Array<String>) {
        val resPath = "$ETSIR_BASE/$ETSIR_DIR"
        val etsirDir = object {}::class.java.getResource(resPath)?.toURI()?.toPath()
            ?: error("Resource not found: '$resPath'")
        logger.info { "baseDir = $etsirDir" }

        etsirDir.walk()
            .filter { it.name.endsWith(".json") }
            .forEach { path ->
                val relative = path.relativeTo(etsirDir)

                process(relative, ".dto") {
                    loadEtsFileDtoFromResource(it)
                }
                process(relative, "") {
                    loadEtsFileFromResource(it)
                }

                logger.info { "Processed: $path" }
            }
    }

    private fun <T> process(
        relative: Path,
        suffix: String,
        load: (String) -> T,
    ) {
        val resourcePath = "$ETSIR_BASE/$ETSIR_DIR/$relative"
        val relativeDot = (Path(ETSIR_DIR) / relative)
            .resolveSibling("${relative.nameWithoutExtension}$suffix.dot")
        val dotPath = DOT_DIR / relativeDot
        when (val f = load(resourcePath)) {
            is EtsFileDto -> f.dumpDot(dotPath)
            is EtsFile -> f.dumpDot(dotPath)
            else -> error("Unknown type: $f")
        }
        render(DOT_DIR, relativeDot)
    }
}
