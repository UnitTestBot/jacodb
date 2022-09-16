package org.utbot.jcdb.remote.rd.client

import com.jetbrains.rd.framework.impl.RdCall
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import org.utbot.jcdb.api.ByteCodeLocation
import org.utbot.jcdb.api.ClassId
import org.utbot.jcdb.api.Classpath
import org.utbot.jcdb.api.JCDB
import org.utbot.jcdb.api.PredefinedPrimitives
import org.utbot.jcdb.impl.types.ArrayClassIdImpl
import org.utbot.jcdb.impl.types.ArrayClassInfo
import org.utbot.jcdb.impl.types.ClassInfo
import org.utbot.jcdb.impl.types.ClassInfoContainer
import org.utbot.jcdb.impl.types.PredefinedClassInfo
import org.utbot.jcdb.remote.rd.CallIndexReq
import org.utbot.jcdb.remote.rd.CallIndexRes
import org.utbot.jcdb.remote.rd.GetClassReq
import org.utbot.jcdb.remote.rd.GetClassRes
import org.utbot.jcdb.remote.rd.GetSubClassesReq
import org.utbot.jcdb.remote.rd.GetSubClassesRes
import java.io.Serializable

class RemoteClasspath(
    private val key: String,
    override val db: JCDB,
    override val locations: List<ByteCodeLocation>,
    private val getClass: RdCall<GetClassReq, GetClassRes?>,
    private val close: RdCall<String, Unit>,
    private val getSubClasses: RdCall<GetSubClassesReq, GetSubClassesRes>,
    private val callIndex: RdCall<CallIndexReq, CallIndexRes>
) : Classpath {

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

    override suspend fun <RES: Serializable, REQ: Serializable> query(key: String, req: REQ): Sequence<RES> {
        return (callIndex.startSuspending(CallIndexReq(this.key, key, req)).result as List<RES>).asSequence()
    }

    override fun close() {
        runBlocking {
            close.startSuspending(key)
        }
    }

    private fun ClassInfoContainer.asClassId(location: String?): ClassId {
        return when (this) {
            is ArrayClassInfo -> ArrayClassIdImpl(elementInfo.asClassId(location))
            is ClassInfo -> RemoteClassId(location, this, this@RemoteClasspath)
            is PredefinedClassInfo -> PredefinedPrimitives.of(name, this@RemoteClasspath)
                ?: throw IllegalStateException("unsupported predefined name $name")
            else -> throw IllegalStateException("unsupported class info container type ${this.javaClass.name}")
        }
    }

    private fun GetClassRes.asClassId(): ClassId {
        val info = Cbor.decodeFromByteArray<ClassInfoContainer>(serializedClassInfo)
        return info.asClassId(location)
    }

}