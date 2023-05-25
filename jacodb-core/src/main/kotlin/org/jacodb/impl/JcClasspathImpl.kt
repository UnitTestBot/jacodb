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

package org.jacodb.impl

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.withContext
import org.jacodb.api.ClassSource
import org.jacodb.api.JcArrayType
import org.jacodb.api.JcByteCodeLocation
import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcClasspathExtFeature
import org.jacodb.api.JcClasspathFeature
import org.jacodb.api.JcClasspathTask
import org.jacodb.api.JcRefType
import org.jacodb.api.JcType
import org.jacodb.api.PredefinedPrimitives
import org.jacodb.api.RegisteredLocation
import org.jacodb.api.ext.toType
import org.jacodb.api.throwClassNotFound
import org.jacodb.impl.bytecode.JcClassOrInterfaceImpl
import org.jacodb.impl.features.JcFeaturesChain
import org.jacodb.impl.fs.ClassSourceImpl
import org.jacodb.impl.types.JcArrayTypeImpl
import org.jacodb.impl.types.JcClassTypeImpl
import org.jacodb.impl.types.substition.JcSubstitutor
import org.jacodb.impl.vfs.ClasspathVfs
import org.jacodb.impl.vfs.GlobalClassesVfs
import java.util.*

class JcClasspathImpl(
    private val locationsRegistrySnapshot: LocationsRegistrySnapshot,
    override val db: JcDatabaseImpl,
    override val features: List<JcClasspathFeature>,
    globalClassVFS: GlobalClassesVfs
) : JcClasspath, JcClasspathExtFeature {

    override val locations: List<JcByteCodeLocation> = locationsRegistrySnapshot.locations.mapNotNull { it.jcLocation }
    override val registeredLocations: List<RegisteredLocation> = locationsRegistrySnapshot.locations

    private val classpathVfs = ClasspathVfs(globalClassVFS, locationsRegistrySnapshot)
    private val featuresChain = JcFeaturesChain(features + this)

    override suspend fun refreshed(closeOld: Boolean): JcClasspath {
        return db.new(this).also {
            if (closeOld) {
                close()
            }
        }
    }

    override fun tryFindClass(classpath: JcClasspath, name: String): Optional<JcClassOrInterface> {
        val source = classpathVfs.firstClassOrNull(name)
        val jcClass = source?.let { toJcClass(it.source) }
            ?: db.persistence.findClassSourceByName(this, locationsRegistrySnapshot.locations, name)?.let {
                toJcClass(it)
            }
        return Optional.ofNullable(jcClass)
    }

    override fun tryFindType(classpath: JcClasspath, name: String): Optional<JcType>? {
        if (name.endsWith("[]")) {
            val targetName = name.removeSuffix("[]")
            return findTypeOrNull(targetName)?.let {
                Optional.of(JcArrayTypeImpl(it, true))
            }
        }
        val predefined = PredefinedPrimitives.of(name, this)
        if (predefined != null) {
            return Optional.of(predefined)
        }
        val clazz = findClassOrNull(name) ?: return Optional.empty()
        return Optional.of(typeOf(clazz))
    }

    override fun findClassOrNull(name: String): JcClassOrInterface? {
        return featuresChain.newRequest(name).call<JcClasspathExtFeature, Optional<JcClassOrInterface>> {
            it.tryFindClass(this, name)
        }?.orElse(null)
    }

    override fun typeOf(jcClass: JcClassOrInterface): JcRefType {
        return JcClassTypeImpl(
            this,
            jcClass.name,
            jcClass.outerClass?.toType() as? JcClassTypeImpl,
            JcSubstitutor.empty,
            nullable = null
        )
    }

    override fun arrayTypeOf(elementType: JcType): JcArrayType {
        return JcArrayTypeImpl(elementType, null)
    }

    override fun toJcClass(source: ClassSource): JcClassOrInterface {
        return JcClassOrInterfaceImpl(this, source, featuresChain)
    }

    override fun findTypeOrNull(name: String): JcType? {
        return featuresChain.newRequest(name).call<JcClasspathExtFeature, Optional<JcType>> {
            it.tryFindType(this, name)
        }?.orElse(null)
    }

    override suspend fun <T : JcClasspathTask> execute(task: T): T {
        val locations = registeredLocations.filter { task.shouldProcess(it) }
        task.before(this)
        withContext(Dispatchers.IO) {
            val parentScope = this
            locations.map {
                async {
                    val sources = db.persistence.findClassSources(it)
                        .takeIf { it.isNotEmpty() } ?: it.jcLocation?.classes?.map { entry ->
                        ClassSourceImpl(location = it, className = entry.key, byteCode = entry.value)
                    } ?: emptyList()

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