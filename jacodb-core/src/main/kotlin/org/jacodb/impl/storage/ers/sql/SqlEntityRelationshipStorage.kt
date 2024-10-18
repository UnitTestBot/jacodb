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

package org.jacodb.impl.storage.ers.sql

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jacodb.api.storage.ers.BindingProvider
import org.jacodb.api.storage.ers.ERSConflictingTransactionException
import org.jacodb.api.storage.ers.EntityRelationshipStorage
import org.jacodb.api.storage.ers.Transaction
import org.jacodb.impl.storage.ers.decorators.withAllDecorators
import org.jacodb.impl.storage.ers.jooq.tables.references.TYPES
import org.jacodb.impl.storage.ers.sql.SqlErsNames.ENTITY_ID_FIELD
import org.jacodb.impl.storage.ers.sql.SqlErsNames.ENTITY_TABLE_PREFIX
import org.jacodb.impl.storage.executeQueriesFrom
import org.jacodb.impl.util.allCauses
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.conf.Settings
import org.jooq.impl.DSL
import org.jooq.impl.DefaultExecuteListenerProvider
import org.sqlite.SQLiteDataSource
import java.sql.Connection
import java.sql.SQLException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

private fun Connection.createSQLiteDSLContext(): DSLContext {
    val settings = Settings().withExecuteLogging(false)
    val configuration = DSL.using(/*connection = */ this, SQLDialect.SQLITE, settings)
        .configuration()
        .derive(DefaultExecuteListenerProvider(SqlErsExceptionTransformer()))

    return DSL.using(configuration).also {
        // Fixes CASCADE DELETE
        it.execute("PRAGMA foreign_keys = ON;")
    }
}

class SqlEntityRelationshipStorage(
    dataSource: SQLiteDataSource,
    bindingProvider: BindingProvider
) : EntityRelationshipStorage, BindingProvider by bindingProvider {
    private val primaryConnection: Connection = dataSource.connection
    private val jooq: DSLContext = primaryConnection.createSQLiteDSLContext()
    private val connectionPool = run {
        val config = HikariConfig()
        config.dataSource = dataSource
        config.connectionTimeout = 15_000
        HikariDataSource(config)
    }

    init {
        jooq.executeQueriesFrom("ers/sqlite/create-schema.sql")
    }

    private val entityIdGen = AtomicLong(
        jooq.meta().tables
            .filter { it.name.startsWith(ENTITY_TABLE_PREFIX) }
            .maxOfOrNull { table -> jooq.select(DSL.max(ENTITY_ID_FIELD)).from(table).fetchAny()?.component1() ?: 0L }
            ?: 0L
    )

    private val typeIdGen = AtomicInteger(
        jooq.select(DSL.max(TYPES.ID)).from(TYPES).fetchAny()?.component1() ?: 0
    )

    override fun beginTransaction(readonly: Boolean): Transaction {
        return SqlErsTransactionImpl(object : SqlErsContext {
            override val connection: Connection = getConnectionFromPool()
            override val jooq: DSLContext = connection.createSQLiteDSLContext()
            override val ers: EntityRelationshipStorage = this@SqlEntityRelationshipStorage
            override fun nextEntityId(): Long = entityIdGen.incrementAndGet()
            override fun nextTypeId(): Int = typeIdGen.incrementAndGet()
        }).withAllDecorators()
    }

    private fun getConnectionFromPool(): Connection = try {
        connectionPool.connection
    } catch (e: SQLException) {
        if (e.allCauses().any { it.message?.contains("SQLITE_LOCKED_SHAREDCACHE") == true }) {
            throw ERSConflictingTransactionException(
                "Cannot begin a transaction since a parallel one has locked a shared resource", e
            )
        } else {
            throw e
        }
    }

    override fun close() {
        connectionPool.close()
        primaryConnection.close()
    }
}
