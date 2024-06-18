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

import org.jacodb.api.jvm.JcByteCodeLocation
import org.jacodb.api.jvm.JcDatabasePersistence
import org.jacodb.api.jvm.RegisteredLocation
import org.jacodb.api.jvm.storage.ers.getEntityOrNull
import org.jacodb.impl.JCDBSymbolsInternerImpl
import org.jacodb.impl.asSymbolId
import org.jacodb.impl.caches.PluggableCache
import org.jacodb.impl.caches.PluggableCacheProvider
import org.jacodb.impl.caches.xodus.XODUS_CACHE_PROVIDER_ID
import org.jacodb.impl.fs.JavaRuntime
import org.jacodb.impl.fs.asByteCodeLocation
import org.jacodb.impl.fs.logger
import org.jacodb.impl.storage.jooq.tables.references.BYTECODELOCATIONS
import org.jacodb.impl.storage.jooq.tables.references.CLASSES
import java.io.File
import java.time.Duration

abstract class AbstractJcDbPersistence(
    private val javaRuntime: JavaRuntime,
) : JcDatabasePersistence {

    companion object {
        private const val CACHE_PREFIX = "org.jacodb.persistence.caches"
        private val locationsCacheSize = Integer.getInteger("$CACHE_PREFIX.locations", 1_000)
        private val byteCodeCacheSize = Integer.getInteger("$CACHE_PREFIX.bytecode", 10_000)
        private val cacheProvider = PluggableCacheProvider.getProvider(
            System.getProperty("$CACHE_PREFIX.cacheProviderId", XODUS_CACHE_PROVIDER_ID)
        )

        fun <KEY : Any, VALUE : Any> cacheOf(size: Int): PluggableCache<KEY, VALUE> {
            return cacheProvider.newCache {
                maximumSize = size
                expirationDuration = Duration.ofSeconds(
                    Integer.getInteger("$CACHE_PREFIX.expirationDurationSec", 10).toLong()
                )
            }
        }
    }

    private val locationsCache = cacheOf<Long, RegisteredLocation>(locationsCacheSize)
    private val byteCodeCache = cacheOf<Long, ByteArray>(byteCodeCacheSize)

    override val locations: List<JcByteCodeLocation>
        get() {
            return read { context ->
                context.execute(
                    sqlAction = { jooq ->
                        jooq.selectFrom(BYTECODELOCATIONS).fetch().map {
                            PersistentByteCodeLocationData.fromSqlRecord(it)
                        }
                    },
                    noSqlAction = { txn ->
                        txn.all(BytecodeLocationEntity.BYTECODE_LOCATION_ENTITY_TYPE).map {
                            PersistentByteCodeLocationData.fromErsEntity(it)
                        }
                    }
                ).mapNotNull {
                    try {
                        File(it.path).asByteCodeLocation(javaRuntime.version, isRuntime = it.runtime)
                    } catch (e: Exception) {
                        null
                    }
                }
            }
        }

    abstract override val symbolInterner: JCDBSymbolsInternerImpl

    override fun findBytecode(classId: Long): ByteArray {
        return byteCodeCache.get(classId) {
            read { context ->
                context.execute(
                    sqlAction = { jooq ->
                        jooq.select(CLASSES.BYTECODE).from(CLASSES).where(CLASSES.ID.eq(classId)).fetchAny()?.value1()
                    },
                    noSqlAction = { txn ->
                        txn.getEntityOrNull("Class", classId)?.getRawBlob("bytecode")
                    }
                )
            } ?: throw IllegalArgumentException("Can't find bytecode for $classId")
        }
    }

    override fun findSymbolId(symbol: String): Long {
        return symbol.asSymbolId(symbolInterner)
    }

    override fun findSymbolName(symbolId: Long): String {
        return symbolInterner.findSymbolName(symbolId)!!
    }

    override fun findLocation(locationId: Long): RegisteredLocation {
        return locationsCache.get(locationId) {
            val locationData = read { context ->
                context.execute(
                    sqlAction = { jooq ->
                        jooq.fetchOne(BYTECODELOCATIONS, BYTECODELOCATIONS.ID.eq(locationId))
                            ?.let { PersistentByteCodeLocationData.fromSqlRecord(it) }
                    },
                    noSqlAction = { txn ->
                        txn.getEntityOrNull(BytecodeLocationEntity.BYTECODE_LOCATION_ENTITY_TYPE, locationId)
                            ?.let { PersistentByteCodeLocationData.fromErsEntity(it) }
                    }
                ) ?: throw IllegalArgumentException("location not found by id $locationId")
            }
            PersistentByteCodeLocation(
                persistence = this,
                runtimeVersion = javaRuntime.version,
                id = locationId,
                cachedData = locationData,
                cachedLocation = null
            )
        }
    }

    override fun close() {
        try {
            symbolInterner.close()
        } catch (e: Exception) {
            // ignore
        }
    }

    protected val runtimeProcessed: Boolean
        get() {
            try {
                return read { context ->
                    context.execute(
                        sqlAction = { jooq ->
                            val hasBytecodeLocations = jooq.meta().tables.any { it.name.equals(BYTECODELOCATIONS.name, true) }
                            if (!hasBytecodeLocations) {
                                return@execute false
                            }

                            val count = jooq.fetchCount(
                                BYTECODELOCATIONS,
                                BYTECODELOCATIONS.STATE.notEqual(LocationState.PROCESSED.ordinal)
                                    .and(BYTECODELOCATIONS.RUNTIME.isTrue)
                            )
                            count == 0
                        },
                        noSqlAction = { txn ->
                            txn.all(BytecodeLocationEntity.BYTECODE_LOCATION_ENTITY_TYPE).none {
                                it.get<Boolean>(BytecodeLocationEntity.IS_RUNTIME) == true &&
                                        it.get<Int>(BytecodeLocationEntity.STATE) != LocationState.PROCESSED.ordinal
                            }
                        }
                    )
                }
            } catch (e: Exception) {
                logger.warn("can't check that runtime libraries is processed with", e)
                return false
            }
        }
}
