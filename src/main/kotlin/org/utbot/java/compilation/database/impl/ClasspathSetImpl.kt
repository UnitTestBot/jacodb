package org.utbot.java.compilation.database.impl

import org.utbot.java.compilation.database.api.ByteCodeLocation
import org.utbot.java.compilation.database.api.ClassId
import org.utbot.java.compilation.database.api.ClasspathSet
import org.utbot.java.compilation.database.impl.tree.ClassTree
import org.utbot.java.compilation.database.impl.tree.ClasspathClassTree

class ClasspathSetImpl(
    private val locationsRegistrySnapshot: LocationsRegistrySnapshot,
    private val db: CompilationDatabaseImpl,
    classTree: ClassTree
) : ClasspathSet {

    override val locations: List<ByteCodeLocation> = locationsRegistrySnapshot.locations

    private val classpathClassTree = ClasspathClassTree(classTree, locationsRegistrySnapshot)
    private val classIdService = ClassIdService(classpathClassTree)

    override suspend fun findClassOrNull(name: String): ClassId? {
        return classIdService.toClassId(classpathClassTree.firstClassOrNull(name))
    }

    override suspend fun findSubTypesOf(name: String): List<ClassId> {
        db.awaitBackgroundJobs()
        return classpathClassTree.findSubTypesOf(name)
            .map { classIdService.toClassId(it) }
            .filterNotNull()
    }

    override fun close() {
        locationsRegistrySnapshot.close()
    }


}