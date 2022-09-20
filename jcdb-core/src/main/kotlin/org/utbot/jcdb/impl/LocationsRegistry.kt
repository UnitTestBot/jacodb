package org.utbot.jcdb.impl

import org.utbot.jcdb.api.JcByteCodeLocation
import org.utbot.jcdb.api.RegisteredLocation
import java.io.Closeable

interface LocationsRegistry : Closeable {
    // all loaded locations
    val locations: Set<JcByteCodeLocation>
    fun addLocation(location: JcByteCodeLocation): RegisteredLocation
    fun addLocations(location: List<JcByteCodeLocation>): List<RegisteredLocation>

    suspend fun refresh(onRefresh: suspend (RegisteredLocation) -> Unit)

    fun snapshot(classpathSetLocations: List<RegisteredLocation>): LocationsRegistrySnapshot
    fun cleanup(): Set<RegisteredLocation>
    fun onClose(snapshot: LocationsRegistrySnapshot): Set<RegisteredLocation>

    fun RegisteredLocation.hasReferences(snapshots: Set<LocationsRegistrySnapshot>): Boolean {
        return snapshots.isNotEmpty() && snapshots.any { it.ids.contains(id) }
    }

}


open class LocationsRegistrySnapshot(
    private val registry: LocationsRegistry,
    val locations: List<RegisteredLocation>
) : Closeable {

    val ids = locations.map { it.id }.toHashSet()

    override fun close() {
        registry.onClose(this)
    }
}