package org.utbot.jcdb.impl.vfs

import org.objectweb.asm.tree.ClassNode
import org.utbot.jcdb.api.ByteCodeContainer
import org.utbot.jcdb.impl.fs.ClassByteCodeSource

class ClassVfsItem(
    override val name: String,
    packageNode: PackageVfsItem,
    @Volatile
    private var source: ClassByteCodeSource
) : AbstractVfsItem<PackageVfsItem>(name, packageNode), ByteCodeContainer {

    val location get() = source.location

    fun fullByteCode() = source.fullAsmNode
    fun info() = source.info

    override val asmNode: ClassNode
        get() = source.asmNode

    override val binary: ByteArray
        get() = source.binaryByteCode

}