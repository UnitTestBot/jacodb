package org.utbot.jcdb.remote.rd.client

import com.jetbrains.rd.framework.impl.RdCall
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import org.utbot.jcdb.api.ByteCodeLocation
import org.utbot.jcdb.api.ClassId
import org.utbot.jcdb.api.ClasspathSet
import org.utbot.jcdb.api.CompilationDatabase
import org.utbot.jcdb.impl.types.*
import org.utbot.jcdb.remote.rd.GetClassReq
import org.utbot.jcdb.remote.rd.GetClassRes

class RemoteClasspathSet(
    private val key: String,
    private val getClass: RdCall<GetClassReq, GetClassRes?>,
    private val close: RdCall<String, Unit>
) : ClasspathSet {

    override val locations: List<ByteCodeLocation>
        get() = emptyList()

    override val db: CompilationDatabase
        get() = TODO("Not yet implemented")

    override suspend fun refreshed(closeOld: Boolean) = this

    override suspend fun findClassOrNull(name: String): ClassId? {
        val res = getClass.startSuspending(GetClassReq(key, name)) ?: return null
        val info = Cbor.decodeFromByteArray<ClassInfoContainer>(res.serializedClassInfo)
        return info.asClassId(res.location)
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
        close.start(key)
    }

    private fun ClassInfoContainer.asClassId(location: String?): ClassId {
        return when (this) {
            is ArrayClassInfo -> ArrayClassIdImpl(elementInfo.asClassId(location))
            is ClassInfo -> RemoteClassId(location, this, this@RemoteClasspathSet)
            is PredefinedClassInfo -> PredefinedPrimitives.of(name, this@RemoteClasspathSet) ?: throw IllegalStateException("unsupported predefined name $name")
            else -> throw IllegalStateException("unsupported class info container type ${this.javaClass.name}")
        }
    }

}