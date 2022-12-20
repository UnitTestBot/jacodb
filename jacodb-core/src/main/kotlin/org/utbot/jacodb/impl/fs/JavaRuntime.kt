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

package org.utbot.jacodb.impl.fs

import org.utbot.jacodb.api.JavaVersion
import org.utbot.jacodb.api.JcByteCodeLocation
import java.io.File
import java.nio.file.Paths

class JavaRuntime(private val javaHome: File) {

    val version: JavaVersion = try {
        val releaseFile = File(javaHome, "release")
        val javaVersion = releaseFile.readLines().first { it.startsWith("JAVA_VERSION=") }
        parseRuntimeVersion(javaVersion.substring("JAVA_VERSION=".length + 1, javaVersion.length - 1))
    } catch (e: Exception) {
        logger.info("Can't find or parse 'release' file inside java runtime folder. Use 8 java version for this runtime.")
        parseRuntimeVersion("1.8.0")
    }

    val allLocations: List<JcByteCodeLocation> = modules.takeIf { it.isNotEmpty() } ?: (bootstrapJars + extJars)

    private val modules: List<JcByteCodeLocation> get() = locations("jmods")

    private val bootstrapJars: List<JcByteCodeLocation>
        get() {
            return when (isJDK) {
                true -> locations("jre", "lib")
                else -> locations("lib")
            }
        }

    private val extJars: List<JcByteCodeLocation>
        get() {
            return when (isJDK) {
                true -> locations("jre", "lib", "ext")
                else -> locations("lib", "ext")
            }
        }

    private val isJDK: Boolean get() = !javaHome.endsWith("jre")

    private fun locations(vararg subFolders: String): List<JcByteCodeLocation> {
        return Paths.get(javaHome.toPath().toString(), *subFolders).toFile()
            .listFiles { file -> file.name.endsWith(".jar") || file.name.endsWith(".jmod") }
            .orEmpty()
            .toList()
            .map { it.asByteCodeLocation(version, true) }
    }

}
