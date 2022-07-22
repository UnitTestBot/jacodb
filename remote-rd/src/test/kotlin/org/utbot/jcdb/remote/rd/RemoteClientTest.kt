package org.utbot.jcdb.remote.rd

import com.jetbrains.rd.framework.util.NetUtils
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.utbot.jcdb.api.ClasspathSet
import org.utbot.jcdb.api.JCDB
import org.utbot.jcdb.api.ext.HierarchyExtension
import org.utbot.jcdb.impl.LibrariesMixin
import org.utbot.jcdb.impl.index.hierarchyExt
import org.utbot.jcdb.impl.tests.DatabaseEnvTest
import org.utbot.jcdb.jcdb

class RemoteClientTest: DatabaseEnvTest() {

    companion object : LibrariesMixin {
        private val port = NetUtils.findFreePort(8080)

        private var serverDB: JCDB? = runBlocking {
            jcdb {
                predefinedDirOrJars = allClasspath
                useProcessJavaRuntime()
                exposeRd(port)
            }.also {
                it.awaitBackgroundJobs()
            }
        }

        private val remoteDB = remoteRdClient(port)

        private val remoteCp = runBlocking { remoteDB.classpathSet(allClasspath) }

        @AfterAll
        @JvmStatic
        fun cleanup() {
            remoteCp.close()
            remoteDB.close()
            serverDB?.close()
            serverDB = null
        }
    }

    override val cp: ClasspathSet
        get() = remoteCp

    override val hierarchyExt: HierarchyExtension
        get() = cp.hierarchyExt


    override fun close() {
        // do nothing
    }

}

