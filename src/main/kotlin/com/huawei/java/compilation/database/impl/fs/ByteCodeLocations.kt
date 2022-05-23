package com.huawei.java.compilation.database.impl.fs

import com.huawei.java.compilation.database.ApiLevel
import com.huawei.java.compilation.database.api.ByteCodeLocation
import com.huawei.java.compilation.database.impl.CompilationDatabaseImpl
import kotlinx.collections.immutable.toPersistentList
import java.io.File


fun File.asByteCodeLocation(apiLevel: ApiLevel, loadClassesOnlyFrom: List<String>? = null): ByteCodeLocation {
    if (!exists()) {
        throw IllegalArgumentException("file $absolutePath doesn't exist")
    }
    if (isFile && name.endsWith(".jar")) {
        return JarFileLocationImpl(this, apiLevel, loadClassesOnlyFrom?.toPersistentList())
    } else if (!isFile) {
        return BuildFolderLocationImpl(this, apiLevel, loadClassesOnlyFrom)
    }
    throw IllegalArgumentException("file $absolutePath is not jar-file nor build dir folder")
}

fun List<File>.filterExisted(): List<File> = filter { file ->
    file.exists().also {
        if (!it) {
            CompilationDatabaseImpl.logger.warn("${file.absolutePath} doesn't exists. make sure there is no mistake")
        }
    }
}

fun String.matchesOneOf(loadClassesOnlyFrom: List<String>?): Boolean {
    loadClassesOnlyFrom ?: return true
    return loadClassesOnlyFrom.any {
        startsWith(it)
    }
}