package org.utbot.jcdb.impl

import org.utbot.jcdb.api.JcArrayType
import org.utbot.jcdb.api.JcByteCodeLocation
import org.utbot.jcdb.api.JcClassOrInterface
import org.utbot.jcdb.api.JcClasspath
import org.utbot.jcdb.api.JcRefType
import org.utbot.jcdb.api.JcType
import org.utbot.jcdb.api.PredefinedPrimitives
import org.utbot.jcdb.api.anyType
import org.utbot.jcdb.api.throwClassNotFound
import org.utbot.jcdb.impl.index.hierarchyExt
import org.utbot.jcdb.impl.types.JcArrayClassTypesImpl
import org.utbot.jcdb.impl.types.JcClassTypeImpl
import org.utbot.jcdb.impl.vfs.ClasspathClassTree
import org.utbot.jcdb.impl.vfs.GlobalClassesVfs
import java.io.Serializable

class JcClasspathImpl(
    private val locationsRegistrySnapshot: LocationsRegistrySnapshot,
    private val featuresRegistry: FeaturesRegistry,
    override val db: JCDBImpl,
    globalClassVFS: GlobalClassesVfs
) : JcClasspath {

    override val locations: List<JcByteCodeLocation> = locationsRegistrySnapshot.locations.map { it.jcLocation }

    private val classpathClassTree = ClasspathClassTree(globalClassVFS, locationsRegistrySnapshot)

    override suspend fun refreshed(closeOld: Boolean): JcClasspath {
        return db.classpath(locationsRegistrySnapshot.locations).also {
            if (closeOld) {
                close()
            }
        }
    }

    override suspend fun findClassOrNull(name: String): JcClassOrInterface? {
        val inMemoryClass = toJcClass(classpathClassTree.firstClassOrNull(name))
        if (inMemoryClass != null) {
            return inMemoryClass
        }
        return db.persistence.findByName(this, locationsRegistrySnapshot.locations, name)
    }

    override suspend fun typeOf(jcClass: JcClassOrInterface): JcRefType {
        TODO("Not yet implemented")
    }

    override suspend fun arrayTypeOf(elementType: JcType): JcArrayType {
        TODO("Not yet implemented")
    }

    override suspend fun findTypeOrNull(name: String): JcType? {
        if (name.endsWith("[]")) {
            val targetName = name.removeSuffix("[]")
            return findTypeOrNull(targetName)?.let {
                JcArrayClassTypesImpl(it, true, anyType())
            } ?: targetName.throwClassNotFound()
        }
        val predefined = PredefinedPrimitives.of(name, this)
        if (predefined != null) {
            return predefined
        }
        val jcClass = findClassOrNull(name) ?: return null
        return JcClassTypeImpl(jcClass, nullable = true)
    }

    override suspend fun findSubClasses(name: String, allHierarchy: Boolean): List<JcClassOrInterface> {
        return hierarchyExt.findSubClasses(name, allHierarchy)
    }

    override suspend fun findSubClasses(jcClass: JcClassOrInterface, allHierarchy: Boolean): List<JcClassOrInterface> {
        return hierarchyExt.findSubClasses(jcClass, allHierarchy)
    }

    override suspend fun <RES : Serializable, REQ : Serializable> query(key: String, req: REQ): Sequence<RES> {
        db.awaitBackgroundJobs()
        return featuresRegistry.findIndex<RES, REQ>(key)?.query(req).orEmpty()
    }

    override fun close() {
        locationsRegistrySnapshot.close()
    }

}