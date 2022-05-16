package com.huawei.java.compilation.database.impl

import com.huawei.java.compilation.database.api.ByteCodeLocation
import com.huawei.java.compilation.database.api.ClassId
import com.huawei.java.compilation.database.api.ClasspathSet

class ClasspathSetImpl(override val locations: List<ByteCodeLocation>): ClasspathSet {

    override suspend fun findClassOrNull(name: String): ClassId? {
        TODO("Not yet implemented")
    }

    override fun close() {
        TODO("Not yet implemented")
    }
}