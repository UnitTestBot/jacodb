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

package org.jacodb.ets.grpc

import mu.KotlinLogging
import java.io.FileNotFoundException
import java.io.IOException
import java.net.Socket
import kotlin.concurrent.thread
import kotlin.io.path.Path
import kotlin.io.path.absolute
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.pathString

private val logger = KotlinLogging.logger {}

class Server(
    val process: Process,
    val outputThread: Thread,
    val errorThread: Thread,
) {
    fun stop() {
        logger.info { "Stopping ArkAnalyzer server..." }
        process.destroy()
        try {
            process.waitFor()
        } catch (e: InterruptedException) {
            logger.error(e) { "Error while waiting for server process to finish" }
        }
        outputThread.join()
        errorThread.join()
        logger.info { "ArkAnalyzer server stopped" }
    }
}

private const val ENV_VAR_ARKANALYZER_PORT = "ARKANALYZER_PORT"

private const val ENV_VAR_ARKANALYZER_DIR = "ARKANALYZER_DIR"
private const val DEFAULT_ARKANALYZER_DIR = "arkanalyzer"

// Note: The script path is relative to the ARKANALYZER_DIR. Or use the absolute path.
private const val ENV_VAR_SERVER_SCRIPT_PATH = "SERVER_SCRIPT_PATH"
private const val DEFAULT_SERVER_SCRIPT_PATH = "out/src/rpc/grpc-server.js"

private const val ENV_VAR_NODE_EXECUTABLE = "NODE_EXECUTABLE"
private const val DEFAULT_NODE_EXECUTABLE = "node"

fun startArkAnalyzerServer(port: Int): Server {
    logger.info { "Starting ArkAnalyzer server on port $port..." }

    val arkAnalyzerDir = Path(System.getenv(ENV_VAR_ARKANALYZER_DIR) ?: DEFAULT_ARKANALYZER_DIR)
    if (!arkAnalyzerDir.exists()) {
        throw FileNotFoundException(
            "ArkAnalyzer directory does not exist: '${arkAnalyzerDir.absolute()}'. " +
                "Did you forget to set the '$ENV_VAR_ARKANALYZER_DIR' environment variable? " +
                "Current value is '${System.getenv(ENV_VAR_ARKANALYZER_DIR)}', " +
                "current dir is '${Path("").toAbsolutePath()}'."
        )
    }
    logger.info { "Using ArkAnalyzer directory: $arkAnalyzerDir" }

    val scriptPath = System.getenv(ENV_VAR_SERVER_SCRIPT_PATH) ?: DEFAULT_SERVER_SCRIPT_PATH
    val script = arkAnalyzerDir / scriptPath
    if (!script.exists()) {
        throw FileNotFoundException(
            "Script file not found: '$script'. " +
                "Did you forget to execute 'npm run build' in the ArkAnalyzer project?"
        )
    }
    logger.info { "Using server script: $script" }

    val node = System.getenv(ENV_VAR_NODE_EXECUTABLE) ?: DEFAULT_NODE_EXECUTABLE
    logger.info { "Using Node.js executable: $node" }

    val process = ProcessBuilder(node, script.pathString)
        .also {
            val env = it.environment()
            env[ENV_VAR_ARKANALYZER_PORT] = port.toString()
        }
        .start()

    // Capture process output (stdout)
    val stdout = StringBuilder()
    val outputThread = thread {
        process.inputStream.bufferedReader().useLines { lines ->
            lines.forEach {
                logger.info { "[STDOUT] $it" }
                stdout.appendLine(it)
            }
        }
    }

    // Capture process error output (stderr)
    val stderr = StringBuilder()
    val errorThread = thread {
        process.errorStream.bufferedReader().useLines { lines ->
            lines.forEach {
                logger.info { "[STDERR] $it" }
                stderr.appendLine(it)
            }
        }
    }

    // Wait for the server to start
    waitForServerToStart(port)

    return Server(process, outputThread, errorThread)
}

private fun waitForServerToStart(port: Int) {
    logger.info { "Waiting for server to start on port $port..." }
    val maxRetries = 100
    val retryDelay = 100L // in milliseconds
    repeat(maxRetries) {
        try {
            Socket("localhost", port).use { socket ->
                if (socket.isConnected) {
                    logger.info { "ArkAnalyzer server is ready on port $port" }
                    return@waitForServerToStart
                }
            }
        } catch (_: IOException) {
            // Server not ready yet, retry
        }
        Thread.sleep(retryDelay)
    }
    val time = maxRetries * retryDelay / 1000
    throw RuntimeException("ArkAnalyzer server did not start after $time s")
}
