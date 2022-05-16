package com.huawei.java.compilation.database.impl.tree

import com.huawei.java.compilation.database.api.ByteCodeLocation
import java.io.File

open class DummyCodeLocation(override val version: String) : ByteCodeLocation{
        override val isJar = false
        override val file = File("")
}

