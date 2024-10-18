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

import org.jacodb.api.jvm.JCDBContext
import org.jacodb.api.jvm.JcByteCodeLocation
import org.jacodb.api.jvm.LocationType
import org.jacodb.api.jvm.RegisteredLocation
import org.jacodb.api.storage.ers.getEntityOrNull
import org.jacodb.impl.CleanupResult
import org.jacodb.impl.JcDatabaseImpl
import org.jacodb.impl.JcInternalSignal
import org.jacodb.impl.LocationsRegistry
import org.jacodb.impl.LocationsRegistrySnapshot
import org.jacodb.impl.RefreshResult
import org.jacodb.impl.RegistrationResult
import org.jacodb.impl.storage.jooq.tables.records.BytecodelocationsRecord
import org.jacodb.impl.storage.jooq.tables.references.BYTECODELOCATIONS
import java.sql.Types
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentHashMap.KeySetView
import java.util.concurrent.atomic.AtomicLong

class PersistentLocationsRegistry(private val jcdb: JcDatabaseImpl) : LocationsRegistry {

    private val persistence = jcdb.persistence

    // non-null only for SQL-based persistence
    private val idGen: AtomicLong? = persistence.read { context ->
        context.execute(
            sqlAction = { jooq -> AtomicLong(BYTECODELOCATIONS.ID.maxId(jooq) ?: 0) },
            noSqlAction = { null }
        )
    }

    init {
        persistence.write { context ->
            context.execute(
                sqlAction = { jooq ->
                    jooq.update(BYTECODELOCATIONS)
                        .set(BYTECODELOCATIONS.STATE, LocationState.OUTDATED.ordinal)
                        .where(BYTECODELOCATIONS.STATE.notEqual(LocationState.PROCESSED.ordinal))
                        .execute()
                },
                noSqlAction = { txn ->
                    txn.all(BytecodeLocationEntity.BYTECODE_LOCATION_ENTITY_TYPE)
                        .filter { it.get<LocationState>(BytecodeLocationEntity.STATE) != LocationState.PROCESSED }
                        .forEach { it[BytecodeLocationEntity.STATE] = LocationState.OUTDATED.ordinal }
                }
            )
        }
    }

    override val actualLocations: List<PersistentByteCodeLocation>
        get() = persistence.read { context ->
            context.execute(
                sqlAction = { jooq ->
                    jooq.selectFrom(BYTECODELOCATIONS).fetch { record ->
                        PersistentByteCodeLocation(
                            jcdb,
                            PersistentByteCodeLocationData.fromSqlRecord(record)
                        )
                    }
                },
                noSqlAction = { txn ->
                    txn.all(BytecodeLocationEntity.BYTECODE_LOCATION_ENTITY_TYPE).map { entity ->
                        PersistentByteCodeLocation(
                            jcdb,
                            PersistentByteCodeLocationData.fromErsEntity(entity)
                        )
                    }.toList()
                }
            )
        }

    private val notRuntimeLocations: List<PersistentByteCodeLocation>
        get() = persistence.read { context ->
            context.execute(
                sqlAction = { jooq ->
                    jooq.selectFrom(BYTECODELOCATIONS).where(BYTECODELOCATIONS.RUNTIME.ne(true)).fetch { record ->
                        PersistentByteCodeLocation(
                            jcdb,
                            PersistentByteCodeLocationData.fromSqlRecord(record)
                        )
                    }
                },
                noSqlAction = { txn ->
                    txn.find(
                        type = BytecodeLocationEntity.BYTECODE_LOCATION_ENTITY_TYPE,
                        propertyName = BytecodeLocationEntity.IS_RUNTIME,
                        value = false
                    ).map { entity ->
                        PersistentByteCodeLocation(
                            jcdb,
                            PersistentByteCodeLocationData.fromErsEntity(entity)
                        )
                    }.toList()
                }
            )
        }

    override lateinit var runtimeLocations: List<RegisteredLocation>

    override val snapshots: KeySetView<LocationsRegistrySnapshot, Boolean> = ConcurrentHashMap.newKeySet()

    private fun JCDBContext.save(location: JcByteCodeLocation) =
        PersistentByteCodeLocation(jcdb, location.findOrNew(this), location)

    override fun setup(runtimeLocations: List<JcByteCodeLocation>): RegistrationResult {
        return registerIfNeeded(runtimeLocations).also {
            this.runtimeLocations = it.registered
        }
    }
//
//    fun restorePure() {
//        runtimeLocations = persistence.read {
//            it.selectFrom(BYTECODELOCATIONS)
//                .where(BYTECODELOCATIONS.RUNTIME.eq(true))
//                .fetch {
//                    PersistentByteCodeLocation(jcdb, it.id!!)
//                }
//        }
//    }

