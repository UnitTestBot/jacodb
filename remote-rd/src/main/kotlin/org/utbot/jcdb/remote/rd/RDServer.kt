package org.utbot.jcdb.remote.rd

import com.jetbrains.rd.framework.*
import com.jetbrains.rd.framework.base.IRdBindable
import com.jetbrains.rd.framework.base.static
import com.jetbrains.rd.framework.impl.RdCall
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.threading.SingleThreadScheduler
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.encodeToByteArray
import org.utbot.jcdb.api.ClasspathSet
import org.utbot.jcdb.api.Hook
import org.utbot.jcdb.compilationDatabase
import org.utbot.jcdb.impl.CompilationDatabaseImpl
import org.utbot.jcdb.impl.types.ClassIdImpl
import org.utbot.jcdb.remote.rd.client.RemoteCompilationDatabase
import java.io.File
import java.util.concurrent.ConcurrentHashMap


class RDServer(port: Int, val db: CompilationDatabaseImpl) : Hook {

    private val lifetimeDef = Lifetime.Eternal.createNested()

    private val classpaths = ConcurrentHashMap<String, ClasspathSet>()
    private val scheduler = SingleThreadScheduler(lifetimeDef, "rd-scheduler")

    private val serverProtocol = Protocol(
        "rd-server",
        serializers,
        Identities(IdKind.Server),
        scheduler,
        SocketWire.Server(lifetimeDef, scheduler, port, allowRemoteConnections = false),
        lifetimeDef
    )

    private val getClasspath = RdCall<GetClasspathReq, String>(null, null) { req ->
        val key = req.locations.sorted().joinToString()
        val cp = runBlocking {
            db.classpathSet(req.locations.map { File(it) })
        }
        classpaths[key] = cp
        key
    }.makeAsync()

    private val closeClasspath = RdCall<String, Unit>(null, null) { req ->
        classpaths[req]?.close()
        classpaths.remove(req)
    }.makeAsync()

    private val getClass = RdCall<GetClassReq, GetClassRes?>(null, null) { req ->
        val key = req.cpKey
        val cp = classpaths[key] ?: throw IllegalStateException("No classpath found by key $key. \n Create it first")
        runBlocking {
            val classId = cp.findClassOrNull(req.className) as? ClassIdImpl
            if (classId != null) {
                val bytes = Cbor.encodeToByteArray(classId.info())
                val url = classId.location.locationURL
                GetClassRes(url.toString(), bytes)
            } else {
                null
            }
        }
    }.makeAsync()

    override fun afterStart() {
        scheduler.invokeOrQueue {
            getClasspath.static(1)
            closeClasspath.static(3)
            getClass.static(2)

            serverProtocol.bindStatic(getClasspath, "get-classpath")
            serverProtocol.bindStatic(closeClasspath, "close-classpath")
            serverProtocol.bindStatic(getClass, "get-class")
        }
        scheduler.flush()
    }

    override fun afterStop() {
        println("TERMINATED: " + lifetimeDef.terminate(true))
    }

    private fun <T : IRdBindable> IProtocol.bindStatic(x: T, name: String): T {
        x.bind(lifetimeDef, this, name)
        return x
    }

}

fun <T, X> RdCall<T, X>.makeAsync(): RdCall<T, X> {
    async = true
    return this
}

fun main() {
    val db = runBlocking {
        compilationDatabase {
            useProcessJavaRuntime()
        } as CompilationDatabaseImpl
    }
    RDServer(8080, db).afterStart()

    val client = RemoteCompilationDatabase(8080)
    runBlocking {
        val classpathSet = client.classpathSet(emptyList())
        println(classpathSet)
    }
}