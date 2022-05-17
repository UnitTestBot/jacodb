package com.huawei.java.compilation.database.impl

import com.huawei.java.compilation.database.api.ByteCodeLocation
import com.huawei.java.compilation.database.api.ClassId
import com.huawei.java.compilation.database.api.ClasspathSet
import com.huawei.java.compilation.database.impl.tree.ClassTree
import com.huawei.java.compilation.database.impl.tree.LimitedClassTree
import kotlinx.collections.immutable.PersistentList

class ClasspathSetImpl(
    override val locations: PersistentList<ByteCodeLocation>,
    classTree: ClassTree
) : ClasspathSet {

    private val limitedClassTree = LimitedClassTree(classTree, locations)

    override suspend fun findClassOrNull(name: String): ClassId? {
        TODO("Not yet implemented")
    }

    override fun close() {
        TODO("Not yet implemented")
    }
}