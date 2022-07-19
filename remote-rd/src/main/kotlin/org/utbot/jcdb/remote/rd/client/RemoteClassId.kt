package org.utbot.jcdb.remote.rd.client

import org.objectweb.asm.tree.ClassNode
import org.utbot.jcdb.api.*
import org.utbot.jcdb.impl.fs.ByteCodeConverter
import org.utbot.jcdb.impl.signature.TypeResolution

class RemoteClassId(private val node: ClassNode, override val classpath: ClasspathSet) : ClassId, ByteCodeConverter {

    private val classInfo = node.asClassInfo()

    override val name: String
        get() = classInfo.name

    override suspend fun access() = classInfo.access

    override val location: ByteCodeLocation?
        get() = null

    override val simpleName: String
        get() = classInfo.name

    override suspend fun byteCode() = node

    override suspend fun innerClasses(): List<ClassId> {
        TODO("Not yet implemented")
    }

    override suspend fun outerClass(): ClassId? {
        TODO("Not yet implemented")
    }

    override suspend fun isAnonymous(): Boolean {
        TODO("Not yet implemented")
    }

    override suspend fun signature(): TypeResolution {
        TODO("Not yet implemented")
    }

    override suspend fun outerMethod(): MethodId? {
        TODO("Not yet implemented")
    }

    override suspend fun methods(): List<MethodId> {
        TODO("Not yet implemented")
    }

    override suspend fun superclass(): ClassId? {
        TODO("Not yet implemented")
    }

    override suspend fun interfaces(): List<ClassId> {
        TODO("Not yet implemented")
    }

    override suspend fun annotations(): List<ClassId> {
        TODO("Not yet implemented")
    }

    override suspend fun fields(): List<FieldId> {
        TODO("Not yet implemented")
    }
}