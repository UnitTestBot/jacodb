package org.utbot.jcdb.impl

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.utbot.jcdb.api.ClasspathSet
import org.utbot.jcdb.api.CompilationDatabase
import org.utbot.jcdb.api.ext.HierarchyExtension
import org.utbot.jcdb.compilationDatabase
import org.utbot.jcdb.impl.index.hierarchyExt
import org.utbot.jcdb.impl.tests.DatabaseEnvTest

class ClassesTest : DatabaseEnvTest() {
    companion object : LibrariesMixin {
        var db: CompilationDatabase? = runBlocking {
            compilationDatabase {
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

    override val cp: ClasspathSet = runBlocking { db!!.classpathSet(allClasspath) }

    override val hierarchyExt: HierarchyExtension
        get() = cp.hierarchyExt

}

