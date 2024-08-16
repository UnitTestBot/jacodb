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

import mu.KotlinLogging
import org.jacodb.api.jvm.ClassSource
import org.jacodb.api.jvm.JCDBContext
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcDatabase
import org.jacodb.api.jvm.RegisteredLocation
import org.jacodb.api.jvm.storage.ers.EntityRelationshipStorage
import org.jacodb.impl.JCDBSymbolsInternerImpl
import org.jacodb.impl.fs.JavaRuntime
import org.jacodb.impl.fs.PersistenceClassSource
import org.jacodb.impl.fs.info
import org.jacodb.impl.storage.ers.BuiltInBindingProvider
import org.jacodb.impl.storage.ers.sql.SqlEntityRelationshipStorage
import org.jacodb.impl.storage.jooq.tables.references.CLASSES
import org.jacodb.impl.storage.jooq.tables.references.SYMBOLS
import org.jooq.Condition
import org.jooq.SQLDialect
import org.jooq.conf.Settings
import org.jooq.impl.DSL
import java.sql.Connection
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

val defaultBatchSize: Int get() = System.getProperty("org.jacodb.impl.storage.defaultBatchSize", "100").toInt()

class SQLitePersistenceImpl(
    javaRuntime: JavaRuntime,
    clearOnStart: Boolean,
    val location: String?,
) : AbstractJcDbPersistence(javaRuntime) {
    private val dataSource = configuredSQLiteDataSource(location)
    private val connection: Connection = dataSource.connection
    internal val jooq = DSL.using(connection, SQLDialect.SQLITE, Settings().withExecuteLogging(false))
    private val lock = ReentrantLock()
    private val persistenceService = SQLitePersistenceService(this)
    override val ers: EntityRelationshipStorage = SqlEntityRelationshipStorage(dataSource, BuiltInBindingProvider)

    companion object {
        private val logger = KotlinLogging.logger {}
    }

    init {
        if (clearOnStart || !runtimeProcessed) {
            jooq.executeQueriesFrom("sqlite/drop-schema.sql")
        }
        jooq.executeQueriesFrom("sqlite/create-schema.sql")
    }

    override val symbolInterner = JCDBSymbolsInternerImpl().apply { setup(this@SQLitePersistenceImpl) }

    override fun <T> read(action: (JCDBContext) -> T): T {
        return action(toJCDBContext(jooq))
    }

    override fun <T> write(action: (JCDBContext) -> T): T = lock.withLock {
        action(toJCDBContext(jooq))
    }

    override fun close() {
        try {
            ers.close()
            connection.close()
            super.close()
        } catch (e: Exception) {
            logger.warn(e) { "Failed to close SQL persistence" }
        }
    }

    override fun createIndexes() {
        jooq.executeQueries("add-indexes.sql".sqlScript())
    }

    override fun setup() {
        persistenceService.setup()
    }

    override fun persist(location: RegisteredLocation, classes: List<ClassSource>) {
        val allClasses = classes.map { it.info }
        persistenceService.persist(location, allClasses)
    }

    override fun findClassSourceByName(cp: JcClasspath, fullName: String): ClassSource? {
        val symbolId = findSymbolId(fullName)
        return cp.db.classSources(CLASSES.NAME.eq(symbolId).and(cp.clause), single = true).firstOrNull()
    }

    override fun findClassSources(db: JcDatabase, location: RegisteredLocation): List<ClassSource> {
        return db.classSources(CLASSES.LOCATION_ID.eq(location.id))
    }

    override fun findClassSources(cp: JcClasspath, fullName: String): List<ClassSource> {
        val symbolId = findSymbolId(fullName)
        return cp.db.classSources(CLASSES.NAME.eq(symbolId).and(cp.clause))
    }

    private val JcClasspath.clause: Condition
        get() {
            return CLASSES.LOCATION_ID.`in`(registeredLocationIds)
        }

    private fun JcDatabase.classSources(clause: Condition, single: Boolean = false): List<ClassSource> = read { context ->
        val jooq = context.dslContext
        val classesQuery =
            jooq.select(CLASSES.LOCATION_ID, CLASSES.ID, CLASSES.BYTECODE, SYMBOLS.NAME).from(CLASSES).join(SYMBOLS)
                .on(CLASSES.NAME.eq(SYMBOLS.ID)).where(clause)
        val classes = when {
            single -> listOfNotNull(classesQuery.fetchAny())
            else -> classesQuery.fetch()
        }
        classes.map { (locationId, classId, bytecode, name) ->
            PersistenceClassSource(
                db = this, className = name!!, classId = classId!!, locationId = locationId!!, cachedByteCode = bytecode
            )
        }
    }
}

fun String.sqlScript(): String {
    return SQLitePersistenceImpl::class.java.classLoader.getResourceAsStream("sqlite/${this}")?.reader()?.readText()
        ?: throw IllegalStateException("no sql script for sqlite/${this} found")
}
