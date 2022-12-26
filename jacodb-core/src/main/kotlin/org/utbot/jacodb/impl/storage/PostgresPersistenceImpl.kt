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

package org.utbot.jacodb.impl.storage

import com.zaxxer.hikari.HikariDataSource
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.conf.Settings
import org.jooq.impl.DSL
import org.utbot.jacodb.impl.FeaturesRegistry
import org.utbot.jacodb.impl.fs.JavaRuntime

class PostgresPersistenceImpl(
    javaRuntime: JavaRuntime,
    featuresRegistry: FeaturesRegistry,
    jcdbUrl: String? = null,
    private val clearOnStart: Boolean
) : AbstractJcDatabasePersistenceImpl(javaRuntime, featuresRegistry, clearOnStart) {

    override val jooq: DSLContext
    private val dataSource: HikariDataSource

    init {
        dataSource = HikariDataSource().also {
            it.maximumPoolSize = 80
            it.transactionIsolation = "TRANSACTION_READ_COMMITTED"
            it.jdbcUrl = jcdbUrl
        }
        jooq = DSL.using(dataSource, SQLDialect.POSTGRES, Settings().withExecuteLogging(false))
        write {
            if (clearOnStart) {
                jooq.executeQueriesFrom("postgres/drop-schema.sql")
            }
            jooq.executeQueriesFrom("postgres/create-schema.sql")
        }
    }

    override fun close() {
        dataSource.close()
    }


    override fun createIndexes() {
        write {
            jooq.executeQueriesFrom("postgres/create-constraint-function.sql", asSingle = true)
            jooq.executeQueriesFrom("postgres/add-indexes.sql")
        }
    }

    override fun getScript(name: String): String {
        return javaClass.classLoader.getResourceAsStream("postgres/$name")?.reader()?.readText()
            ?: throw IllegalStateException("no sql script for postgres/$name found")
    }

}