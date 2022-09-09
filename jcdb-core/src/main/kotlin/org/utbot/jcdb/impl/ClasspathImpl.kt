package org.utbot.jcdb.impl

import org.utbot.jcdb.api.ByteCodeLocation
import org.utbot.jcdb.api.ClassId
import org.utbot.jcdb.api.Classpath
import org.utbot.jcdb.api.IndexRequest
import org.utbot.jcdb.api.PredefinedPrimitives
import org.utbot.jcdb.impl.index.hierarchyExt
import org.utbot.jcdb.impl.tree.ClassTree
import org.utbot.jcdb.impl.tree.ClasspathClassTree
import org.utbot.jcdb.impl.types.ArrayClassIdImpl
import java.io.Serializable

class ClasspathImpl(
    private val locationsRegistrySnapshot: LocationsRegistrySnapshot,
    private val featuresRegistry: FeaturesRegistry,
    override val db: JCDBImpl,
    classTree: ClassTree
) : Classpath {

    override val locations: List<ByteCodeLocation> = locationsRegistrySnapshot.locations

    private val classpathClassTree = ClasspathClassTree(classTree, locationsRegistrySnapshot)
    private val classIdService = ClassIdService(this, classpathClassTree)

    override suspend fun refreshed(closeOld: Boolean): Classpath {
        return db.classpathSet(locationsRegistrySnapshot.locations).also {
            if (closeOld) {
                close()
            }
        }
    }

    override suspend fun findClassOrNull(name: String): ClassId? {
        if (name.endsWith("[]")) {
            val targetName = name.removeSuffix("[]")
            return findClassOrNull(targetName)?.let {
                ArrayClassIdImpl(it)
            }
        }
        val predefined = PredefinedPrimitives.of(name, this)
        if (predefined != null) {
            return predefined
        }
        return classIdService.toClassId(classpathClassTree.firstClassOrNull(name))
    }

    override suspend fun findSubClasses(name: String, allHierarchy: Boolean): List<ClassId> {
        return hierarchyExt.findSubClasses(name, allHierarchy)
    }

    override suspend fun findSubClasses(classId: ClassId, allHierarchy: Boolean): List<ClassId> {
        return hierarchyExt.findSubClasses(classId, allHierarchy)
    }

    override suspend fun <T: Serializable, REQ: IndexRequest> query(key: String, term: REQ): List<T> {
        db.awaitBackgroundJobs()
        return featuresRegistry.findIndex<T, REQ>(key)?.query(term).orEmpty().toList()
    }

    override suspend fun <T: Serializable, REQ: IndexRequest> query(key: String, location: ByteCodeLocation, term: REQ): List<T> {
        db.awaitBackgroundJobs()
        return featuresRegistry.findIndex<T, REQ>(key)?.query(term).orEmpty().toList()
    }

    override fun close() {
        locationsRegistrySnapshot.close()
    }

}