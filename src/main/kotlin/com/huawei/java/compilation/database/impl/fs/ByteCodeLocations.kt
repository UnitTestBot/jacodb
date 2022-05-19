package com.huawei.java.compilation.database.impl.fs

import com.huawei.java.compilation.database.ApiLevel
import com.huawei.java.compilation.database.api.ByteCodeLocation
import com.huawei.java.compilation.database.impl.CompilationDatabaseImpl
import java.io.File


fun File.asByteCodeLocation(apiLevel: ApiLevel): ByteCodeLocation {
    if (!exists()) {
        throw IllegalArgumentException("file $absolutePath doesn't exist")
    }
    if (isFile && name.endsWith(".jar")) {
        return JarFileLocationImpl(this, apiLevel)
    } else if (!isFile) {
        return BuildFolderLocationImpl(this, apiLevel)
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
