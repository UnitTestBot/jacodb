package com.huawei.java.compilation.database.impl.meta

import com.huawei.java.compilation.database.api.ClassId
import com.huawei.java.compilation.database.api.MethodId
import com.huawei.java.compilation.database.impl.ClassIdService
import com.huawei.java.compilation.database.impl.fs.MethodMetaInfo
import com.huawei.java.compilation.database.impl.tree.ClassNode
import org.objectweb.asm.tree.MethodNode

class MethodIdImpl(
    private val methodInfo: MethodMetaInfo,
    private val classNode: ClassNode,
    override val classId: ClassId,
    private val classIdService: ClassIdService
) : MethodId {

    override val name: String get() = methodInfo.name
    override suspend fun access() = methodInfo.access

    private val lazyParameters by lazy {
        methodInfo.parameters.mapNotNull {
            classIdService.toClassId(it)
        }
    }
    private val lazyAnnotations by lazy {
        methodInfo.annotations.mapNotNull {
            classIdService.toClassId(it.className)
        }
    }

    override suspend fun returnType() = classIdService.toClassId(methodInfo.returnType)

    override suspend fun parameters() = lazyParameters

    override suspend fun annotations() = lazyAnnotations

    override suspend fun readBody(): MethodNode {
        val location = classId.location
        if (location?.isChanged() == true) {
            throw IllegalStateException("bytecode is changed")
        }
        return classNode.source.loadMethod(name, methodInfo.desc)
    }
}