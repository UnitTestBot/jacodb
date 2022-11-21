package org.utbot.jcdb.impl.fs

import org.utbot.jcdb.api.JavaVersion
import org.utbot.jcdb.api.JcByteCodeLocation
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
