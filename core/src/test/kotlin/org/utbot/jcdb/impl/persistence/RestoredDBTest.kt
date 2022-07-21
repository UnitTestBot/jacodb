package org.utbot.jcdb.impl.persistence

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.utbot.jcdb.api.CompilationDatabase
import org.utbot.jcdb.api.ext.HierarchyExtension
import org.utbot.jcdb.compilationDatabase
import org.utbot.jcdb.impl.LibrariesMixin
import org.utbot.jcdb.impl.index.hierarchyExt
import org.utbot.jcdb.impl.tests.DatabaseEnvTest
import java.nio.file.Files

class RestoredDBTest : DatabaseEnvTest() {

    companion object : LibrariesMixin {

        private val jdbcLocation = Files.createTempDirectory("jdbc").toFile().absolutePath

        var tempDb: CompilationDatabase? = newDB()

        var db: CompilationDatabase? = newDB {
            tempDb?.close()
            tempDb = null
        }

        private fun newDB(before: () -> Unit = {}): CompilationDatabase {
            before()
            return runBlocking {
                compilationDatabase {
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

    override val cp = runBlocking { db!!.classpathSet(allClasspath) }
    override val hierarchyExt: HierarchyExtension
        get() = cp.hierarchyExt


}

