package com.huawei.java.compilation.database.impl

import com.huawei.java.compilation.database.api.ByteCodeLocation
import java.io.File

class ByteCodeLocationImpl(override val file: File): ByteCodeLocation {

    override val isJar: Boolean
        get() = file.isFile

    override val version: String
        get() = file.name // todo check last modified date
}