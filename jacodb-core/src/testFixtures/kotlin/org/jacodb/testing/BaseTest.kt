/*
 *  Copyright 2022 UnitTestBot contributors (utbot.org)
 * <p>
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * <p>
 *  http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.jacodb.testing

import kotlinx.coroutines.runBlocking
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcClasspathFeature
import org.jacodb.api.JcDatabase
import org.jacodb.api.JcFeature
import org.jacodb.impl.features.Builders
import org.jacodb.impl.features.InMemoryHierarchy
import org.jacodb.impl.features.Usages
import org.jacodb.impl.jacodb
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Tag
import java.nio.file.Files
import kotlin.reflect.full.companionObjectInstance

@Tag("lifecycle")
annotation class LifecycleTest


abstract class BaseTest {

    protected open val cp: JcClasspath = runBlocking {
        val withDB = this@BaseTest.javaClass.withDB
        withDB.db.classpath(allClasspath, withDB.classpathFeatures.toList())
    }

    @AfterEach
    open fun close() {
        cp.close()
    }

}

val Class<*>.withDB: JcDatabaseHolder
    get() {
        val comp = kotlin.companionObjectInstance
        if (comp is JcDatabaseHolder) {
            return comp
        }
        val s = superclass
        if (superclass == null) {
            throw IllegalStateException("can't find WithDB companion object. Please check that test class has it.")
        }
        return s.withDB
    }


interface JcDatabaseHolder {

    val classpathFeatures: List<JcClasspathFeature>
    val db: JcDatabase
    fun cleanup()
}

open class WithDB(vararg features: Any) : JcDatabaseHolder {

    protected var allFeatures = features.toList().toTypedArray()

    init {
        System.setProperty("org.jacodb.impl.storage.defaultBatchSize", "500")
    }

    val dbFeatures = allFeatures.mapNotNull { it as? JcFeature<*, *> }
    override val classpathFeatures = allFeatures.mapNotNull { it as? JcClasspathFeature }

    override var db = runBlocking {
        jacodb {
            // persistent("D:\\work\\jacodb\\jcdb-index.db")
            loadByteCode(allClasspath)
            useProcessJavaRuntime()
            keepLocalVariableNames()
            installFeatures(*dbFeatures.toTypedArray())
        }.also {
            it.awaitBackgroundJobs()
        }
    }

    override  fun cleanup() {
        db.close()
    }
}

val globalDb by lazy {
    WithDB(Usages, Builders, InMemoryHierarchy).db
}

open class WithGlobalDB(vararg _classpathFeatures: JcClasspathFeature): JcDatabaseHolder {

    init {
        System.setProperty("org.jacodb.impl.storage.defaultBatchSize", "500")
    }

    override val classpathFeatures: List<JcClasspathFeature> = _classpathFeatures.toList()

    override val db: JcDatabase get() = globalDb

    override fun cleanup() {
    }
}



open class WithRestoredDB(vararg features: JcFeature<*, *>) : WithDB(*features) {

    private val jdbcLocation = Files.createTempFile("jcdb-", null).toFile().absolutePath

    var tempDb: JcDatabase? = newDB()

    override var db: JcDatabase = newDB {
        tempDb?.close()
        tempDb = null
    }

    private fun newDB(before: () -> Unit = {}): JcDatabase {
        before()
        return runBlocking {
            jacodb {
                persistent(jdbcLocation)
                loadByteCode(allClasspath)
                useProcessJavaRuntime()
                keepLocalVariableNames()
                installFeatures(*dbFeatures.toTypedArray())
            }.also {
                it.awaitBackgroundJobs()
            }
        }
    }

}