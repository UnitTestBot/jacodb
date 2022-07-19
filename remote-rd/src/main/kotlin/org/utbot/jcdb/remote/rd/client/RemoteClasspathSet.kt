package org.utbot.jcdb.remote.rd.client

import com.jetbrains.rd.framework.impl.RdCall
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.utbot.jcdb.api.ByteCodeLocation
import org.utbot.jcdb.api.ClassId
import org.utbot.jcdb.api.ClasspathSet
import org.utbot.jcdb.api.CompilationDatabase
import org.utbot.jcdb.remote.rd.GetClassReq
import org.utbot.jcdb.remote.rd.GetClassRes

class RemoteClasspathSet(
    private val key: String,
    private val getClass: RdCall<GetClassReq, GetClassRes>,
    private val close: RdCall<String, Unit>
) : ClasspathSet {

    override val locations: List<ByteCodeLocation>
        get() = emptyList()

    override val db: CompilationDatabase
        get() = TODO("Not yet implemented")

    override suspend fun refreshed(closeOld: Boolean) = this

    override suspend fun findClassOrNull(name: String): ClassId? {
        val res = getClass.startSuspending(GetClassReq(key, name))
        val node = ClassNode(Opcodes.ASM9).also {
            ClassReader(res.bytes).accept(it, ClassReader.EXPAND_FRAMES)
        }
        val classNode = res.bytes
        return null
    }

    override suspend fun findSubClasses(name: String, allHierarchy: Boolean): List<ClassId> {
        TODO("Not yet implemented")
    }

    override suspend fun findSubClasses(classId: ClassId, allHierarchy: Boolean): List<ClassId> {
        TODO("Not yet implemented")
    }

    override suspend fun <T> query(key: String, term: String): List<T> {
        TODO("Not yet implemented")
    }

    override suspend fun <T> query(key: String, location: ByteCodeLocation, term: String): List<T> {
        TODO("Not yet implemented")
    }

    override fun close() {
        close.start(id)
    }

}