    override fun afterProcessing(locations: List<RegisteredLocation>) {
        val ids = locations.map { it.id }
        persistence.write { context ->
            context.execute(
                sqlAction = { jooq ->
                    jooq.update(BYTECODELOCATIONS)
                        .set(BYTECODELOCATIONS.STATE, LocationState.PROCESSED.ordinal)
                        .where(BYTECODELOCATIONS.ID.`in`(ids))
                        .execute()
                },
                noSqlAction = { txn ->
                    ids.forEach { id ->
                        val entity = txn.getEntityOrNull(BytecodeLocationEntity.BYTECODE_LOCATION_ENTITY_TYPE, id)
                        entity?.set(BytecodeLocationEntity.STATE, LocationState.PROCESSED.ordinal)
                    }
                }
            )
        }
        jcdb.featuresRegistry.broadcast(JcInternalSignal.AfterIndexing)
    }

    override fun registerIfNeeded(locations: List<JcByteCodeLocation>): RegistrationResult {
        val uniqueLocations = locations.toSet()
        return persistence.write { context ->
            val result = arrayListOf<RegisteredLocation>()
            val toAdd = arrayListOf<JcByteCodeLocation>()
            val fsIds = uniqueLocations.map { it.fileSystemId }
            val existed = context.execute(
                sqlAction = { jooq ->
                    jooq.selectFrom(BYTECODELOCATIONS).where(BYTECODELOCATIONS.UNIQUEID.`in`(fsIds)).map { record ->
                        PersistentByteCodeLocationData.fromSqlRecord(record)
                    }
                },
                noSqlAction = { txn ->
                    fsIds.flatMap { fsId ->
                        txn.find(
                            type = BytecodeLocationEntity.BYTECODE_LOCATION_ENTITY_TYPE,
                            propertyName = BytecodeLocationEntity.FILE_SYSTEM_ID,
                            value = fsId
                        )
                    }.map { entity -> PersistentByteCodeLocationData.fromErsEntity(entity) }
                }
            ).associateBy { it.fileSystemId }

            uniqueLocations.forEach {
                val found = existed[it.fileSystemId]
                if (found == null) {
                    toAdd += it
                } else {
                    result += PersistentByteCodeLocation(jcdb, found, it)
                }
            }
            val records = context.execute(
                sqlAction = { jooq ->
                    val records = toAdd.map { add ->
                        idGen!!.incrementAndGet() to add
                    }
                    jooq.connection {
                        it.insertElements(BYTECODELOCATIONS, records) { (id, location) ->
                            setLong(1, id)
                            setString(2, location.path)
                            setString(3, location.fileSystemId)
                            setBoolean(4, location.type == LocationType.RUNTIME)
                            setInt(5, LocationState.INITIAL.ordinal)
                            setNull(6, Types.BIGINT)
                        }
                    }
                    records
                },
                noSqlAction = { txn ->
                    toAdd.map { location ->
                        val entity = txn.newEntity(BytecodeLocationEntity.BYTECODE_LOCATION_ENTITY_TYPE)
                        entity[BytecodeLocationEntity.PATH] = location.path
                        entity[BytecodeLocationEntity.FILE_SYSTEM_ID] = location.fileSystemId
                        entity[BytecodeLocationEntity.IS_RUNTIME] = location.type == LocationType.RUNTIME
                        entity[BytecodeLocationEntity.STATE] = LocationState.INITIAL.ordinal
                        entity.id.instanceId to location
                    }
                }
            )
            val added = records.map {
                PersistentByteCodeLocation(
                    jcdb.persistence,
                    jcdb.runtimeVersion,
                    it.first,
                    null,
                    it.second
                )
            }
            RegistrationResult(result + added, added)
        }
    }

    private fun JCDBContext.deprecate(locations: List<RegisteredLocation>) {
        locations.forEach {
            jcdb.featuresRegistry.broadcast(JcInternalSignal.LocationRemoved(it))
        }
        val locationIds = locations.map { it.id }.toSet()
        execute(
            sqlAction = { jooq ->
                jooq.deleteFrom(BYTECODELOCATIONS).where(BYTECODELOCATIONS.ID.`in`(locationIds)).execute()
            },
            noSqlAction = { txn ->
                txn.all(BytecodeLocationEntity.BYTECODE_LOCATION_ENTITY_TYPE)
                    .filter { it.id.instanceId in locationIds }
                    .forEach { it.delete() }
            }
        )

    }

