package org.utbot.jcdb.impl.tree

import org.utbot.jcdb.impl.fs.ClassByteCodeSource

class ClassNode(
    simpleName: String,
    packageNode: PackageNode,
    val source: ClassByteCodeSource
) : AbstractNode<PackageNode>(simpleName, packageNode) {

    override val name: String = simpleName

    val location get() = source.location

    suspend fun info() = source.info
    suspend fun fullByteCode() = source.fullByteCode

}