package org.utbot.jcdb.remote.rd.client

import com.jetbrains.rd.framework.*
import com.jetbrains.rd.framework.base.IRdBindable
import com.jetbrains.rd.framework.base.static
import com.jetbrains.rd.framework.impl.RdCall
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.threading.SingleThreadScheduler
import org.utbot.jcdb.api.ByteCodeLocation
import org.utbot.jcdb.api.ClasspathSet
import org.utbot.jcdb.api.CompilationDatabase
import org.utbot.jcdb.remote.rd.*
import java.io.File

class RemoteCompilationDatabase(port: Int) : CompilationDatabase {

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

    private val getClasspath = RdCall<GetClasspathReq, String>().static(1).makeAsync()
    private val getClass = RdCall<GetClassReq, GetClassRes?>().static(2).makeAsync()
    private val closeClasspath = RdCall<String, Unit>().static(3).makeAsync()

    init {
        scheduler.queue {
            clientProtocol.bindStatic(getClasspath, "client-get-classpath")
            clientProtocol.bindStatic(getClass, "client-get-class")
            clientProtocol.bindStatic(closeClasspath, "client-close-classpath")
        }
        scheduler.flush()
    }

    override suspend fun classpathSet(dirOrJars: List<File>): ClasspathSet {
        val id = getClasspath.startSuspending(GetClasspathReq(dirOrJars.map { it.absolutePath }.sorted()))
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
        x.bind(lifetimeDef.lifetime, this, name)
        return x
    }

    override fun close() {
        lifetimeDef.terminate()
    }
}


