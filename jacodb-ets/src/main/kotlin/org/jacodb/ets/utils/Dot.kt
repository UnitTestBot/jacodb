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
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolute
import kotlin.io.path.nameWithoutExtension
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}

fun render(dotPath: Path) {
    for (format in listOf("pdf")) {
        logger.info { "Rendering DOT to ${format.uppercase()}..." }
        val formatFile = dotPath.resolveSibling(dotPath.nameWithoutExtension + ".$format")
        val cmd: List<String> = listOf(
            "dot",
            "-T$format",
            "$dotPath",
            "-o",
            "$formatFile"
        )
        runProcess(cmd, 60.seconds)
        logger.info { "Generated ${format.uppercase()} file: ${formatFile.absolute()}" }
    }
}

fun render(dotPath: String) {
    render(Path(dotPath))
}
