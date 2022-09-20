package org.utbot.jcdb.impl

import kotlinx.collections.immutable.toImmutableList
import org.utbot.jcdb.api.JcByteCodeLocation
import org.utbot.jcdb.api.RegisteredLocation
import java.util.concurrent.atomic.AtomicLong

/** registry of locations for JCDB */
class InMemoryLocationsRegistry(private val featuresRegistry: FeaturesRegistry) : LocationsRegistry {

    private val idGen = AtomicLong()

    // all loaded locations
    private val registeredLocations = HashSet<InMemoryRegisteredLocation>()


    // loaded but outdated locations. that means that they are used in some snapshots but outdated
    internal val usedButOutdated = HashSet<RegisteredLocation>()

    // all snapshot associated with classpaths
    internal val snapshots = HashSet<LocationsRegistrySnapshot>()

    override val locations = synchronized(this) {
        registeredLocations.map { it.jcLocation }.toSet()
    }

    override fun addLocation(location: JcByteCodeLocation) = synchronized(this) {
        InMemoryRegisteredLocation(idGen.incrementAndGet(), location).also {
            registeredLocations.add(it)
        }
    }

    override fun addLocations(location: List<JcByteCodeLocation>) = synchronized(this) {
        location.map { addLocation(it) }
    }

    private suspend fun refresh(location: RegisteredLocation, onRefresh: suspend (RegisteredLocation) -> Unit) {
        val jcLocation = location.jcLocation
        if (jcLocation.isChanged()) {
            val refreshedLocation = synchronized(this) {
                val refreshedLocation = jcLocation.createRefreshed()
                // let's check snapshots
                val hasReferences = location.hasReferences(snapshots)
                if (!hasReferences) {
                    registeredLocations.remove(location)
                    featuresRegistry.onLocationRemove(location)
                } else {
                    usedButOutdated.add(location)
                }
                refreshedLocation
            }
            onRefresh(addLocation(refreshedLocation))
        }
    }

    override suspend fun refresh(onRefresh: suspend (RegisteredLocation) -> Unit) {
        val currentState = synchronized(this) {
            registeredLocations.toImmutableList()
        }
        currentState.forEach { refresh(it, onRefresh) }
    }

    override fun snapshot(classpathSetLocations: List<RegisteredLocation>): LocationsRegistrySnapshot =
        synchronized(this) {
            LocationsRegistrySnapshot(this, classpathSetLocations).also {
                snapshots.add(it)
            }
        }

    override fun cleanup(): Set<RegisteredLocation> {
        synchronized(this) {
            val forRemoval = hashSetOf<RegisteredLocation>()
            usedButOutdated.forEach {
                if (!it.hasReferences(snapshots)) {
                    forRemoval.add(it)
                }
            }
            usedButOutdated.removeAll(forRemoval)
            return forRemoval
        }
    }

    override fun onClose(snapshot: LocationsRegistrySnapshot) = synchronized(this) {
        snapshots.remove(snapshot)
        cleanup()
    }

    override fun close() {
        synchronized(this) {
            registeredLocations.clear()
            usedButOutdated.clear()
            snapshots.clear()
        }
    }

}

class InMemoryRegisteredLocation(
    override val id: Long,
    override val jcLocation: JcByteCodeLocation
) : RegisteredLocation
