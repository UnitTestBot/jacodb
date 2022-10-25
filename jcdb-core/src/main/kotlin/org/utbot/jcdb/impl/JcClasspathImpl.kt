package org.utbot.jcdb.impl

import com.google.common.cache.CacheBuilder
import org.utbot.jcdb.api.JcArrayType
import org.utbot.jcdb.api.JcByteCodeLocation
import org.utbot.jcdb.api.JcClassOrInterface
import org.utbot.jcdb.api.JcClasspath
import org.utbot.jcdb.api.JcRefType
import org.utbot.jcdb.api.JcType
import org.utbot.jcdb.api.PredefinedPrimitives
import org.utbot.jcdb.api.RegisteredLocation
import org.utbot.jcdb.api.throwClassNotFound
import org.utbot.jcdb.api.toType
import org.utbot.jcdb.impl.bytecode.JcClassOrInterfaceImpl
import org.utbot.jcdb.impl.bytecode.toJcClass
import org.utbot.jcdb.impl.types.JcArrayTypeImpl
import org.utbot.jcdb.impl.types.JcClassTypeImpl
import org.utbot.jcdb.impl.types.substition.JcSubstitutor
import org.utbot.jcdb.impl.vfs.ClasspathVfs
import org.utbot.jcdb.impl.vfs.GlobalClassesVfs
import java.time.Duration

class JcClasspathImpl(
    private val locationsRegistrySnapshot: LocationsRegistrySnapshot,
    override val db: JCDBImpl,
    globalClassVFS: GlobalClassesVfs
) : JcClasspath {

    private val classCache = CacheBuilder.newBuilder()
        .expireAfterAccess(Duration.ofSeconds(10))
        .maximumSize(1_000)
        .build<String, JcClassOrInterface>()

    override val locations: List<JcByteCodeLocation> = locationsRegistrySnapshot.locations.map { it.jcLocation }
    override val registeredLocations: List<RegisteredLocation> = locationsRegistrySnapshot.locations

    private val classpathVfs = ClasspathVfs(globalClassVFS, locationsRegistrySnapshot)

    override suspend fun refreshed(closeOld: Boolean): JcClasspath {
        return db.new(this).also {
            if (closeOld) {
                close()
            }
        }
    }

    override fun findClassOrNull(name: String): JcClassOrInterface? {
        return classCache.get(name) {
            toJcClass(classpathVfs.firstClassOrNull(name))
                ?: db.persistence.findClassByName(this, locationsRegistrySnapshot.locations, name)?.let {
                    JcClassOrInterfaceImpl(this, it)
                }
        }
    }

    override fun typeOf(jcClass: JcClassOrInterface): JcRefType {
        return JcClassTypeImpl(
            jcClass,
            jcClass.outerClass?.toType() as? JcClassTypeImpl,
            JcSubstitutor.empty,
            nullable = true
        )
    }

    override fun arrayTypeOf(elementType: JcType): JcArrayType {
        return JcArrayTypeImpl(elementType, true)
    }

    override fun findTypeOrNull(name: String): JcType? {
        if (name.endsWith("[]")) {
            val targetName = name.removeSuffix("[]")
            return findTypeOrNull(targetName)?.let {
                JcArrayTypeImpl(it, true)
            } ?: targetName.throwClassNotFound()
        }
        val predefined = PredefinedPrimitives.of(name, this)
        if (predefined != null) {
            return predefined
        }
        return typeOf(findClassOrNull(name) ?: return null)
    }

    override fun close() {
        locationsRegistrySnapshot.close()
    }

}