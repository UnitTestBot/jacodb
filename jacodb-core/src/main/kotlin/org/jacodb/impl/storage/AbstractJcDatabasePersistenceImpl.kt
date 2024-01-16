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

import org.jacodb.api.jvm.ClassSource
import org.jacodb.api.jvm.JcByteCodeLocation
import org.jacodb.api.jvm.JcProject
import org.jacodb.api.jvm.JcDatabase
import org.jacodb.api.jvm.JcDatabasePersistence
import org.jacodb.api.jvm.RegisteredLocation
import org.jacodb.impl.FeaturesRegistry
import org.jacodb.impl.JcInternalSignal
import org.jacodb.impl.fs.JavaRuntime
import org.jacodb.impl.fs.PersistenceClassSource
import org.jacodb.impl.fs.asByteCodeLocation
import org.jacodb.impl.fs.info
import org.jacodb.impl.storage.jooq.tables.references.BYTECODELOCATIONS
import org.jacodb.impl.storage.jooq.tables.references.CLASSES
import org.jacodb.impl.storage.jooq.tables.references.SYMBOLS
import org.jacodb.impl.vfs.PersistentByteCodeLocation
import org.jooq.Condition
import org.jooq.DSLContext
import java.io.Closeable
import java.io.File

val defaultBatchSize: Int get() = System.getProperty("org.jacodb.impl.storage.defaultBatchSize", "100").toInt()

abstract class AbstractJcDatabasePersistenceImpl(
    private val javaRuntime: JavaRuntime,
    private val featuresRegistry: FeaturesRegistry,
    private val clearOnStart: Boolean
) : JcDatabasePersistence, Closeable {

    companion object {
        private const val cachesPrefix = "org.utbot.jacodb.persistence.caches"
        private val locationsCacheSize = Integer.getInteger("$cachesPrefix.locations", 1_000).toLong()
        private val byteCodeCacheSize = Integer.getInteger("$cachesPrefix.bytecode", 10_000).toLong()
        private val symbolsCacheSize = Integer.getInteger("$cachesPrefix.symbols", 100_000).toLong()
    }

    private val persistenceService = PersistenceService(this)

    abstract val jooq: DSLContext

    private val locationsCache = cacheOf<Long, RegisteredLocation>(locationsCacheSize)
    private val byteCodeCache = cacheOf<Long, ByteArray>(byteCodeCacheSize)
    private val symbolsCache = cacheOf<Long, String>(symbolsCacheSize)

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

    override val symbolInterner by lazy {
        JCDBSymbolsInternerImpl(jooq).also { it.setup() }
    }

    override fun setup() {
        write {
            featuresRegistry.broadcast(JcInternalSignal.BeforeIndexing(clearOnStart))
        }
        persistenceService.setup()
    }

    override fun findBytecode(classId: Long): ByteArray {
        return byteCodeCache.get(classId) {
            jooq.select(CLASSES.BYTECODE).from(CLASSES)
                .where(CLASSES.ID.eq(classId)).fetchAny()?.value1()
                ?: throw IllegalArgumentException("Can't find bytecode for $classId")
        }
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

    override fun findClassSourceByName(cp: JcProject, fullName: String): ClassSource? {
        val symbolId = findSymbolId(fullName) ?: return null
        return cp.db.classSources(CLASSES.NAME.eq(symbolId).and(cp.clause), single = true).firstOrNull()
    }

    override fun findClassSources(db: JcDatabase, location: RegisteredLocation): List<ClassSource> {
        return db.classSources(CLASSES.LOCATION_ID.eq(location.id))
    }

    override fun findClassSources(cp: JcProject, fullName: String): List<ClassSource> {
        val symbolId = findSymbolId(fullName) ?: return emptyList()
        return cp.db.classSources(CLASSES.NAME.eq(symbolId).and(cp.clause))
    }

    private val JcProject.clause: Condition
        get() {
            val ids = registeredLocations.map { it.id }
            return CLASSES.LOCATION_ID.`in`(ids)
        }

    private fun JcDatabase.classSources(clause: Condition, single: Boolean = false): List<ClassSource> {
        val classesQuery = jooq.select(CLASSES.LOCATION_ID, CLASSES.ID, CLASSES.BYTECODE, SYMBOLS.NAME).from(CLASSES)
            .join(SYMBOLS).on(CLASSES.NAME.eq(SYMBOLS.ID))
            .where(clause)
        val classes = when {
            single -> listOfNotNull(classesQuery.fetchAny())
            else -> classesQuery.fetch()
        }
        return classes.map { (locationId, classId, bytecode, name) ->
            PersistenceClassSource(
                db = this,
                className = name!!,
                classId = classId!!,
                locationId = locationId!!,
                cachedByteCode = bytecode
            )
        }
    }

    override fun persist(location: RegisteredLocation, classes: List<ClassSource>) {
        val allClasses = classes.map { it.info }
        persistenceService.persist(location, allClasses)
    }

    override fun close() {
        locationsCache.invalidateAll()
        symbolsCache.invalidateAll()
        byteCodeCache.invalidateAll()
        symbolInterner.setup()
    }
}