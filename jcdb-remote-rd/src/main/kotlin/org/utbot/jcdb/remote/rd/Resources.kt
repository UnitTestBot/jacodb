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
import org.utbot.jcdb.api.Classpath
import org.utbot.jcdb.api.JCDB
import org.utbot.jcdb.api.PredefinedPrimitive
import org.utbot.jcdb.api.PredefinedPrimitives
import org.utbot.jcdb.impl.types.ArrayClassIdImpl
import org.utbot.jcdb.impl.types.ArrayClassInfo
import org.utbot.jcdb.impl.types.ClassIdImpl
import org.utbot.jcdb.impl.types.ClassInfoContainer
import org.utbot.jcdb.impl.types.PredefinedClassInfo
import java.io.File
import java.io.Serializable
import java.security.MessageDigest
import java.util.*
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

class GetClasspathResource(private val classpaths: ConcurrentHashMap<String, Classpath> = ConcurrentHashMap()) :
    CallResource<GetClasspathReq, GetClasspathRes>(1, "get-classpath") {

    override val serializers = listOf(GetClasspathReq, GetClasspathRes)

    override suspend fun JCDB.handler(req: GetClasspathReq): GetClasspathRes {
        val key = req.locations.sorted().joinToString().sha1Hash
        val cp = classpathSet(req.locations.map { File(it) })
        classpaths[key] = cp
        return GetClasspathRes(key, cp.locations.map { it.path }, cp.locations.map { it.scope })
    }
}

class LoadLocationsResource() : CallResource<GetClasspathReq, Unit>(10, "load-locations") {

    override val serializers = listOf(GetClasspathReq)

    override suspend fun JCDB.handler(req: GetClasspathReq) {
        load(req.locations.map { File(it) }.filter { it.exists() })
    }
}

class CloseClasspathResource(private val classpaths: ConcurrentHashMap<String, Classpath> = ConcurrentHashMap()) :
    CallResource<String, Unit>(2, "close-classpath") {

    override suspend fun JCDB.handler(req: String) {
        classpaths[req]?.close()
        classpaths.remove(req)
    }
}

abstract class AbstractClasspathResource<REQUEST : ClasspathBasedReq, RESPONSE>(
    id: Int, name: String, private val classpaths: ConcurrentHashMap<String, Classpath> = ConcurrentHashMap()
) : CallResource<REQUEST, RESPONSE>(id, name) {

    override suspend fun JCDB.handler(req: REQUEST): RESPONSE {
        val key = req.cpKey
        val cp = classpaths[key] ?: throw IllegalStateException("No classpath found by key $key. \n Create it first")
        return cp.handler(req)
    }

    protected abstract suspend fun Classpath.handler(req: REQUEST): RESPONSE

    protected suspend fun ClassId.toGetClass(): GetClassRes {
        val bytes = Cbor.encodeToByteArray(convertToContainer())
        return GetClassRes(location?.path, bytes)
    }

}


class GetClassResource(classpaths: ConcurrentHashMap<String, Classpath> = ConcurrentHashMap()) :
    AbstractClasspathResource<GetClassReq, GetClassRes?>(3, "get-class", classpaths) {

    override val serializers = listOf(GetClassReq, GetClassRes)

    override suspend fun Classpath.handler(req: GetClassReq): GetClassRes? {
        return findClassOrNull(req.className)?.toGetClass()
    }
}

class GetSubClassesResource(classpaths: ConcurrentHashMap<String, Classpath> = ConcurrentHashMap()) :
    AbstractClasspathResource<GetSubClassesReq, GetSubClassesRes>(4, "get-sub-classes", classpaths) {

    override val serializers = listOf(GetSubClassesRes, GetSubClassesReq)

    override suspend fun Classpath.handler(req: GetSubClassesReq): GetSubClassesRes {
        val subclasses = findSubClasses(req.className, req.allHierarchy).map { it.toGetClass() }
        return GetSubClassesRes(subclasses)
    }
}


class GetGlobalIdResource : CallResource<String, Int>(5, "get-global-id") {

    override suspend fun JCDB.handler(req: String): Int {
        return symbolIdStorage.findOrNewId(req)
    }
}

class GetGlobalNameResource : CallResource<Int, String?>(6, "get-global-name") {

    override suspend fun JCDB.handler(req: Int): String? {
        return symbolIdStorage.findNameOrNull(req)
    }
}

class StopServerResource(private val server: RemoteRdServer? = null) : CallResource<Unit, Unit>(7, "stop-server") {

    override suspend fun JCDB.handler(req: Unit) {
        return server!!.awaitFinishAndShutdown()
    }
}

class CallIndexResource(classpaths: ConcurrentHashMap<String, Classpath> = ConcurrentHashMap()) :
    AbstractClasspathResource<CallIndexReq, CallIndexRes>(8, "call-index", classpaths) {

    override val serializers = listOf(CallIndexReq, CallIndexRes)

    override suspend fun Classpath.handler(req: CallIndexReq): CallIndexRes {
        val location = req.location
        val result = if (location == null) {
            query(req.indexKey, req.term)
        } else {
            val byteCodeLocation = locations.first { it.path == location }
            query<Serializable>(req.indexKey, byteCodeLocation, req.term)
        }
        val first = result.firstOrNull() ?: return CallIndexRes("unknown", emptyList<Serializable>())
        return when (first::class.java) {
            java.lang.String::class.java -> CallIndexRes("string", result as List<String>)

            java.lang.Boolean::class.java -> CallIndexRes(PredefinedPrimitives.boolean, result as List<Boolean>)
            java.lang.Byte::class.java -> CallIndexRes(PredefinedPrimitives.byte, result as List<Byte>)
            java.lang.Character::class.java -> CallIndexRes(PredefinedPrimitives.char, result as List<Char>)
            java.lang.Integer::class.java -> CallIndexRes(PredefinedPrimitives.int, result as List<Int>)
            java.lang.Long::class.java -> CallIndexRes(PredefinedPrimitives.long, result as List<Long>)
            java.lang.Float::class.java -> CallIndexRes(PredefinedPrimitives.float, result as List<Float>)
            java.lang.Double::class.java -> CallIndexRes(PredefinedPrimitives.double, result as List<Double>)
            else -> CallIndexRes("unknown", emptyList<Serializable>())
        }
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

private val String.sha1Hash: String
    get() {
        val md = MessageDigest.getInstance("SHA-1")
        return Base64.getEncoder().encodeToString(md.digest(toByteArray()))
    }
