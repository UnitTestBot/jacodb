package com.huawei.java.compilation.database.impl.tree

import com.huawei.java.compilation.database.ApiLevel
import com.huawei.java.compilation.database.api.ByteCodeLocation
import java.io.InputStream

open class DummyCodeLocation(override val version: String) : ByteCodeLocation{
        override val apiLevel: ApiLevel
                get() = ApiLevel.ASM8

        override val currentVersion: String
                get() = version
        override suspend fun classesByteCode() = emptySequence<Pair<String,InputStream>>()
        override suspend fun resolve(classFullName: String): InputStream? {
                TODO("Not yet implemented")
        }
}

