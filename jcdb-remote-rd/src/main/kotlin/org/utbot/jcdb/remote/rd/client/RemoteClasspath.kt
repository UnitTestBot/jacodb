package org.utbot.jcdb.remote.rd.client

import com.jetbrains.rd.framework.impl.RdCall
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import org.utbot.jcdb.api.JCDB
import org.utbot.jcdb.api.JcByteCodeLocation
import org.utbot.jcdb.api.JcClassOrInterface
import org.utbot.jcdb.api.JcClasspath
import org.utbot.jcdb.api.PredefinedPrimitives
import org.utbot.jcdb.impl.types.ArrayClassInfo
import org.utbot.jcdb.impl.types.ClassInfo
import org.utbot.jcdb.impl.types.ClassInfoContainer
import org.utbot.jcdb.impl.types.JcArrayClassTypesImpl
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
    override val locations: List<JcByteCodeLocation>,
    private val getClass: RdCall<GetClassReq, GetClassRes?>,
    private val close: RdCall<String, Unit>,
    private val getSubClasses: RdCall<GetSubClassesReq, GetSubClassesRes>,
    private val callIndex: RdCall<CallIndexReq, CallIndexRes>
) : JcClasspath {

    override suspend fun refreshed(closeOld: Boolean) = this

    override suspend fun findClassOrNull(name: String): JcClassOrInterface? {
        return getClass.startSuspending(GetClassReq(key, name))?.asClassId() ?: return null
    }

    override suspend fun findSubClasses(name: String, allHierarchy: Boolean): List<JcClassOrInterface> {
        val res = getSubClasses.startSuspending(GetSubClassesReq(key, name, allHierarchy))
        return res.classes.map { it.asClassId() }
    }

    override suspend fun findSubClasses(jcClass: JcClassOrInterface, allHierarchy: Boolean): List<JcClassOrInterface> {
        return findSubClasses(jcClass.name, allHierarchy)
    }

    override suspend fun <RES: Serializable, REQ: Serializable> query(key: String, req: REQ): Sequence<RES> {
        return (callIndex.startSuspending(CallIndexReq(this.key, key, req)).result as List<RES>).asSequence()
    }

    override fun close() {
        runBlocking {
            close.startSuspending(key)
        }
    }

    private fun ClassInfoContainer.asClassId(location: String?): JcClassOrInterface {
        return when (this) {
            is ArrayClassInfo -> JcArrayClassTypesImpl(elementInfo.asClassId(location))
            is ClassInfo -> RemoteClassId(location, this, this@RemoteClasspath)
            is PredefinedClassInfo -> PredefinedPrimitives.of(name, this@RemoteClasspath)
                ?: throw IllegalStateException("unsupported predefined name $name")
            else -> throw IllegalStateException("unsupported class info container type ${this.javaClass.name}")
        }
    }

    private fun GetClassRes.asClassId(): JcClassOrInterface {
        val info = Cbor.decodeFromByteArray<ClassInfoContainer>(serializedClassInfo)
        return info.asClassId(location)
    }

}