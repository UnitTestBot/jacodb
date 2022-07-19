package org.utbot.jcdb.remote.rd

import com.jetbrains.rd.framework.*
import com.jetbrains.rd.framework.base.IRdBindable
import com.jetbrains.rd.framework.base.static
import com.jetbrains.rd.framework.impl.RdCall
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.threading.SingleThreadScheduler
import org.utbot.jcdb.api.ByteCodeLocation
import org.utbot.jcdb.api.ClasspathSet
import org.utbot.jcdb.api.CompilationDatabase
import org.utbot.jcdb.remote.rd.client.RemoteClasspathSet
import java.io.File

class RDClient(port: Int) : CompilationDatabase {

    private val lifetimeDef = Lifetime.Eternal.createNested()
    private val scheduler = SingleThreadScheduler(lifetimeDef, "rd-scheduler")

    private val clientProtocol = Protocol(
        "rd-client-$port",
        serializers,
        Identities(IdKind.Client),
        scheduler,
        SocketWire.Client(lifetimeDef, scheduler, port),
        lifetimeDef
    )

    private val getClasspath = RdCall<GetClasspathReq, String>().static(1)
    private val getClass = RdCall<GetClassReq, GetClassRes>().static(2)
    private val closeClasspath = RdCall<String, Unit>().static(3)

    init {
        clientProtocol.bindStatic(getClasspath, "client-get-classpath")
        clientProtocol.bindStatic(getClass, "client-get-class")
        clientProtocol.bindStatic(closeClasspath, "client-close-classpath")
    }

    override suspend fun classpathSet(dirOrJars: List<File>): ClasspathSet {
        val id = getClasspath.startSuspending(GetClasspathReq(dirOrJars.map { it.absolutePath }))
        return RemoteClasspathSet(
            id,
            close = closeClasspath,
            getClass = getClass
        )
    }

    override suspend fun load(dirOrJar: File): CompilationDatabase {
        TODO("Not yet implemented")
    }

    override suspend fun load(dirOrJars: List<File>): CompilationDatabase {
        TODO("Not yet implemented")
    }

    override suspend fun loadLocations(locations: List<ByteCodeLocation>): CompilationDatabase {
        TODO("Not yet implemented")
    }

    override suspend fun refresh() {
    }

    override fun watchFileSystemChanges() = this

    override suspend fun awaitBackgroundJobs() {
    }

    private fun <T : IRdBindable> IProtocol.bindStatic(x: T, name: String): T {
        x.bind(lifetimeDef, this, name)
        return x
    }

    override fun close() {
        lifetimeDef.terminate()
    }
}


