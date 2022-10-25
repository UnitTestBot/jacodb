package org.utbot.jcdb.impl

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.extension.ExtendWith
import org.utbot.jcdb.api.JcClasspath
import org.utbot.jcdb.api.ext.HierarchyExtension
import org.utbot.jcdb.impl.index.hierarchyExt
import org.utbot.jcdb.impl.tests.DatabaseEnvTest

@ExtendWith(CleanDB::class)
class ClassesTest : DatabaseEnvTest() {
    companion object : WithDB()

    override val cp: JcClasspath = runBlocking { db!!.classpath(allClasspath) }

    override val hierarchyExt: HierarchyExtension
        get() = runBlocking { cp.hierarchyExt() }

}

