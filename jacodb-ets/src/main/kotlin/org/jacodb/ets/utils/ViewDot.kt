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

import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.div
import kotlin.io.path.writeText

fun view(
    dot: String,
    viewerCmd: String = when {
        System.getProperty("os.name").startsWith("Mac") -> "open"
        System.getProperty("os.name").startsWith("Win") -> "cmd /c start"
        else -> "xdg-open"
    },
    dotCmd: String = "dot",
    outputFormat: String = "svg",
    name: String = "graph",
    tempDir: Path = createTempDirectory("dot"),
) {
    val dotFile = tempDir / "$name.dot"
    println("Writing DOT file to $dotFile")
    dotFile.writeText(dot)
    val outputFile = tempDir / "$name.$outputFormat"
    println("Rendering ${outputFormat.uppercase()} to '$outputFile'...")
    Runtime.getRuntime().exec("$dotCmd -T$outputFormat -o $outputFile $dotFile").waitFor()
    println("Opening rendered file '$outputFile'...")
    Runtime.getRuntime().exec("$viewerCmd $outputFile").waitFor()
}

fun <T> T.view(dot: (T) -> String) {
    view(dot(this))
}
