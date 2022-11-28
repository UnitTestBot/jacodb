package org.utbot.jcdb.impl.storage

import org.jooq.DSLContext
import org.utbot.jcdb.api.JCDB
import org.utbot.jcdb.api.JcByteCodeLocation
import org.utbot.jcdb.api.LocationType
import org.utbot.jcdb.api.RegisteredLocation
import org.utbot.jcdb.impl.CleanupResult
import org.utbot.jcdb.impl.FeaturesRegistry
import org.utbot.jcdb.impl.JcInternalSignal
import org.utbot.jcdb.impl.LocationsRegistry
import org.utbot.jcdb.impl.LocationsRegistrySnapshot
import org.utbot.jcdb.impl.RefreshResult
import org.utbot.jcdb.impl.RegistrationResult
import org.utbot.jcdb.impl.storage.jooq.tables.records.BytecodelocationsRecord
import org.utbot.jcdb.impl.storage.jooq.tables.references.BYTECODELOCATIONS
import org.utbot.jcdb.impl.vfs.PersistentByteCodeLocation
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class PersistentLocationRegistry(private val jcdb: JCDB, private val featuresRegistry: FeaturesRegistry) :
    LocationsRegistry {

    private val persistence = jcdb.persistence

    private val idGen: AtomicLong = AtomicLong(persistence.read { BYTECODELOCATIONS.ID.maxId(it) } ?: 0)

    // all snapshot associated with classpaths
    internal val snapshots = ConcurrentHashMap.newKeySet<LocationsRegistrySnapshot>()

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
        return persistence.write {
            val result = arrayListOf<RegisteredLocation>()
            val toAdd = arrayListOf<JcByteCodeLocation>()
            val hashes = locations.map { it.hash }
            val existed = it.selectFrom(BYTECODELOCATIONS).where(
                BYTECODELOCATIONS.HASH.`in`(hashes).and(BYTECODELOCATIONS.STATE.ne(LocationState.INITIAL.ordinal))
            ).fetch().associateBy { it.hash }

            locations.forEach {
                val found = existed[it.hash]
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
                    setString(3, location.hash)
                    setBoolean(4, location.type == LocationType.RUNTIME)
                    setInt(5, LocationState.INITIAL.ordinal)
                }
            }
            val added = records.map { PersistentByteCodeLocation(jcdb.persistence, jcdb.runtimeVersion, it.first, null, it.second) }
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
        // do nothing
    }

    private fun JcByteCodeLocation.findOrNew(dslContext: DSLContext): BytecodelocationsRecord {
        val existed = findOrNull(dslContext)
        if (existed != null) {
            return existed
        }
        val record = BytecodelocationsRecord().also {
            it.path = path
            it.hash = hash
            it.runtime = type == LocationType.RUNTIME
        }
        record.insert()
        return record
    }

    private fun JcByteCodeLocation.findOrNull(dslContext: DSLContext): BytecodelocationsRecord? {
        return dslContext.selectFrom(BYTECODELOCATIONS)
            .where(BYTECODELOCATIONS.PATH.eq(path).and(BYTECODELOCATIONS.HASH.eq(hash))).fetchAny()
    }

}