    override fun refresh(): RefreshResult {
        val deprecated = arrayListOf<PersistentByteCodeLocation>()
        val newLocations = arrayListOf<JcByteCodeLocation>()
        val updated = hashMapOf<JcByteCodeLocation, PersistentByteCodeLocation>()
        notRuntimeLocations.forEach { location ->
            val jcLocation = location.jcLocation
            when {
                jcLocation == null -> {
                    if (!location.hasReferences(snapshots)) {
                        deprecated.add(location)
                    }
                }

                jcLocation.isChanged() -> {
                    val refreshed = jcLocation.createRefreshed()
                    if (refreshed != null) {
                        newLocations.add(refreshed)
                    }
                    if (!location.hasReferences(snapshots)) {
                        deprecated.add(location)
                    } else {
                        updated[jcLocation] = location
                    }
                }
            }
        }
        val new = persistence.write { context ->
            context.deprecate(deprecated)
            newLocations.map { location ->
                val refreshed = context.save(location)
                val toUpdate = updated[location]
                if (toUpdate != null) {
                    context.execute(
                        sqlAction = { jooq ->
                            jooq.update(BYTECODELOCATIONS)
                                .set(BYTECODELOCATIONS.UPDATED_ID, refreshed.id)
                                .set(BYTECODELOCATIONS.STATE, LocationState.OUTDATED.ordinal)
                                .where(BYTECODELOCATIONS.ID.eq(toUpdate.id)).execute()
                        },
                        noSqlAction = { txn ->
                            txn.getEntityOrNull(BytecodeLocationEntity.BYTECODE_LOCATION_ENTITY_TYPE, toUpdate.id)
                                ?.let {
                                    it.addLink(
                                        BytecodeLocationEntity.UPDATED_LINK,
                                        txn.getEntityOrNull(
                                            BytecodeLocationEntity.BYTECODE_LOCATION_ENTITY_TYPE,
                                            refreshed.id
                                        )!!
                                    )
                                    it[BytecodeLocationEntity.STATE] = LocationState.OUTDATED.ordinal
                                }
                        }
                    )
                }
                refreshed
            }
        }
        return RefreshResult(new = new)
    }

    override fun newSnapshot(classpathSetLocations: List<RegisteredLocation>): LocationsRegistrySnapshot {
        return LocationsRegistrySnapshot(this, classpathSetLocations).also {
            snapshots.add(it)
        }
    }

    override fun cleanup(): CleanupResult {
        return persistence.write { context ->
            val deprecated = context.execute(
                sqlAction = { jooq ->
                    jooq.selectFrom(BYTECODELOCATIONS)
                        .where(BYTECODELOCATIONS.UPDATED_ID.isNotNull).fetch()
                        .map { PersistentByteCodeLocationData.fromSqlRecord(it) }
                },
                noSqlAction = { txn ->
                    txn.all(BytecodeLocationEntity.BYTECODE_LOCATION_ENTITY_TYPE)
                        .filter { it.getLinks(BytecodeLocationEntity.UPDATED_LINK).isNotEmpty }
                        .map { PersistentByteCodeLocationData.fromErsEntity(it) }.toList()
                }
            )
                .filterNot { data -> snapshots.any { it.ids.contains(data.id) } }
                .map { PersistentByteCodeLocation(jcdb, it) }
            context.deprecate(deprecated)
            CleanupResult(deprecated)
        }
    }

    override fun close(snapshot: LocationsRegistrySnapshot) {
        snapshots.remove(snapshot)
        cleanup()
    }

    override fun close() {
        jcdb.featuresRegistry.broadcast(JcInternalSignal.Closed)
        runtimeLocations = emptyList()
    }

    private fun JcByteCodeLocation.findOrNew(context: JCDBContext): PersistentByteCodeLocationData {
        val existed = findOrNull(context)
        if (existed != null) {
            return existed
        }
        return context.execute(
            sqlAction = { jooq ->
                val record = BytecodelocationsRecord().also {
                    it.path = path
                    it.uniqueid = fileSystemId
                    it.runtime = type == LocationType.RUNTIME
                }
                jooq.insertInto(BYTECODELOCATIONS).set(record)
                PersistentByteCodeLocationData.fromSqlRecord(record)
            },
            noSqlAction = { txn ->
                val entity = txn.newEntity(BytecodeLocationEntity.BYTECODE_LOCATION_ENTITY_TYPE)
                entity[BytecodeLocationEntity.PATH] = path
                entity[BytecodeLocationEntity.FILE_SYSTEM_ID] = fileSystemId
                entity[BytecodeLocationEntity.IS_RUNTIME] = type == LocationType.RUNTIME
                PersistentByteCodeLocationData.fromErsEntity(entity)
            }
        )
    }

    private fun JcByteCodeLocation.findOrNull(context: JCDBContext): PersistentByteCodeLocationData? {
        return context.execute(
            sqlAction = { jooq ->
                jooq.selectFrom(BYTECODELOCATIONS)
                    .where(BYTECODELOCATIONS.PATH.eq(path).and(BYTECODELOCATIONS.UNIQUEID.eq(fileSystemId)))
                    .fetchAny()
                    ?.let { PersistentByteCodeLocationData.fromSqlRecord(it) }
            },
            noSqlAction = { txn ->
                txn.find(
                    type = BytecodeLocationEntity.BYTECODE_LOCATION_ENTITY_TYPE,
                    propertyName = BytecodeLocationEntity.PATH,
                    value = path
                )
                    .firstOrNull { it.get<String>(BytecodeLocationEntity.FILE_SYSTEM_ID) == fileSystemId }
                    ?.let {
                        PersistentByteCodeLocationData.fromErsEntity(it)
                    }
            }
        )
    }

}

object BytecodeLocationEntity {
    const val BYTECODE_LOCATION_ENTITY_TYPE = "ByteCodeLocation"
    const val STATE = "state"
    const val IS_RUNTIME = "isRuntime"
    const val PATH = "path"
    const val FILE_SYSTEM_ID = "fileSystemId"
    const val UPDATED_LINK = "updatedLink"
}
