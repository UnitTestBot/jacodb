package com.huawei.java.compilation.database.impl.meta

import com.huawei.java.compilation.database.api.FieldId
import com.huawei.java.compilation.database.impl.ClassIdService
import com.huawei.java.compilation.database.impl.fs.FieldMetaInfo

class FieldIdImpl(
    val info: FieldMetaInfo,
    private val classIdService: ClassIdService
) : FieldId {

    override val name: String
        get() = info.name

    private val lazyType by lazy(LazyThreadSafetyMode.NONE) {
        classIdService.toClassId(info.type)
    }

    override suspend fun access() = info.access
    override suspend fun type() = lazyType

}