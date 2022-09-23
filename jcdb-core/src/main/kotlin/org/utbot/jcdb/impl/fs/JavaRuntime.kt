package org.utbot.jcdb.impl.fs

import org.utbot.jcdb.api.JcByteCodeLocation
import java.io.File
import java.nio.file.Paths

class JavaRuntime(val javaHome: File) {

    val allLocations: List<JcByteCodeLocation> = modules.takeIf { it.isNotEmpty() } ?: (bootstrapJars + extJars)

    val modules: List<JcByteCodeLocation> get() = locations("jmods")

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
            .map { it.asByteCodeLocation(true) }
    }

}
