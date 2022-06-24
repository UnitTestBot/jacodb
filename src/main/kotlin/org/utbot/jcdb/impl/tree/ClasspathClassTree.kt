package org.utbot.jcdb.impl.tree

import org.utbot.jcdb.api.ByteCodeLocation
import org.utbot.jcdb.impl.LocationsRegistrySnapshot

/**
 * ClassTree view limited by number of `locations`
 */
class ClasspathClassTree(
    private val classTree: ClassTree,
    locations: List<ByteCodeLocation>
) {

    constructor(classTree: ClassTree, locationsRegistrySnapshot: LocationsRegistrySnapshot) : this(
        classTree,
        locationsRegistrySnapshot.locations
    )

    private val locationIds: Set<String> = locations.map { it.id }.toHashSet()


    fun firstClassOrNull(fullName: String): ClassNode? {
        return classTree.firstClassNodeOrNull(fullName) {
            locationIds.contains(it)
        }
    }
}