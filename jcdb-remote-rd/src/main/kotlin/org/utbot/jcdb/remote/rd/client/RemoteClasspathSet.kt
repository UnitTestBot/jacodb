package org.utbot.jcdb.remote.rd.client

import com.jetbrains.rd.framework.impl.RdCall
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import org.utbot.jcdb.api.*
import org.utbot.jcdb.impl.types.*
import org.utbot.jcdb.remote.rd.*
import java.io.Serializable

class RemoteClasspathSet(
    private val key: String,
    override val db: JCDB,
    override val locations: List<ByteCodeLocation>,
    private val getClass: RdCall<GetClassReq, GetClassRes?>,
    private val close: RdCall<String, Unit>,
    private val getSubClasses: RdCall<GetSubClassesReq, GetSubClassesRes>,
    private val callIndex: RdCall<CallIndexReq, CallIndexRes>
) : ClasspathSet {

    override suspend fun refreshed(closeOld: Boolean) = this

    override suspend fun findClassOrNull(name: String): ClassId? {
        return getClass.startSuspending(GetClassReq(key, name))?.asClassId() ?: return null
    }

    override suspend fun findSubClasses(name: String, allHierarchy: Boolean): List<ClassId> {
        val res = getSubClasses.startSuspending(GetSubClassesReq(key, name, allHierarchy))
        return res.classes.map { it.asClassId() }
    }

    override suspend fun findSubClasses(classId: ClassId, allHierarchy: Boolean): List<ClassId> {
        return findSubClasses(classId.name, allHierarchy)
    }

    override suspend fun <T: Serializable> query(key: String, term: String): List<T> {
        return callIndex.startSuspending(CallIndexReq(this.key, key, null, term)).result as List<T>
    }

    override suspend fun <T: Serializable> query(key: String, location: ByteCodeLocation, term: String): List<T> {
        return callIndex.startSuspending(CallIndexReq(this.key, key, location.path, term)).result as List<T>
    }

    override fun close() {
        runBlocking {
            close.startSuspending(key)
        }
    }

    private fun ClassInfoContainer.asClassId(location: String?): ClassId {
        return when (this) {
            is ArrayClassInfo -> ArrayClassIdImpl(elementInfo.asClassId(location))
            is ClassInfo -> RemoteClassId(location, this, this@RemoteClasspathSet)
            is PredefinedClassInfo -> PredefinedPrimitives.of(name, this@RemoteClasspathSet)
                ?: throw IllegalStateException("unsupported predefined name $name")
            else -> throw IllegalStateException("unsupported class info container type ${this.javaClass.name}")
        }
    }

    private fun GetClassRes.asClassId(): ClassId {
        val info = Cbor.decodeFromByteArray<ClassInfoContainer>(serializedClassInfo)
        return info.asClassId(location)
    }

}