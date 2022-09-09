package org.utbot.jcdb.impl

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.utbot.jcdb.api.Classpath
import org.utbot.jcdb.api.JCDB
import org.utbot.jcdb.api.ext.HierarchyExtension
import org.utbot.jcdb.impl.index.hierarchyExt
import org.utbot.jcdb.impl.tests.DatabaseEnvTest
import org.utbot.jcdb.jcdb

class ClassesTest : DatabaseEnvTest() {
    companion object : LibrariesMixin {
        var db: JCDB? = runBlocking {
            jcdb {
                predefinedDirOrJars = allClasspath
                useProcessJavaRuntime()
            }.also {
                it.awaitBackgroundJobs()
            }
        }

        @AfterAll
        @JvmStatic
        fun cleanup() {
            db?.close()
            db = null
        }
    }

    override val cp: Classpath = runBlocking { db!!.classpathSet(allClasspath) }

    override val hierarchyExt: HierarchyExtension
        get() = cp.hierarchyExt

}

