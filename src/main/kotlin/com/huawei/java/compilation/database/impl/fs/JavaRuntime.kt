package com.huawei.java.compilation.database.impl.fs

import com.huawei.java.compilation.database.ApiLevel
import com.huawei.java.compilation.database.api.ByteCodeLocation
import java.io.File
import java.nio.file.Paths

class JavaRuntime(val apiLevel: ApiLevel, val javaHome: File) {

    companion object {
        private val loadedPackages = listOf("java.", "javax.")
    }

    val allLocations: List<ByteCodeLocation> = bootstrapJars + extJars

    val bootstrapJars: List<ByteCodeLocation> get() {
        return locations("jre", "lib")
    }

    val extJars: List<ByteCodeLocation> get() {
        return locations( "jre", "lib", "ext")
    }

    private fun locations(vararg subFolders: String): List<ByteCodeLocation> {
        return Paths.get(javaHome.toPath().toString(), *subFolders).toFile()
            .listFiles { file -> file.name.endsWith(".jar") }
            .orEmpty()
            .toList()
            .map { it.asByteCodeLocation(apiLevel, loadedPackages) }
    }

}
