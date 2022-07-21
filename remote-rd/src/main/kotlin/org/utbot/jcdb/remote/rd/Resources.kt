package org.utbot.jcdb.remote.rd

import com.jetbrains.rd.framework.IMarshaller
import com.jetbrains.rd.framework.IProtocol
import com.jetbrains.rd.framework.Protocol
import com.jetbrains.rd.framework.base.IRdBindable
import com.jetbrains.rd.framework.base.static
import com.jetbrains.rd.framework.impl.RdCall
import com.jetbrains.rd.framework.util.setSuspend
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.encodeToByteArray
import org.utbot.jcdb.api.ClassId
import org.utbot.jcdb.api.ClasspathSet
import org.utbot.jcdb.api.JCDB
import org.utbot.jcdb.impl.types.*
import java.io.File
import java.util.concurrent.ConcurrentHashMap

abstract class CallResource<REQUEST, RESPONSE>(val id: Int, val name: String) {

    private val clientProperty: RdCall<REQUEST, RESPONSE> get() = RdCall<REQUEST, RESPONSE>().static(id).makeAsync()

    private fun serverProperty(db: JCDB): RdCall<REQUEST, RESPONSE> {
        val call = RdCall<REQUEST, RESPONSE>(null, null) { _ -> throw Error() }
        call.setSuspend { _, request ->
            db.handler(request)
        }
        return call.makeAsync().static(id)
    }

    abstract suspend fun JCDB.handler(req: REQUEST): RESPONSE
    open val serializers: List<IMarshaller<*>> = emptyList()

    fun clientCall(protocol: Protocol): RdCall<REQUEST, RESPONSE> {
        return protocol.register("client") { clientProperty }
    }

    fun serverCall(protocol: Protocol, db: JCDB): RdCall<REQUEST, RESPONSE> {
        return protocol.register("server") { serverProperty(db) }
    }

    private fun Protocol.register(prefix: String, getter: () -> RdCall<REQUEST, RESPONSE>): RdCall<REQUEST, RESPONSE> {
        this@CallResource.serializers.forEach {
            serializers.register(it)
        }
        val property = getter()
        scheduler.queue {
            bindStatic(property, "$prefix-${this@CallResource.name}")
        }
        return property
    }


    private fun <T : IRdBindable> IProtocol.bindStatic(x: T, name: String): T {
        x.bind(lifetime, this, name)
        return x
    }

    private fun <T, X> RdCall<T, X>.makeAsync(): RdCall<T, X> {
        async = true
        return this
    }

}

class GetClasspathResource(private val classpaths: ConcurrentHashMap<String, ClasspathSet> = ConcurrentHashMap()) :
    CallResource<GetClasspathReq, String>(1, "get-classpath") {

    override val serializers = listOf(GetClasspathReq)

    override suspend fun JCDB.handler(req: GetClasspathReq): String {
        val key = req.locations.sorted().joinToString()
        val cp = classpathSet(req.locations.map { File(it) })
        classpaths[key] = cp
        return key
    }
}

class LoadLocationsResource() : CallResource<GetClasspathReq, Unit>(10, "load-locations") {

    override val serializers = listOf(GetClasspathReq)

    override suspend fun JCDB.handler(req: GetClasspathReq) {
        load(req.locations.map { File(it) }.filter { it.exists() })
    }
}

class CloseClasspathResource(private val classpaths: ConcurrentHashMap<String, ClasspathSet> = ConcurrentHashMap()) :
    CallResource<String, Unit>(2, "close-classpath") {

    override suspend fun JCDB.handler(req: String) {
        classpaths[req]?.close()
        classpaths.remove(req)
    }
}

abstract class AbstractClasspathResource<REQUEST : ClasspathBasedReq, RESPONSE>(
    id: Int, name: String, private val classpaths: ConcurrentHashMap<String, ClasspathSet> = ConcurrentHashMap()
) : CallResource<REQUEST, RESPONSE>(id, name) {

    override suspend fun JCDB.handler(req: REQUEST): RESPONSE {
        val key = req.cpKey
        val cp = classpaths[key] ?: throw IllegalStateException("No classpath found by key $key. \n Create it first")
        return cp.handler(req)
    }

    protected abstract suspend fun ClasspathSet.handler(req: REQUEST): RESPONSE

    protected suspend fun ClassId.toGetClass(): GetClassRes {
        val bytes = Cbor.encodeToByteArray(convertToContainer())
        val url = location?.locationURL
        return GetClassRes(url?.toString(), bytes)
    }

}


class GetClassResource(classpaths: ConcurrentHashMap<String, ClasspathSet> = ConcurrentHashMap()) :
    AbstractClasspathResource<GetClassReq, GetClassRes?>(3, "get-class", classpaths) {

    override val serializers = listOf(GetClassReq, GetClassRes)

    override suspend fun ClasspathSet.handler(req: GetClassReq): GetClassRes? {
        return findClassOrNull(req.className)?.toGetClass()
    }
}

class GetSubClassesResource(classpaths: ConcurrentHashMap<String, ClasspathSet> = ConcurrentHashMap()) :
    AbstractClasspathResource<GetSubClassesReq, GetSubClassesRes>(4, "get-sub-classes", classpaths) {

    override val serializers = listOf(GetSubClassesRes, GetSubClassesReq)

    override suspend fun ClasspathSet.handler(req: GetSubClassesReq): GetSubClassesRes {
        val subclasses = findSubClasses(req.className, req.allHierarchy).map { it.toGetClass() }
        return GetSubClassesRes(subclasses)
    }
}


class GetGlobalIdResource : CallResource<String, Int>(5, "get-global-id") {

    override suspend fun JCDB.handler(req: String): Int {
        return globalIdStore.getId(req)
    }
}

class GetGlobalNameResource : CallResource<Int, String?>(6, "get-global-name") {

    override suspend fun JCDB.handler(req: Int): String? {
        return globalIdStore.getName(req)
    }
}

class StopServerResource(private val server: RemoteRdServer? = null) : CallResource<Unit, Unit>(7, "stop-server") {

    override suspend fun JCDB.handler(req: Unit) {
        return server!!.awaitFinishAndShutdown()
    }
}

private suspend fun ClassId.convertToContainer(): ClassInfoContainer {
    return when (this) {
        is ArrayClassIdImpl -> ArrayClassInfo(elementClass.convertToContainer())
        is ClassIdImpl -> info()
        is PredefinedPrimitive -> PredefinedClassInfo(name)
        else -> throw IllegalStateException("Can't convert class $name to serializable class info")
    }
}

