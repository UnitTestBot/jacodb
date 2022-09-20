package org.utbot.jcdb.remote.rd.client

import com.jetbrains.rd.framework.IdKind
import com.jetbrains.rd.framework.Identities
import com.jetbrains.rd.framework.Protocol
import com.jetbrains.rd.framework.SocketWire
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.threading.SingleThreadScheduler
import org.utbot.jcdb.api.ByteCodeLocation
import org.utbot.jcdb.api.Classpath
import org.utbot.jcdb.api.JCDB
import org.utbot.jcdb.api.JCDBPersistence
import org.utbot.jcdb.api.LocationType
import org.utbot.jcdb.impl.fs.asByteCodeLocation
import org.utbot.jcdb.remote.rd.CallIndexResource
import org.utbot.jcdb.remote.rd.CloseClasspathResource
import org.utbot.jcdb.remote.rd.GetClassResource
import org.utbot.jcdb.remote.rd.GetClasspathReq
import org.utbot.jcdb.remote.rd.GetClasspathResource
import org.utbot.jcdb.remote.rd.GetSubClassesResource
import org.utbot.jcdb.remote.rd.LoadLocationsResource
import org.utbot.jcdb.remote.rd.StopServerResource
import org.utbot.jcdb.remote.rd.serializers
import java.io.File

class RemoteJCDB(port: Int) : JCDB {

    private val lifetimeDef = Lifetime.Eternal.createNested()
    private val scheduler = SingleThreadScheduler(lifetimeDef.lifetime, "rd-scheduler")

    private val clientProtocol = Protocol(
        "rd-client-$port",
        serializers,
        Identities(IdKind.Client),
        scheduler,
        SocketWire.Client(lifetimeDef.lifetime, scheduler, port),
        lifetimeDef.lifetime
    )

    private val getClasspath = GetClasspathResource().clientCall(clientProtocol)
    private val getClass = GetClassResource().clientCall(clientProtocol)
    private val closeClasspath = CloseClasspathResource().clientCall(clientProtocol)
    private val getSubClasses = GetSubClassesResource().clientCall(clientProtocol)
    private val callIndex = CallIndexResource().clientCall(clientProtocol)
    private val stopServer = StopServerResource().clientCall(clientProtocol)

    private val loadLocations = LoadLocationsResource().clientCall(clientProtocol)

    init {
        scheduler.flush()
    }

    override val locations: List<ByteCodeLocation>
        get() = emptyList()

    override suspend fun rebuildFeatures() {
    }

    override val persistence: JCDBPersistence? = null

    override suspend fun classpathSet(dirOrJars: List<File>): Classpath {
        val resp = getClasspath.startSuspending(GetClasspathReq(dirOrJars.map { it.absolutePath }.sorted()))
        return RemoteClasspath(
            resp.key,
            locations = resp.locations.mapIndexed { index, path ->
                File(path).asByteCodeLocation(isRuntime = resp.scopes.get(index) == LocationType.RUNTIME)
            },
            db = this,
            close = closeClasspath,
            getClass = getClass,
            getSubClasses = getSubClasses,
            callIndex = callIndex
        )
    }

    override suspend fun load(dirOrJar: File): JCDB = apply {
        loadLocations.startSuspending(GetClasspathReq(listOf(dirOrJar.absolutePath)))
    }

    override suspend fun load(dirOrJars: List<File>): JCDB = apply {
        loadLocations.startSuspending(GetClasspathReq(dirOrJars.map { it.absolutePath }))
    }

    override suspend fun loadLocations(locations: List<ByteCodeLocation>): JCDB = apply {
        loadLocations.startSuspending(
            GetClasspathReq(locations.map { it.path })
        )
    }

    override suspend fun refresh() {
    }

    override fun watchFileSystemChanges() = this

    override suspend fun awaitBackgroundJobs() {
    }

    override fun close() {
        scheduler.queue {
            stopServer.start(lifetimeDef, Unit)
        }
        scheduler.flush()
        lifetimeDef.terminate()
    }
}


