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

package org.jacodb.impl.storage

import org.jacodb.impl.FeaturesRegistry
import org.jacodb.impl.fs.JavaRuntime
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.conf.Settings
import org.jooq.impl.DSL
import org.sqlite.SQLiteConfig
import org.sqlite.SQLiteDataSource
import java.sql.Connection
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class SQLitePersistenceImpl(
    javaRuntime: JavaRuntime,
    featuresRegistry: FeaturesRegistry,
    location: String? = null,
    clearOnStart: Boolean
) : AbstractJcDatabasePersistenceImpl(javaRuntime, featuresRegistry, clearOnStart) {

    private var connection: Connection? = null
    override val jooq: DSLContext

    private val lock = ReentrantLock()

    init {
        val config = SQLiteConfig().also {
            it.setSynchronous(SQLiteConfig.SynchronousMode.OFF)
            it.setJournalMode(SQLiteConfig.JournalMode.OFF)
            it.setPageSize(32_768)
            it.setCacheSize(-8_000)
            it.setSharedCache(true)
        }
        val props = listOfNotNull(
            ("mode" to "memory").takeIf { location == null },
            "rewriteBatchedStatements" to "true",
            "useServerPrepStmts" to "false"
        ).joinToString("&") { "${it.first}=${it.second}" }
        val dataSource = SQLiteDataSource(config).also {
            it.url = "jdbc:sqlite:file:${location ?: ("jcdb-" + UUID.randomUUID())}?$props"
        }
        connection = dataSource.connection
        jooq = DSL.using(connection, SQLDialect.SQLITE, Settings().withExecuteLogging(false))
        write {
            if (clearOnStart) {
                jooq.executeQueriesFrom("sqlite/drop-schema.sql")
            }
            jooq.executeQueriesFrom("sqlite/create-schema.sql")
        }
    }

    override fun <T> write(action: (DSLContext) -> T): T = lock.withLock {
        action(jooq)
    }

    override fun close() {
        super.close()
        try {
            connection?.close()
        } catch (e: Exception) {
            // ignore
        }
    }

    override fun getScript(name: String): String {
        return javaClass.classLoader.getResourceAsStream("sqlite/$name")?.reader()?.readText()
            ?: throw IllegalStateException("no sql script for sqlite/$name found")
    }
}