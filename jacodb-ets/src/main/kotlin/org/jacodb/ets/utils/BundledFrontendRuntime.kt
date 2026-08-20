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

import java.io.FileNotFoundException
import java.io.InputStream
import java.nio.file.Path
import java.util.zip.ZipInputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.outputStream

/** Owns the complete lifecycle of the TypeScript frontend bundled in the JAR. */
internal object BundledFrontendRuntime {
    private const val RUNTIME_RESOURCE = "/ets-frontend/runtime.zip"
    private const val SHUTDOWN_HOOK_NAME = "jacodb-ets-bundled-frontend-cleanup"

    /**
     * The extracted entry script, or `null` when this artifact has no bundled runtime.
     * Extraction and its temporary directory are both initialized at most once.
     */
    val script: Path? by lazy {
        val input = BundledFrontendRuntime::class.java.getResourceAsStream(RUNTIME_RESOURCE)
            ?: return@lazy null
        input.use {
            val runtimeDirectory = createTempDirectory("jacodb-ets-frontend-")
            try {
                val extractedScript = extract(it, runtimeDirectory)
                val cleanupHook = Thread(
                    { runtimeDirectory.toFile().deleteRecursively() },
                    SHUTDOWN_HOOK_NAME,
                )
                Runtime.getRuntime().addShutdownHook(cleanupHook)
                extractedScript
            } catch (failure: Throwable) {
                runtimeDirectory.toFile().deleteRecursively()
                throw failure
            }
        }
    }

    /** Extracts one runtime archive into [targetDirectory], deleting it on any failure. */
    internal fun extract(input: InputStream, targetDirectory: Path): Path {
        val root = targetDirectory.toAbsolutePath().normalize()
        try {
            root.createDirectories()
            ZipInputStream(input).use { archive ->
                while (true) {
                    val entry = archive.nextEntry ?: break
                    try {
                        val target = root.resolve(entry.name).normalize()
                        require(target.startsWith(root)) {
                            "Unsafe entry in bundled ts-frontend runtime: '${entry.name}'"
                        }
                        if (entry.isDirectory) {
                            target.createDirectories()
                        } else {
                            target.parent.createDirectories()
                            target.outputStream().use(archive::copyTo)
                        }
                    } finally {
                        archive.closeEntry()
                    }
                }
            }

            val script = root.resolve("index.js")
            if (!script.isRegularFile()) {
                throw FileNotFoundException("Bundled ts-frontend runtime does not contain index.js")
            }
            return script
        } catch (failure: Throwable) {
            root.toFile().deleteRecursively()
            throw failure
        }
    }
}
