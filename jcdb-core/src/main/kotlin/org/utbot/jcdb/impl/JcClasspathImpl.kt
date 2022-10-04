package org.utbot.jcdb.impl

import org.utbot.jcdb.api.JcArrayType
import org.utbot.jcdb.api.JcByteCodeLocation
import org.utbot.jcdb.api.JcClassOrInterface
import org.utbot.jcdb.api.JcClasspath
import org.utbot.jcdb.api.JcRefType
import org.utbot.jcdb.api.JcType
import org.utbot.jcdb.api.PredefinedPrimitives
import org.utbot.jcdb.api.Pure
import org.utbot.jcdb.api.RegisteredLocation
import org.utbot.jcdb.api.anyType
import org.utbot.jcdb.api.throwClassNotFound
import org.utbot.jcdb.impl.bytecode.JcClassOrInterfaceImpl
import org.utbot.jcdb.impl.index.hierarchyExt
import org.utbot.jcdb.impl.signature.TypeResolutionImpl
import org.utbot.jcdb.impl.signature.TypeSignature
import org.utbot.jcdb.impl.types.JcArrayClassTypesImpl
import org.utbot.jcdb.impl.types.JcClassTypeImpl
import org.utbot.jcdb.impl.types.JcParameterizedTypeImpl
import org.utbot.jcdb.impl.types.typeDeclarations
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
    override val registeredLocations: List<RegisteredLocation> = locationsRegistrySnapshot.locations

    private val classpathClassTree = ClasspathClassTree(globalClassVFS, locationsRegistrySnapshot)

    override suspend fun refreshed(closeOld: Boolean): JcClasspath {
        return db.new(this).also {
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
        return db.persistence.findClassByName(this, locationsRegistrySnapshot.locations, name)?.let {
            JcClassOrInterfaceImpl(this, it)
        }
    }

    override suspend fun typeOf(jcClass: JcClassOrInterface): JcRefType {
        val signature = TypeSignature.of(jcClass.signature)
        when (signature) {
            is Pure -> return JcClassTypeImpl(jcClass, signature, true)
            is TypeResolutionImpl -> return JcParameterizedTypeImpl(jcClass,
                originParametrization = typeDeclarations(signature.typeVariable),
                emptyList(),
                true
            )
        }
        return JcClassTypeImpl(jcClass, signature, true)
    }

    override suspend fun arrayTypeOf(elementType: JcType): JcArrayType {
        return JcArrayClassTypesImpl(elementType, true, anyType())
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
        return featuresRegistry.query<REQ, RES>(key, req).orEmpty()
    }

    override fun close() {
        locationsRegistrySnapshot.close()
    }

}