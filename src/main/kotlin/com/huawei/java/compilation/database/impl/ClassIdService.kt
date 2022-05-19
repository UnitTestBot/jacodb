package com.huawei.java.compilation.database.impl

import com.huawei.java.compilation.database.api.ClassId
import com.huawei.java.compilation.database.api.MethodId
import com.huawei.java.compilation.database.impl.fs.MethodMetaInfo
import com.huawei.java.compilation.database.impl.meta.ClassIdImpl
import com.huawei.java.compilation.database.impl.meta.MethodIdImpl
import com.huawei.java.compilation.database.impl.meta.PredefinedPrimitive
import com.huawei.java.compilation.database.impl.tree.ClassNode
import com.huawei.java.compilation.database.impl.tree.ClasspathClassTree
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.toPersistentHashMap

class ClassIdService(internal val classpathClassTree: ClasspathClassTree) {

    companion object {
        private val predefinedClasses: PersistentMap<String, ClassId> = PredefinedPrimitive.values.map { it.simpleName to it }.toMap().toPersistentHashMap()
    }

    fun toClassId(node: ClassNode?): ClassId? {
        node ?: return null
        return ClassIdImpl(node, this)
    }

    fun toClassId(fullName: String?): ClassId? {
        fullName ?: return null
        val predefinedClass = predefinedClasses[fullName]
        if (predefinedClass != null) {
            return predefinedClass
        }
        return toClassId(classpathClassTree.findClassOrNull(fullName))
    }

    fun toMethodId(classId: ClassId, methodInfo: MethodMetaInfo, node: ClassNode): MethodId {
        return MethodIdImpl(methodInfo, node, classId, this)
    }

}