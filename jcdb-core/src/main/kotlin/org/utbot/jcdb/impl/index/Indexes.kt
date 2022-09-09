package org.utbot.jcdb.impl.index

import org.utbot.jcdb.api.ByteCodeIndexBuilder
import org.utbot.jcdb.impl.tree.ClassNode

suspend fun index(node: ClassNode, builder: ByteCodeIndexBuilder<*, *>) {
    val asmNode = node.fullByteCode()
    builder.index(asmNode)
    asmNode.methods.forEach {
        builder.index(asmNode, it)
    }
}