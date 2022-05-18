package com.huawei.java.compilation.database.impl

import com.huawei.java.compilation.database.api.ClassId
import com.huawei.java.compilation.database.api.MethodId
import com.huawei.java.compilation.database.impl.meta.ClassIdImpl
import com.huawei.java.compilation.database.impl.meta.MethodIdImpl
import com.huawei.java.compilation.database.impl.reader.MethodMetaInfo
import com.huawei.java.compilation.database.impl.tree.ClassNode
import com.huawei.java.compilation.database.impl.tree.ClasspathClassTree

class ClassIdService(internal val classpathClassTree: ClasspathClassTree) {

    fun toClassId(node: ClassNode?): ClassId? {
        node ?: return null
        return ClassIdImpl(node, this)
    }

    fun toClassId(fullName: String?): ClassId? {
        fullName ?: return null
        return toClassId(classpathClassTree.findClassOrNull(fullName))
    }

    fun toMethodId(classId: ClassId, methodInfo: MethodMetaInfo): MethodId {
        return MethodIdImpl(methodInfo, classId, this)
    }

}