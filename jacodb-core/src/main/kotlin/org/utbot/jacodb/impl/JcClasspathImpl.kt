/*
 *  Copyright 2022 UnitTestBot contributors (utbot.org)
 * <p>
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * <p>
 *  http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.utbot.jacodb.impl

import com.google.common.cache.CacheBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.withContext
import org.utbot.jacodb.api.ClassSource
import org.utbot.jacodb.api.JcAnnotation
import org.utbot.jacodb.api.JcArrayType
import org.utbot.jacodb.api.JcByteCodeLocation
import org.utbot.jacodb.api.JcClassOrInterface
import org.utbot.jacodb.api.JcClasspath
import org.utbot.jacodb.api.JcClasspathTask
import org.utbot.jacodb.api.JcRefType
import org.utbot.jacodb.api.JcType
import org.utbot.jacodb.api.PredefinedPrimitives
import org.utbot.jacodb.api.RegisteredLocation
import org.utbot.jacodb.api.ext.toType
import org.utbot.jacodb.api.throwClassNotFound
import org.utbot.jacodb.impl.bytecode.JcClassOrInterfaceImpl
import org.utbot.jacodb.impl.types.JcArrayTypeImpl
import org.utbot.jacodb.impl.types.JcClassTypeImpl
import org.utbot.jacodb.impl.types.substition.JcSubstitutor
import org.utbot.jacodb.impl.vfs.ClasspathVfs
import org.utbot.jacodb.impl.vfs.GlobalClassesVfs
import java.time.Duration

class JcClasspathImpl(
    private val locationsRegistrySnapshot: LocationsRegistrySnapshot,
    override val db: JcDatabaseImpl,
    globalClassVFS: GlobalClassesVfs
) : JcClasspath {

    private class ClassHolder(val jcClass: JcClassOrInterface?)
    private class TypeHolder(val type: JcType?)

    private val classCache = CacheBuilder.newBuilder()
        .expireAfterAccess(Duration.ofSeconds(10))
        .maximumSize(1_000)
        .build<String, ClassHolder>()

    private val typeCache = CacheBuilder.newBuilder()
        .expireAfterAccess(Duration.ofSeconds(10))
        .maximumSize(1_000)
        .build<String, TypeHolder>()

    override val locations: List<JcByteCodeLocation> = locationsRegistrySnapshot.locations.mapNotNull { it.jcLocation }
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
            val source = classpathVfs.firstClassOrNull(name)
            val jcClass = source?.let { toJcClass(it.source, false) }
                ?: db.persistence.findClassSourceByName(this, locationsRegistrySnapshot.locations, name)?.let {
                    toJcClass(it, false)
                }
            ClassHolder(jcClass)
        }.jcClass
    }

    override fun typeOf(jcClass: JcClassOrInterface, nullability: Boolean?, annotations: List<JcAnnotation>): JcRefType {
        return JcClassTypeImpl(
            jcClass,
            jcClass.outerClass?.toType() as? JcClassTypeImpl,
            JcSubstitutor.empty,
            nullability,
            annotations
        )
    }

    override fun arrayTypeOf(elementType: JcType, nullability: Boolean?, annotations: List<JcAnnotation>): JcArrayType {
        return JcArrayTypeImpl(elementType, nullability, annotations)
    }

    override fun toJcClass(source: ClassSource, withCaching: Boolean): JcClassOrInterface {
        if (withCaching) {
            return classCache.get(source.className) {
                ClassHolder(JcClassOrInterfaceImpl(this, source))
            }.jcClass!!
        }
        return JcClassOrInterfaceImpl(this, source)
    }

    override fun findTypeOrNull(name: String): JcType? {
        return typeCache.get(name) {
            TypeHolder(doFindTypeOrNull(name))
        }.type
    }

    private fun doFindTypeOrNull(name: String): JcType? {
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

    override suspend fun execute(task: JcClasspathTask): JcClasspathTask {
        val locations = registeredLocations.filter { task.shouldProcess(it) }
        task.before(this)
        withContext(Dispatchers.IO) {
            val parentScope = this
            locations.map {
                async {
                    val sources = db.persistence.findClassSources(it)
                    sources.forEach {
                        if (parentScope.isActive && task.shouldProcess(it)) {
                            task.process(it, this@JcClasspathImpl)
                        }
                    }
                }
            }.joinAll()
        }
        task.after(this)
        return task
    }

    override fun close() {
        locationsRegistrySnapshot.close()
    }

}