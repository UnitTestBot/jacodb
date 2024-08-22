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
import org.jacodb.api.jvm.ClassSource
import org.jacodb.api.jvm.JcAnnotation
import org.jacodb.api.jvm.JcArrayType
import org.jacodb.api.jvm.JcByteCodeLocation
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcClasspathExtFeature
import org.jacodb.api.jvm.JcClasspathExtFeature.JcResolvedClassResult
import org.jacodb.api.jvm.JcClasspathExtFeature.JcResolvedTypeResult
import org.jacodb.api.jvm.JcClasspathFeature
import org.jacodb.api.jvm.JcClasspathTask
import org.jacodb.api.jvm.JcFeatureEvent
import org.jacodb.api.jvm.JcRefType
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.PredefinedPrimitives
import org.jacodb.api.jvm.RegisteredLocation
import org.jacodb.api.jvm.ext.JAVA_OBJECT
import org.jacodb.api.jvm.ext.toType
import org.jacodb.impl.bytecode.JcClassOrInterfaceImpl
import org.jacodb.impl.features.JcFeatureEventImpl
import org.jacodb.impl.features.JcFeaturesChain
import org.jacodb.impl.features.classpaths.AbstractJcResolvedResult.JcResolvedClassResultImpl
import org.jacodb.impl.features.classpaths.AbstractJcResolvedResult.JcResolvedTypeResultImpl
import org.jacodb.impl.features.classpaths.ClasspathCache
import org.jacodb.impl.features.classpaths.JcUnknownClass
import org.jacodb.impl.features.classpaths.UnknownClasses
import org.jacodb.impl.features.classpaths.isResolveAllToUnknown
import org.jacodb.impl.fs.ClassSourceImpl
import org.jacodb.impl.types.JcArrayTypeImpl
import org.jacodb.impl.types.JcClassTypeImpl
import org.jacodb.impl.types.substition.JcSubstitutorImpl
import org.jacodb.impl.vfs.ClasspathVfs
import org.jacodb.impl.vfs.GlobalClassesVfs
import kotlin.LazyThreadSafetyMode.PUBLICATION

