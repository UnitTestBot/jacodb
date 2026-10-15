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

import org.jacodb.ets.dto.CfgDto
import org.jacodb.ets.dto.EtsFileDto
import org.jacodb.ets.model.EtsBlockCfg
import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.model.EtsLinearCfg
import java.nio.file.Path
import java.util.StringTokenizer
import kotlin.io.path.createTempDirectory
import kotlin.io.path.div
import kotlin.io.path.writeText

private const val windowsViewerCommand = "cmd /c start"
private val windowsViewerPrefix = listOf("cmd", "/c", "start", "")

private fun legacyCommandArguments(command: String): List<String> {
    val tokens = StringTokenizer(command)
    val arguments = mutableListOf<String>()
    while (tokens.hasMoreTokens()) {
        arguments.add(tokens.nextToken())
    }

    return arguments
}

internal fun viewerLaunchCommand(viewerCommand: List<String>, outputFile: Path): List<String> {
    if (viewerCommand == windowsViewerPrefix) {
        return viewerCommand + "\"${outputFile.toAbsolutePath()}\""
    }

    return viewerCommand + outputFile.toString()
}

/**
 * Writes and renders DOT, then launches a viewer. String commands retain their whitespace-separated prefix syntax;
 * use the list overload for executable paths or arguments containing spaces. Returning means launch succeeded,
 * not that the viewer has exited.
 */
fun view(
    dot: String,
    viewerCmd: String = when {
        System.getProperty("os.name").startsWith("Mac") -> "open"
        System.getProperty("os.name").startsWith("Win") -> windowsViewerCommand
        else -> "xdg-open"
    },
    dotCmd: String = "dot",
    outputFormat: String = "svg",
    name: String = "graph",
    tempDir: Path = createTempDirectory("dot"),
) {
    val viewerCommand = if (viewerCmd == windowsViewerCommand) {
        windowsViewerPrefix // cmd's empty window title keeps the following path as the file to open.
    } else {
        legacyCommandArguments(viewerCmd)
    }
    val dotCommand = legacyCommandArguments(dotCmd)

    view(
        dot = dot,
        viewerCommand = viewerCommand,
        dotCommand = dotCommand,
        outputFormat = outputFormat,
        name = name,
        tempDir = tempDir,
    )
}

/** Supply executable and arguments separately when a custom renderer or viewer needs options. */
fun view(
    dot: String,
    viewerCommand: List<String>,
    dotCommand: List<String> = listOf("dot"),
    outputFormat: String = "svg",
    name: String = "graph",
    tempDir: Path = createTempDirectory("dot"),
) {
    require(viewerCommand.isNotEmpty()) { "Viewer command must contain an executable" }
    require(dotCommand.isNotEmpty()) { "DOT command must contain an executable" }

    val dotFile = tempDir / "$name.dot"
    println("Writing DOT file to $dotFile")
    dotFile.writeText(dot)
    val outputFile = tempDir / "$name.$outputFormat"
    println("Rendering ${outputFormat.uppercase()} to '$outputFile'...")

    renderDotFile(dotCommand = dotCommand, format = outputFormat, dotFile = dotFile, outputFile = outputFile)

    println("Launching viewer for '$outputFile'...")
    val launchCommand = viewerLaunchCommand(viewerCommand, outputFile)
    ProcessBuilder(launchCommand).inheritIO().start()
}

fun <T> T.view(dot: (T) -> String) {
    view(dot(this))
}

fun EtsFile.view() {
    view(toDot())
}

fun EtsFileDto.view() {
    view(toDot())
}

fun EtsBlockCfg.view() {
    view(toDot())
}

fun EtsLinearCfg.view() {
    view(toDot())
}

fun CfgDto.view() {
    view(toDot())
}
