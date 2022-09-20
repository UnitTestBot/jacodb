package org.utbot.jcdb.impl.fs

import org.utbot.jcdb.api.ClassLoadingContainer
import org.utbot.jcdb.api.JcByteCodeLocation
import org.utbot.jcdb.api.LocationType
import org.utbot.jcdb.impl.vfs.LibraryClassVfs
import java.io.InputStream

class ClassLoadingContainerImpl(
    override val classes: Map<String, InputStream>,
    val onClose: () -> Unit = {}
) : ClassLoadingContainer {

    override fun close() {
        onClose()
    }
}

/**
 * load sync part into the tree and returns lambda that will do async part
 */
suspend fun JcByteCodeLocation.load(): LibraryClassVfs {
    val libraryTree = LibraryClassVfs(this)
    val container = classes()
    container?.classes?.forEach {
        val source = ClassByteCodeSource(this, it.key, it.value.readBytes())
        libraryTree.addClass(source)
    }
    container?.close()
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