class JcClasspathImpl(
    private val locationsRegistrySnapshot: LocationsRegistrySnapshot,
    override val db: JcDatabaseImpl,
    override val features: List<JcClasspathFeature>,
    globalClassVFS: GlobalClassesVfs
) : JcClasspath {

    override val locations: List<JcByteCodeLocation> = locationsRegistrySnapshot.locations.mapNotNull { it.jcLocation }
    override val registeredLocations: List<RegisteredLocation> = locationsRegistrySnapshot.locations
    override val registeredLocationIds: Set<Long> = locationsRegistrySnapshot.ids
    private val classpathVfs = ClasspathVfs(globalClassVFS, locationsRegistrySnapshot)
    private val featuresChain = JcFeaturesChain(
        if (!features.any { it is UnknownClasses }) {
            features + JcClasspathFeatureImpl()
        } else {
            features.filter { it !is UnknownClasses } + JcClasspathFeatureImpl() + UnknownClasses
        })

    override suspend fun refreshed(closeOld: Boolean): JcClasspath {
        return db.new(this).also {
            if (closeOld) {
                close()
            }
        }
    }

    private val javaObjectClass: JcClassOrInterface? by lazy(PUBLICATION) {
        findClassWithCache(JAVA_OBJECT)
    }

    override fun findClassOrNull(name: String): JcClassOrInterface? =
        if (JAVA_OBJECT == name) javaObjectClass else findClassWithCache(name)

    private fun findClassWithCache(name: String): JcClassOrInterface? =
        featuresChain.call<JcClasspathExtFeature, JcResolvedClassResult> {
            it.tryFindClass(this, name)
        }?.clazz

    override fun typeOf(
        jcClass: JcClassOrInterface,
        nullability: Boolean?,
        annotations: List<JcAnnotation>
    ): JcRefType {
        val jcRefType = findTypeOrNullWithNullability(jcClass.name, nullability) as? JcRefType
        jcRefType?.let {
            //
            // NB! cached type can have a different set of annotations,e.g., if it has
            // been substituted by a "substitutor"
            //
            val cachedAnnotations = jcRefType.annotations
            if (cachedAnnotations.size == annotations.size) {
                if (annotations.isEmpty() || cachedAnnotations.toSet() == annotations.toSet()) {
                    return jcRefType
                }
            }
        }
        return newClassType(jcClass, nullability, annotations)
    }

    override fun arrayTypeOf(elementType: JcType, nullability: Boolean?, annotations: List<JcAnnotation>): JcArrayType {
        return JcArrayTypeImpl(elementType, nullability, annotations)
    }

    override fun toJcClass(source: ClassSource): JcClassOrInterface {
        // findClassOrNull() can return instance of JcVirtualClass which is not expected here
        // also a duplicate class with different location can be cached
        return (findCachedClass(source.className) as? JcClassOrInterfaceImpl)?.run {
            if (source.location.id == declaration.location.id) this else null
        } ?: newClassOrInterface(source)
    }

    private val javaObjectType: JcType? by lazy(PUBLICATION) {
        findTypeOrNullWithNullability(JAVA_OBJECT)
    }

    override fun findTypeOrNull(name: String): JcType? =
        if (JAVA_OBJECT == name) javaObjectType else findTypeOrNullWithNullability(name)

    override suspend fun <T : JcClasspathTask> execute(task: T): T {
        val locations = registeredLocations.filter { task.shouldProcess(it) }
        task.before(this)
        withContext(Dispatchers.IO) {
            val parentScope = this
            locations.map {
                async {
                    val sources = db.persistence.findClassSources(db, it)
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

    override fun findClasses(name: String): Set<JcClassOrInterface> {
        return featuresChain.features.filterIsInstance<JcClasspathExtFeature>().flatMap { feature ->
            feature.findClasses(this, name).orEmpty()
        }.toSet()
    }

    override fun isInstalled(feature: JcClasspathFeature): Boolean {
        return featuresChain.features.contains(feature)
    }

    override fun close() {
        locationsRegistrySnapshot.close()
    }

    private fun findTypeOrNullWithNullability(name: String, nullable: Boolean? = null): JcType? {
        return featuresChain.call<JcClasspathExtFeature, JcResolvedTypeResult> {
            it.tryFindType(this, name, nullable)
        }?.type
    }

    private fun newClassType(
        jcClass: JcClassOrInterface,
        nullability: Boolean?,
        annotations: List<JcAnnotation>
    ): JcClassTypeImpl {
        return JcClassTypeImpl(
            this,
            jcClass.name,
            jcClass.outerClass?.toType() as? JcClassTypeImpl,
            JcSubstitutorImpl.empty,
            nullability,
            annotations
        )
    }

    private fun newClassOrInterface(source: ClassSource) = JcClassOrInterfaceImpl(this, source, featuresChain)

    private fun findCachedClass(name: String): JcClassOrInterface? {
        return featuresChain.call<JcClasspathExtFeature, JcResolvedClassResult> { feature ->
            if (feature is ClasspathCache) feature.tryFindClass(this, name) else null
        }?.clazz ?: findClassOrNull(name)
    }

    private inner class JcClasspathFeatureImpl : JcClasspathExtFeature {

        override fun tryFindClass(classpath: JcClasspath, name: String): JcResolvedClassResult? {
            val source = classpathVfs.firstClassOrNull(name)
            val jcClass = source?.let { newClassOrInterface(it.source) }
                ?: db.persistence.findClassSourceByName(classpath, name)?.let {
                    newClassOrInterface(it)
                }
            if (jcClass == null && isResolveAllToUnknown) {
                return null
            }
            return JcResolvedClassResultImpl(name, jcClass)
        }

        override fun tryFindType(classpath: JcClasspath, name: String, nullable: Boolean?): JcResolvedTypeResult? {
            if (name.endsWith("[]")) {
                val targetName = name.removeSuffix("[]")
                return JcResolvedTypeResultImpl(name,
                    findTypeOrNull(targetName)?.let { JcArrayTypeImpl(it, true) }
                )
            }
            val predefined = PredefinedPrimitives.of(name, classpath)
            if (predefined != null) {
                return JcResolvedTypeResultImpl(name, predefined)
            }
            return when (val clazz = findClassOrNull(name)) {
                null -> JcResolvedTypeResultImpl(name, null)
                is JcUnknownClass -> null // delegating to UnknownClass feature
                else -> JcResolvedTypeResultImpl(name, newClassType(clazz, nullable, clazz.annotations))
            }
        }

        override fun findClasses(classpath: JcClasspath, name: String): List<JcClassOrInterface> {
            val findClassNodes = classpathVfs.findClassNodes(name)
            val vfsClasses = findClassNodes.map { toJcClass(it.source) }
            val persistedClasses = db.persistence.findClassSources(classpath, name).map { toJcClass(it) }
            return buildSet {
                addAll(vfsClasses)
                addAll(persistedClasses)
            }.toList()
        }

        override fun event(result: Any): JcFeatureEvent {
            return JcFeatureEventImpl(this, result)
        }

    }

}
