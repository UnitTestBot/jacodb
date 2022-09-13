package org.utbot.jcdb.impl.tree

import org.objectweb.asm.tree.ClassNode
import org.utbot.jcdb.api.ByteCodeContainer
import org.utbot.jcdb.impl.fs.ClassByteCodeSource

class ClassNode(
    simpleName: String,
    packageNode: PackageNode,
    private val source: ClassByteCodeSource
) : AbstractNode<PackageNode>(simpleName, packageNode), ByteCodeContainer {

    override val name: String = simpleName
    val location get() = source.location

    fun fullByteCode() = source.fullByteCode
    fun info() = source.info

    override val classNode: ClassNode
        get() = source.byteCode
    override val binary: ByteArray
        get() = source.binaryByteCode

}