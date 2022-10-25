package org.utbot.jcdb.impl.vfs

import org.utbot.jcdb.api.RegisteredLocation
import org.utbot.jcdb.impl.LocationsRegistrySnapshot

/**
 * ClassTree view limited by number of `locations`
 */
class ClasspathVfs(
    private val globalClassVFS: GlobalClassesVfs,
    locations: List<RegisteredLocation>
) {

    constructor(globalClassVFS: GlobalClassesVfs, locationsRegistrySnapshot: LocationsRegistrySnapshot) : this(
        globalClassVFS,
        locationsRegistrySnapshot.locations
    )

    private val locationIds: Set<Long> = locations.map { it.id }.toHashSet()

    fun firstClassOrNull(fullName: String): ClassVfsItem? {
        return globalClassVFS.firstClassNodeOrNull(fullName) {
            locationIds.contains(it)
        }
    }
}