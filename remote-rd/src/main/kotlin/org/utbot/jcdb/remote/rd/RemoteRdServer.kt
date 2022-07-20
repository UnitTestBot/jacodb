package org.utbot.jcdb.remote.rd

import com.jetbrains.rd.framework.IdKind
import com.jetbrains.rd.framework.Identities
import com.jetbrains.rd.framework.Protocol
import com.jetbrains.rd.framework.SocketWire
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.threading.SingleThreadScheduler
import org.utbot.jcdb.api.ClasspathSet
import org.utbot.jcdb.api.Hook
import org.utbot.jcdb.impl.CompilationDatabaseImpl
import java.util.concurrent.ConcurrentHashMap


class RemoteRdServer(port: Int, val db: CompilationDatabaseImpl) : Hook {

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