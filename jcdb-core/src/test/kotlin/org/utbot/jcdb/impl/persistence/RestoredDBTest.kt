package org.utbot.jcdb.impl.persistence

import kotlinx.coroutines.runBlocking
import org.utbot.jcdb.api.JcClasspath
import org.utbot.jcdb.api.ext.HierarchyExtension
import org.utbot.jcdb.impl.WithRestoredDB
import org.utbot.jcdb.impl.allClasspath
import org.utbot.jcdb.impl.features.hierarchyExt
import org.utbot.jcdb.impl.tests.DatabaseEnvTest
import org.utbot.jcdb.impl.withDB

class RestoredDBTest : DatabaseEnvTest() {

    companion object : WithRestoredDB()

    override val cp: JcClasspath
        get() = runBlocking {
            val withDB = this@RestoredDBTest.javaClass.withDB
            withDB.db.classpath(allClasspath)
        }

    override val hierarchyExt: HierarchyExtension
        get() = runBlocking { cp.hierarchyExt() }


}

