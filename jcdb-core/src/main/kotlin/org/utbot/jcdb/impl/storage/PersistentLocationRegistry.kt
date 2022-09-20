package org.utbot.jcdb.impl.storage

import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.transactions.transaction
import org.utbot.jcdb.api.JcByteCodeLocation
import org.utbot.jcdb.api.LocationType
import org.utbot.jcdb.api.RegisteredLocation
import org.utbot.jcdb.impl.FeaturesRegistry
import org.utbot.jcdb.impl.LocationsRegistry
import org.utbot.jcdb.impl.LocationsRegistrySnapshot
import org.utbot.jcdb.impl.storage.BytecodeLocationEntity.Companion.findOrNew
import org.utbot.jcdb.impl.vfs.PersistentByteCodeLocation
import org.utbot.jcdb.impl.vfs.toJcLocation

class PersistentLocationRegistry(
    private val persistence: SQLitePersistenceImpl,
    private val featuresRegistry: FeaturesRegistry
) : LocationsRegistry {

    override val locations: Set<JcByteCodeLocation>
        get() = transaction(persistence.db) {
            BytecodeLocationEntity.all().toList().map { it.toJcLocation() }.toSet()
        }

    private val registeredLocations: Set<PersistentByteCodeLocation>
        get() = transaction(persistence.db) {
            BytecodeLocationEntity.all().toList().map { PersistentByteCodeLocation(it) }.toSet()
        }

    // all snapshot associated with classpaths
    private val snapshots = HashSet<LocationsRegistrySnapshot>()

    private fun add(location: JcByteCodeLocation): PersistentByteCodeLocation {
        return persistence.write {
            PersistentByteCodeLocation(location.findOrNew(), location)
        }
    }

    override fun addLocation(location: JcByteCodeLocation) = add(location)

    override fun addLocations(location: List<JcByteCodeLocation>): List<RegisteredLocation> {
        return persistence.write {
            val result = arrayListOf<RegisteredLocation>()
            val toAdd = arrayListOf<JcByteCodeLocation>()
            locations.forEach {
                val found = BytecodeLocationEntity.find {
                    (BytecodeLocations.path eq it.path) and (BytecodeLocations.hash eq it.hash)
                }.firstOrNull()
                if (found == null) {
                    toAdd += it
                } else {
                    result += PersistentByteCodeLocation(found, it)
                }
            }
            BytecodeLocations.batchInsert(toAdd, shouldReturnGeneratedValues = false) {
                this[BytecodeLocations.hash] = it.hash
                this[BytecodeLocations.path] = it.path
                this[BytecodeLocations.runtime] = it.type == LocationType.RUNTIME
            }
            result
        }
    }

    private suspend fun refresh(location: RegisteredLocation, onRefresh: suspend (RegisteredLocation) -> Unit) {
        val jcLocation = location.jcLocation
        if (jcLocation.isChanged()) {
            val refreshedLocation = persistence.write {
                val refreshedLocation = jcLocation.createRefreshed()
                val refreshed = add(refreshedLocation)
                // let's check snapshots
                val hasReferences = location.hasReferences(snapshots)
                val entity = BytecodeLocationEntity.findById(location.id)
                    ?: throw IllegalStateException("location with ${location.id} not found")
                if (!hasReferences) {
                    featuresRegistry.onLocationRemove(location)
                    entity.delete()
                } else {
                    entity.updated = refreshed.entity
                }
                refreshedLocation
            }
            onRefresh(addLocation(refreshedLocation))
        }
    }

    override suspend fun refresh(onRefresh: suspend (RegisteredLocation) -> Unit) {
        registeredLocations.forEach {
            refresh(it, onRefresh)
        }
    }

    override fun snapshot(classpathSetLocations: List<RegisteredLocation>): LocationsRegistrySnapshot {
        return synchronized(this) {
            LocationsRegistrySnapshot(this, classpathSetLocations).also {
                snapshots.add(it)
            }
        }
    }

    override fun cleanup(): Set<RegisteredLocation> {
        return persistence.write {
            BytecodeLocationEntity
                .find(BytecodeLocations.updated neq null)
                .toList()
                .filter { entity -> snapshots.any { it.ids.contains(entity.id.value) } }
                .map { PersistentByteCodeLocation(it) }
                .toSet()
        }
    }

    override fun onClose(snapshot: LocationsRegistrySnapshot): Set<RegisteredLocation> {
        snapshots.remove(snapshot)
        return cleanup()
    }

    override fun close() {
        // do nothing
    }
}