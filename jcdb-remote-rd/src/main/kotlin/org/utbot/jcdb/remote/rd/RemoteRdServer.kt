package org.utbot.jcdb.remote.rd

import com.jetbrains.rd.framework.IdKind
import com.jetbrains.rd.framework.Identities
import com.jetbrains.rd.framework.Protocol
import com.jetbrains.rd.framework.SocketWire
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.threading.SingleThreadScheduler
import mu.KLogging
import org.utbot.jcdb.api.Classpath
import org.utbot.jcdb.api.Hook
import org.utbot.jcdb.api.JCDB
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread


class RemoteRdServer(private val port: Int, private val db: JCDB) : Hook {

    companion object : KLogging()

    private val lifetimeDef = Lifetime.Eternal.createNested()

    private val classpaths = ConcurrentHashMap<String, Classpath>()
    private val scheduler = SingleThreadScheduler(lifetimeDef, "rd-scheduler")

    private val serverProtocol: Protocol

    private val resources: List<CallResource<*, *>>

    init {
        logger.info("starting rd server on $port")
        serverProtocol = Protocol(
            "rd-server",
            serializers,
            Identities(IdKind.Server),
            scheduler,
            SocketWire.Server(lifetimeDef, scheduler, port, allowRemoteConnections = false),
            lifetimeDef
        )
        resources = listOf(
            GetClasspathResource(classpaths),
            CloseClasspathResource(classpaths),
            GetClassResource(classpaths),
            GetSubClassesResource(classpaths),
            CallIndexResource(classpaths),
            StopServerResource(this),
            LoadLocationsResource()
        )
    }


    override fun afterStart() {
        resources.forEach { it.serverCall(serverProtocol, db) }
        scheduler.flush()
    }

    override fun afterStop() {
        logger.info("stopping rd server on $port")
        lifetimeDef.terminate(true)
    }

    internal fun awaitFinishAndShutdown() {
        thread(start = true) {
            while (true) {
                Thread.sleep(50)
                if (scheduler.executor.activeCount == 0) {
                    try {
                        afterStop()
                        return@thread
                    } catch (e: Exception) {
                        logger.error("can't stop server", e)
                    }
                }
            }
        }
    }

}