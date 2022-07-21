package org.utbot.jcdb.remote.rd

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.utbot.jcdb.api.ClasspathSet
import org.utbot.jcdb.api.CompilationDatabase
import org.utbot.jcdb.api.ext.HierarchyExtension
import org.utbot.jcdb.compilationDatabase
import org.utbot.jcdb.impl.LibrariesMixin
import org.utbot.jcdb.impl.index.hierarchyExt
import org.utbot.jcdb.impl.tests.DatabaseEnvTest

class RemoteClientTest: DatabaseEnvTest() {

    companion object : LibrariesMixin {
        private var serverDB: CompilationDatabase? = runBlocking {
            compilationDatabase {
                predefinedDirOrJars = allClasspath
                useProcessJavaRuntime()
                exposeRd(8080)
            }.also {
                it.awaitBackgroundJobs()
            }
        }

        private val remoteDB = remoteRdClient(8080)

        private val remoteCp = runBlocking { remoteDB.classpathSet(allClasspath) }

        @AfterAll
        @JvmStatic
        fun cleanup() {
            serverDB?.close()
            serverDB = null
            remoteCp.close()
            remoteDB.close()
        }
    }

    override val cp: ClasspathSet
        get() = remoteCp

    override val hierarchyExt: HierarchyExtension
        get() = cp.hierarchyExt


}

