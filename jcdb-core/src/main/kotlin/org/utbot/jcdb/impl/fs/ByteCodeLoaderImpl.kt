package org.utbot.jcdb.impl.fs

import org.utbot.jcdb.api.JcByteCodeLocation
import org.utbot.jcdb.api.LocationType
import org.utbot.jcdb.api.RegisteredLocation
import org.utbot.jcdb.impl.vfs.LibraryClassVfs

/**
 * load sync part into the tree and returns lambda that will do async part
 */
fun RegisteredLocation.load(): LibraryClassVfs {
    val libraryTree = LibraryClassVfs(this)
    jcLocation.classes?.forEach {
        val source = ClassSourceImpl(this, it.key, it.value)
        libraryTree.addClass(source)
    }
    return libraryTree
}

/**
 * limits scope for search base on location. That means that sometimes there is no need to search for subclasses of
 * library class inside java runtime.
 *
 * @param location target location
 */
fun Collection<JcByteCodeLocation>.relevantLocations(location: JcByteCodeLocation?): Collection<JcByteCodeLocation> {
    if (location?.type != LocationType.APP) {
        return this
    }
    return filter { it.type == LocationType.APP }
}