package org.utbot.jcdb.impl.vfs

import org.utbot.jcdb.api.JcByteCodeLocation
import org.utbot.jcdb.impl.LocationsRegistrySnapshot

/**
 * ClassTree view limited by number of `locations`
 */
class ClasspathClassTree(
    private val globalClassVFS: GlobalClassesVfs,
    locations: List<JcByteCodeLocation>
) {

    constructor(globalClassVFS: GlobalClassesVfs, locationsRegistrySnapshot: LocationsRegistrySnapshot) : this(
        globalClassVFS,
        locationsRegistrySnapshot.locations
    )

    private val locationIds: Set<String> = locations.map { it.path }.toHashSet()

    fun firstClassOrNull(fullName: String): ClassVfsItem? {
        return globalClassVFS.firstClassNodeOrNull(fullName) {
            locationIds.contains(it)
        }
    }
}