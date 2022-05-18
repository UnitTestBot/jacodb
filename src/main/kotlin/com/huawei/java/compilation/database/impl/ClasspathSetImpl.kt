package com.huawei.java.compilation.database.impl

import com.huawei.java.compilation.database.api.ByteCodeLocation
import com.huawei.java.compilation.database.api.ClassId
import com.huawei.java.compilation.database.api.ClasspathSet
import com.huawei.java.compilation.database.impl.tree.ClassTree
import com.huawei.java.compilation.database.impl.tree.ClasspathClassTree
import kotlinx.collections.immutable.PersistentList

class ClasspathSetImpl(
    override val locations: PersistentList<ByteCodeLocation>,
    classTree: ClassTree
) : ClasspathSet {

    private val classpathClassTree = ClasspathClassTree(classTree, locations)
    private val classIdService = ClassIdService(classpathClassTree)

    override suspend fun findClassOrNull(name: String): ClassId? {
        return classIdService.toClassId(classpathClassTree.findClassOrNull(name))
    }

    override fun close() {
        TODO("Not yet implemented")
    }
}