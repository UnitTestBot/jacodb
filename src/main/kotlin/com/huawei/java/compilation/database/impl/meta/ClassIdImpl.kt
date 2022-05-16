package com.huawei.java.compilation.database.impl.meta

import com.huawei.java.compilation.database.api.ByteCodeLocation
import com.huawei.java.compilation.database.api.ClassId
import com.huawei.java.compilation.database.api.MethodId

class ClassIdImpl(
    override val location: ByteCodeLocation,
    override val name: String,
    override val simpleName: String
) : ClassId {

    override suspend fun methods(): List<MethodId> {
        TODO("Not yet implemented")
    }

    override suspend fun superclass(): ClassId? {
        TODO("Not yet implemented")
    }

    override suspend fun interfaces(): List<ClassId> {
        TODO("Not yet implemented")
    }

    override suspend fun annotations(): List<ClassId> {
        TODO("Not yet implemented")
    }
}