package com.huawei.java.compilation.database.impl.tree

import com.huawei.java.compilation.database.api.ByteCodeLocation
import kotlinx.collections.immutable.PersistentList

class LimitedClassTree(
    private val classTree: ClassTree,
    locations: PersistentList<ByteCodeLocation>
) {

    private val locationHashes = locations.map { it.version }.toHashSet()

    fun findClassOrNull(fullName: String): ClassNode? {
        return classTree.firstClassNodeOrNull(fullName) {
            locationHashes.contains(it)
        }
    }
}