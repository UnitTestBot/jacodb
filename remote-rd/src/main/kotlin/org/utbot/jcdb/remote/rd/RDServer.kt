package org.utbot.jcdb.remote.rd

import com.jetbrains.rd.framework.*
import com.jetbrains.rd.framework.base.IRdBindable
import com.jetbrains.rd.framework.base.static
import com.jetbrains.rd.framework.impl.RdCall
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.threading.SingleThreadScheduler
import kotlinx.coroutines.runBlocking
import org.utbot.jcdb.api.ClasspathSet
import org.utbot.jcdb.api.Hook
import org.utbot.jcdb.compilationDatabase
import org.utbot.jcdb.impl.CompilationDatabaseImpl
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
        val key = req.locations.joinToString()
        val cp = runBlocking {
            db.classpathSet(req.locations.map { File(it) })
        }
        classpaths[key] = cp
        key
    }

    private val closeClasspath = RdCall<String, Unit>(null, null) { req ->
        classpaths[req]?.close()
    }

    private val getClass = RdCall<GetClassReq, GetClassRes>(null, null) { req ->
        val key = req.cpKey
        val cp = classpaths[key] ?: throw IllegalStateException("No classpath found by key $key. \n Create it first")
        GetClassRes(ByteArray(1))
    }

    override fun afterStart() {
        getClasspath.static(1)
        closeClasspath.static(3)
        getClass.static(2)

        serverProtocol.bindStatic(getClasspath, "get-classpath")
        serverProtocol.bindStatic(closeClasspath, "close-classpath")
        serverProtocol.bindStatic(getClass, "get-class")
    }

    override fun afterStop() {
        lifetimeDef.terminate()
    }

    private fun <T : IRdBindable> IProtocol.bindStatic(x: T, name: String): T {
        x.bind(lifetimeDef, this, name)
        return x
    }

}

fun main() {
    val db = runBlocking {
        compilationDatabase {
            useProcessJavaRuntime()
        } as CompilationDatabaseImpl
    }
    RDServer(8080, db).afterStart()

    val client = RDClient(8080)
    runBlocking {
        val classpathSet = client.classpathSet(emptyList())
        println(classpathSet)
    }
}