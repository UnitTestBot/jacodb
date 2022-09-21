package org.utbot.jcdb.impl.persistence

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.utbot.jcdb.api.JCDB
import org.utbot.jcdb.api.ext.HierarchyExtension
import org.utbot.jcdb.impl.LibrariesMixin
import org.utbot.jcdb.impl.index.hierarchyExt
import org.utbot.jcdb.impl.tests.DatabaseEnvTest
import org.utbot.jcdb.jcdb
import java.nio.file.Files

class RestoredDBTest : DatabaseEnvTest() {

    companion object : LibrariesMixin {

        private val jdbcLocation = Files.createTempDirectory("jdbc").toFile().absolutePath

        var tempDb: JCDB? = newDB()

        var db: JCDB? = newDB {
            tempDb?.close()
            tempDb = null
        }

        private fun newDB(before: () -> Unit = {}): JCDB {
            before()
            return runBlocking {
                jcdb {
                    persistent {
                        location = jdbcLocation
                    }
                    predefinedDirOrJars = allClasspath
                    useProcessJavaRuntime()
                }.also {
                    it.awaitBackgroundJobs()
                }
            }
        }


        @AfterAll
        @JvmStatic
        fun cleanup() {
            runBlocking {
                db?.awaitBackgroundJobs()
            }
            db?.close()
            db = null
        }
    }

    override val cp = runBlocking { db!!.classpath(allClasspath) }
    override val hierarchyExt: HierarchyExtension
        get() = cp.hierarchyExt


}

