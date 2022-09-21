package org.utbot.jcdb.impl.index

import org.utbot.jcdb.api.ByteCodeIndexer
import org.utbot.jcdb.impl.vfs.ClassVfsItem

suspend fun index(node: ClassVfsItem, builder: ByteCodeIndexer) {
    val asmNode = node.fullAsmNode()
    builder.index(asmNode)
    asmNode.methods.forEach {
        builder.index(asmNode, it)
    }
}