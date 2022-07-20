package org.utbot.jcdb.remote.rd

import com.jetbrains.rd.framework.IdKind
import com.jetbrains.rd.framework.Identities
import com.jetbrains.rd.framework.Protocol
import com.jetbrains.rd.framework.SocketWire
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.threading.SingleThreadScheduler
import kotlinx.coroutines.runBlocking
import org.utbot.jcdb.api.ClasspathSet
import org.utbot.jcdb.api.Hook
import org.utbot.jcdb.compilationDatabase
import org.utbot.jcdb.impl.CompilationDatabaseImpl
import org.utbot.jcdb.remote.rd.client.RemoteCompilationDatabase
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

    private val resources = listOf(
        GetClasspathResource(classpaths),
        CloseClasspathResource(classpaths),
        GetClassResource(classpaths),
        GetSubClassesResource(classpaths),
    )

    override fun afterStart() {
        resources.forEach { it.serverCall(serverProtocol, db) }
        scheduler.flush()
    }

    override fun afterStop() {
        lifetimeDef.terminate(true)
    }

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