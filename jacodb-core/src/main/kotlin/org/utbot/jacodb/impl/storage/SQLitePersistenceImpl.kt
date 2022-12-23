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
import mu.KLogging
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.conf.Settings
import org.jooq.impl.DSL
import org.utbot.jacodb.api.ClassSource
import org.utbot.jacodb.api.JcByteCodeLocation
import org.utbot.jacodb.api.JcClasspath
import org.utbot.jacodb.api.JcDatabasePersistence
import org.utbot.jacodb.api.RegisteredLocation
import org.utbot.jacodb.impl.FeaturesRegistry
import org.utbot.jacodb.impl.JcInternalSignal
import org.utbot.jacodb.impl.fs.ClassSourceImpl
import org.utbot.jacodb.impl.fs.JavaRuntime
import org.utbot.jacodb.impl.fs.asByteCodeLocation
import org.utbot.jacodb.impl.fs.info
import org.utbot.jacodb.impl.storage.jooq.tables.references.BYTECODELOCATIONS
import org.utbot.jacodb.impl.storage.jooq.tables.references.CLASSES
import org.utbot.jacodb.impl.storage.jooq.tables.references.SYMBOLS
import org.utbot.jacodb.impl.vfs.PersistentByteCodeLocation
import java.io.Closeable
import java.io.File
import java.sql.Connection
import java.util.concurrent.locks.ReentrantLock

class SQLitePersistenceImpl(
    private val javaRuntime: JavaRuntime,
    private val featuresRegistry: FeaturesRegistry,
    location: String? = null,
    private val clearOnStart: Boolean
) : JcDatabasePersistence, Closeable {

    companion object : KLogging() {
        private const val cachesPrefix = "org.utbot.jacodb.persistence.caches"

        private val locationsCacheSize = Integer.getInteger("$cachesPrefix.locations", 1_000).toLong()
        private val byteCodeCacheSize = Integer.getInteger("$cachesPrefix.bytecode", 10_000).toLong()
        private val symbolsCacheSize = Integer.getInteger("$cachesPrefix.symbols", 100_000).toLong()
    }

    private val lock = ReentrantLock()

    private var connection: Connection? = null
    private val persistenceService = PersistenceService(this)
    val jooq: DSLContext

    private val locationsCache = cacheOf<Long, RegisteredLocation>(locationsCacheSize)
    private val byteCodeCache = cacheOf<Long, ByteArray>(byteCodeCacheSize)
    private val symbolsCache = cacheOf<Long, String>(symbolsCacheSize)

    init {
//        val config = SQLiteConfig().also {
//            it.setSynchronous(SQLiteConfig.SynchronousMode.OFF)
//            it.setJournalMode(SQLiteConfig.JournalMode.OFF)
//            it.setPageSize(32_768)
//            it.setCacheSize(-8_000)
//            it.setSharedCache(true)
//        }
//        val props = listOfNotNull(
//            ("mode" to "memory").takeIf { location == null },
//            "rewriteBatchedStatements" to "true",
//            "useServerPrepStmts" to "false"
//        ).joinToString("&") { "${it.first}=${it.second}" }

        val dataSource = HikariDataSource().also {
            it.maximumPoolSize = 80
            it.transactionIsolation = "TRANSACTION_READ_COMMITTED"
            it.jdbcUrl = "jdbc:postgresql://localhost:5432/jacodb?user=postgres&password=root&reWriteBatchedInserts=true"
        }
        jooq = DSL.using(dataSource, SQLDialect.POSTGRES, Settings().withExecuteLogging(false))
        write {
            if (clearOnStart) {
                jooq.executeQueriesFrom("postgres/jcdb-drop-schema.sql")
            }
            jooq.executeQueriesFrom("postgres/jcdb-create-schema.sql")
        }
    }

    override fun setup() {
        write {
            featuresRegistry.broadcast(JcInternalSignal.BeforeIndexing(clearOnStart))
        }
        persistenceService.setup()
    }

    override val locations: List<JcByteCodeLocation>
        get() {
            return jooq.selectFrom(BYTECODELOCATIONS).fetch().mapNotNull {
                try {
                    File(it.path!!).asByteCodeLocation(javaRuntime.version, isRuntime = it.runtime!!)
                } catch (e: Exception) {
                    null
                }
            }.toList()
        }

    override fun newSymbolInterner() = persistenceService.newSymbolInterner()

    override fun findBytecode(classId: Long): ByteArray {
        return byteCodeCache.get(classId) {
            jooq.select(CLASSES.BYTECODE).from(CLASSES)
                .where(CLASSES.ID.eq(classId)).fetchAny()?.value1()
                ?: throw IllegalArgumentException("Can't find bytecode for $classId")
        }
    }

    override fun <T> write(action: (DSLContext) -> T): T {
        return action(jooq)
    }

    override fun <T> read(action: (DSLContext) -> T): T {
        return action(jooq)
    }

    override fun findSymbolId(symbol: String): Long? {
        return persistenceService.findSymbolId(symbol)
    }

    override fun findSymbolName(symbolId: Long): String {
        return symbolsCache.get(symbolId) {
            persistenceService.findSymbolName(symbolId)
        }
    }

    override fun findLocation(locationId: Long): RegisteredLocation {
        return locationsCache.get(locationId) {
            val record = jooq.fetchOne(BYTECODELOCATIONS, BYTECODELOCATIONS.ID.eq(locationId))
                ?: throw IllegalArgumentException("location not found by id $locationId")
            PersistentByteCodeLocation(this, runtimeVersion = javaRuntime.version, locationId, record, null)
        }
    }

    override fun findClassSourceByName(
        cp: JcClasspath,
        locations: List<RegisteredLocation>,
        fullName: String
    ): ClassSource? {
        val ids = locations.map { it.id }
        val symbolId = findSymbolId(fullName) ?: return null
        val found = jooq.select(CLASSES.LOCATION_ID, CLASSES.BYTECODE).from(CLASSES)
            .where(CLASSES.NAME.eq(symbolId).and(CLASSES.LOCATION_ID.`in`(ids)))
            .fetchAny() ?: return null
        val locationId = found.component1()!!
        val byteCode = found.component2()!!
        return ClassSourceImpl(
            location = PersistentByteCodeLocation(cp, locationId),
            className = fullName,
            byteCode = byteCode
        )
    }

    override fun findClassSources(location: RegisteredLocation): List<ClassSource> {
        val classes = jooq.select(CLASSES.LOCATION_ID, CLASSES.BYTECODE, SYMBOLS.NAME).from(CLASSES)
            .join(SYMBOLS).on(CLASSES.NAME.eq(SYMBOLS.ID))
            .where(CLASSES.LOCATION_ID.eq(location.id))
            .fetch()
        return classes.map { (locationId, array, name) ->
            ClassSourceImpl(
                location = location,
                className = name!!,
                byteCode = array!!
            )
        }
    }

    override fun persist(location: RegisteredLocation, classes: List<ClassSource>) {
        val allClasses = classes.map { it.info }
        persistenceService.persist(location, allClasses)
    }

    override fun close() {
        try {
            connection?.close()
        } catch (e: Exception) {
            // ignore
        }
    }

}