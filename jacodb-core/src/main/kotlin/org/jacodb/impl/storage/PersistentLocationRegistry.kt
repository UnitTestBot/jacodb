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

import org.jacodb.api.JcByteCodeLocation
import org.jacodb.api.JcDatabase
import org.jacodb.api.LocationType
import org.jacodb.api.RegisteredLocation
import org.jacodb.impl.CleanupResult
import org.jacodb.impl.FeaturesRegistry
import org.jacodb.impl.JcInternalSignal
import org.jacodb.impl.LocationsRegistry
import org.jacodb.impl.LocationsRegistrySnapshot
import org.jacodb.impl.RefreshResult
import org.jacodb.impl.RegistrationResult
import org.jacodb.impl.storage.jooq.tables.records.BytecodelocationsRecord
import org.jacodb.impl.storage.jooq.tables.references.BYTECODELOCATIONS
import org.jacodb.impl.vfs.PersistentByteCodeLocation
import org.jooq.DSLContext
import java.sql.Types
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class PersistentLocationRegistry(private val jcdb: JcDatabase, private val featuresRegistry: FeaturesRegistry) :
    LocationsRegistry {

    private val persistence = jcdb.persistence

    private val idGen: AtomicLong = AtomicLong(persistence.read { BYTECODELOCATIONS.ID.maxId(it) } ?: 0)

    // all snapshot associated with classpaths
    internal val snapshots = ConcurrentHashMap.newKeySet<LocationsRegistrySnapshot>()

    init {
        persistence.write { jooq ->
            jooq.update(BYTECODELOCATIONS)
                .set(BYTECODELOCATIONS.STATE, LocationState.OUTDATED.ordinal)
                .where(BYTECODELOCATIONS.STATE.notEqual(LocationState.PROCESSED.ordinal))
                .execute()
        }
    }

    override val actualLocations: List<PersistentByteCodeLocation>
        get() = persistence.read {
            it.selectFrom(BYTECODELOCATIONS).fetch {
                PersistentByteCodeLocation(jcdb, it)
            }
        }

    private val notRuntimeLocations: List<PersistentByteCodeLocation>
        get() = persistence.read {
            it.selectFrom(BYTECODELOCATIONS).where(BYTECODELOCATIONS.RUNTIME.ne(true)).fetch {
                PersistentByteCodeLocation(jcdb, it)
            }
        }

    override lateinit var runtimeLocations: List<RegisteredLocation>

    private fun DSLContext.add(location: JcByteCodeLocation) =
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
        persistence.write {
            it.update(BYTECODELOCATIONS)
                .set(BYTECODELOCATIONS.STATE, LocationState.PROCESSED.ordinal).where(BYTECODELOCATIONS.ID.`in`(ids))
                .execute()
        }
        featuresRegistry.broadcast(JcInternalSignal.AfterIndexing)
    }

    override fun registerIfNeeded(locations: List<JcByteCodeLocation>): RegistrationResult {
        val uniqueLocations = locations.toSet()
        return persistence.write {
            val result = arrayListOf<RegisteredLocation>()
            val toAdd = arrayListOf<JcByteCodeLocation>()
            val fsId = uniqueLocations.map { it.fileSystemId }
            val existed = it.selectFrom(BYTECODELOCATIONS)
                .where(BYTECODELOCATIONS.UNIQUEID.`in`(fsId))
                .fetch().associateBy { it.uniqueid }

            uniqueLocations.forEach {
                val found = existed[it.fileSystemId]
                if (found == null) {
                    toAdd += it
                } else {
                    result += PersistentByteCodeLocation(jcdb, found, it)
                }
            }
            val records = toAdd.map { add ->
                idGen.incrementAndGet() to add
            }
            it.connection {
                it.insertElements(BYTECODELOCATIONS, records) {
                    val (id, location) = it
                    setLong(1, id)
                    setString(2, location.path)
                    setString(3, location.fileSystemId)
                    setBoolean(4, location.type == LocationType.RUNTIME)
                    setInt(5, LocationState.INITIAL.ordinal)
                    setNull(6, Types.BIGINT)
                }
            }
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

    private fun DSLContext.deprecate(locations: List<RegisteredLocation>) {
        locations.forEach {
            featuresRegistry.broadcast(JcInternalSignal.LocationRemoved(it))
        }
        deleteFrom(BYTECODELOCATIONS).where(BYTECODELOCATIONS.ID.`in`(locations.map { it.id })).execute()
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
        val new = persistence.write {
            it.deprecate(deprecated)
            newLocations.map { location ->
                val refreshed = it.add(location)
                val toUpdate = updated[location]
                if (toUpdate != null) {
                    it.update(BYTECODELOCATIONS)
                        .set(BYTECODELOCATIONS.UPDATED_ID, refreshed.id)
                        .set(BYTECODELOCATIONS.STATE, LocationState.OUTDATED.ordinal)
                        .where(BYTECODELOCATIONS.ID.eq(toUpdate.id)).execute()
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
        return persistence.write {
            val deprecated = it.selectFrom(BYTECODELOCATIONS)
                .where(BYTECODELOCATIONS.UPDATED_ID.isNotNull).fetch()
                .toList()
                .filterNot { entity -> snapshots.any { it.ids.contains(entity.id) } }
                .map { PersistentByteCodeLocation(jcdb, it) }
            it.deprecate(deprecated)
            CleanupResult(deprecated)
        }
    }

    override fun close(snapshot: LocationsRegistrySnapshot) {
        snapshots.remove(snapshot)
        cleanup()
    }

    override fun close() {
        featuresRegistry.broadcast(JcInternalSignal.Closed)
        runtimeLocations = emptyList()
    }

    private fun JcByteCodeLocation.findOrNew(dslContext: DSLContext): BytecodelocationsRecord {
        val existed = findOrNull(dslContext)
        if (existed != null) {
            return existed
        }
        val record = BytecodelocationsRecord().also {
            it.path = path
            it.uniqueid = fileSystemId
            it.runtime = type == LocationType.RUNTIME
        }
        record.insert()
        return record
    }

    private fun JcByteCodeLocation.findOrNull(dslContext: DSLContext): BytecodelocationsRecord? {
        return dslContext.selectFrom(BYTECODELOCATIONS)
            .where(BYTECODELOCATIONS.PATH.eq(path).and(BYTECODELOCATIONS.UNIQUEID.eq(fileSystemId))).fetchAny()
    }

}
