package com.huawei.java.compilation.database.impl.tree

import com.huawei.java.compilation.database.api.ByteCodeLocation

class ClasspathClassTree(
    private val classTree: ClassTree,
    locations: List<ByteCodeLocation>
) {

    private val locationHashes = locations.map { it.version }.toHashSet()
    fun findClassOrNull(fullName: String): ClassNode? {
        return classTree.firstClassNodeOrNull(fullName) {
            locationHashes.contains(it)
        }
    }
}