package org.utbot.jcdb.impl.storage

import org.utbot.jcdb.api.JCDBPersistence
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

class PersistentLocationRegistry(
    private val persistence: JCDBPersistence,
    private val featuresRegistry: FeaturesRegistry
) : LocationsRegistry {

    private val create = (persistence as SQLitePersistenceImpl).create
    private val idGen = AtomicLong()

    // all snapshot associated with classpaths
    internal val snapshots = ConcurrentHashMap.newKeySet<LocationsRegistrySnapshot>()

    override val actualLocations: List<PersistentByteCodeLocation>
        get() = persistence.read {
            create.selectFrom(BYTECODELOCATIONS).fetch {
                PersistentByteCodeLocation(it)
            }
        }

    override lateinit var runtimeLocations: List<RegisteredLocation>

    private fun add(location: JcByteCodeLocation) = PersistentByteCodeLocation(location.findOrNew(), location)

    override fun setup(runtimeLocations: List<JcByteCodeLocation>): RegistrationResult {
        return registerIfNeeded(runtimeLocations).also {
            this.runtimeLocations = it.registered
        }
    }

    override fun afterProcessing(locations: List<RegisteredLocation>) {
        val ids = locations.map { it.id }
        persistence.write {
            create.update(BYTECODELOCATIONS)
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
            val existed = create.selectFrom(BYTECODELOCATIONS).where(
                BYTECODELOCATIONS.HASH.`in`(hashes).and(BYTECODELOCATIONS.STATE.ne(LocationState.INITIAL.ordinal))
            ).fetch().associateBy { it.hash }

            locations.forEach {
                val found = existed[it.hash]
                if (found == null) {
                    toAdd += it
                } else {
                    result += PersistentByteCodeLocation(found, it)
                }
            }
            val records = toAdd.map { add ->
                add to BytecodelocationsRecord().also {
                    it.changed(true)
                    it.id = idGen.incrementAndGet()
                    it.path = add.path
                    it.hash = add.hash
                    it.runtime = add.type == LocationType.RUNTIME
                    it.state = LocationState.INITIAL.ordinal
                }
            }
            create.connection {
                it.insertElements(BYTECODELOCATIONS, toAdd) {
                    setLong(1, idGen.incrementAndGet())
                    setString(2, it.path)
                    setString(3, it.hash)
                    setBoolean(4, it.type == LocationType.RUNTIME)
                    setInt(5, LocationState.INITIAL.ordinal)
                }
            }
            val added = records.map { PersistentByteCodeLocation(it.second, it.first) }
            RegistrationResult(result + added, added)
        }
    }

    private fun deprecate(locations: List<RegisteredLocation>) {
        locations.forEach {
            featuresRegistry.broadcast(JcInternalSignal.LocationRemoved(it))
        }
        create.deleteFrom(BYTECODELOCATIONS).where(BYTECODELOCATIONS.ID.`in`(locations.map { it.id })).execute()
    }

    override fun refresh(): RefreshResult {
        val deprecated = arrayListOf<PersistentByteCodeLocation>()
        val newLocations = arrayListOf<JcByteCodeLocation>()
        val updated = hashMapOf<JcByteCodeLocation, PersistentByteCodeLocation>()
        actualLocations.forEach { location ->
            val jcLocation = location.jcLocation
            if (jcLocation.isChanged()) {
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
        val new = persistence.write {
            deprecate(deprecated)
            newLocations.map { location ->
                val refreshed = add(location)
                val toUpdate = updated[location]
                if (toUpdate != null) {
                    create.update(BYTECODELOCATIONS).set(BYTECODELOCATIONS.UPDATED_ID, refreshed.entity.id)
                        .where(BYTECODELOCATIONS.ID.eq(toUpdate.entity.updatedId)).execute()
                    toUpdate.entity.updatedId = refreshed.entity.id
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
            val deprecated = create.selectFrom(BYTECODELOCATIONS)
                .where(BYTECODELOCATIONS.UPDATED_ID.isNotNull).fetch()
                .toList()
                .filterNot { entity -> snapshots.any { it.ids.contains(entity.id) } }
                .map { PersistentByteCodeLocation(it) }
            deprecate(deprecated)
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

    fun JcByteCodeLocation.findOrNew(): BytecodelocationsRecord {
        val existed = findOrNull()
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

    fun JcByteCodeLocation.findOrNull(): BytecodelocationsRecord? {
        return create.selectFrom(BYTECODELOCATIONS)
            .where(BYTECODELOCATIONS.PATH.eq(path).and(BYTECODELOCATIONS.HASH.eq(hash))).fetchAny()
    }

}