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

import kotlinx.coroutines.*
import org.jacodb.api.*
import org.jacodb.api.jvm.ClassSource
import org.jacodb.api.jvm.JcAnnotation
import org.jacodb.api.jvm.JcArrayType
import org.jacodb.api.jvm.JcByteCodeLocation
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClasspathExtFeature
import org.jacodb.api.jvm.JcRefType
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.PredefinedJcPrimitives
import org.jacodb.api.jvm.JcClasspathExtFeature.JcResolvedClassResult
import org.jacodb.api.jvm.JcClasspathExtFeature.JcResolvedTypeResult
import org.jacodb.api.jvm.JcClasspathFeature
import org.jacodb.api.jvm.JcClasspathTask
import org.jacodb.api.jvm.JcFeatureEvent
import org.jacodb.api.jvm.JcProject
import org.jacodb.api.jvm.RegisteredLocation
import org.jacodb.api.jvm.ext.toType
import org.jacodb.impl.bytecode.JcClassOrInterfaceImpl
import org.jacodb.impl.features.JcFeatureEventImpl
import org.jacodb.impl.features.JcFeaturesChain
import org.jacodb.impl.features.classpaths.AbstractJcResolvedResult.JcResolvedClassResultImpl
import org.jacodb.impl.features.classpaths.AbstractJcResolvedResult.JcResolvedTypeResultImpl
import org.jacodb.impl.features.classpaths.JcUnknownClass
import org.jacodb.impl.features.classpaths.UnknownClasses
import org.jacodb.impl.features.classpaths.isResolveAllToUnknown
import org.jacodb.impl.fs.ClassSourceImpl
import org.jacodb.impl.types.JcArrayTypeImpl
import org.jacodb.impl.types.JcClassTypeImpl
import org.jacodb.impl.types.substition.JcSubstitutorImpl
import org.jacodb.impl.vfs.ClasspathVfs
import org.jacodb.impl.vfs.GlobalClassesVfs

class JcProjectImpl(
    private val locationsRegistrySnapshot: LocationsRegistrySnapshot,
    override val db: JcDatabaseImpl,
    override val features: List<JcClasspathFeature>,
    globalClassVFS: GlobalClassesVfs
) : JcProject {

    override val locations: List<JcByteCodeLocation> = locationsRegistrySnapshot.locations.mapNotNull { it.jcLocation }
    override val registeredLocations: List<RegisteredLocation> = locationsRegistrySnapshot.locations

    private val classpathVfs = ClasspathVfs(globalClassVFS, locationsRegistrySnapshot)
    private val featuresChain = run{
        val strictFeatures = features.filter { it !is UnknownClasses }
        val hasUnknownClasses = strictFeatures.size != features.size
        JcFeaturesChain(strictFeatures + listOfNotNull(JcClasspathFeatureImpl(), UnknownClasses.takeIf { hasUnknownClasses }) )
    }

    override suspend fun refreshed(closeOld: Boolean): JcProject {
        return db.new(this).also {
            if (closeOld) {
                close()
            }
        }
    }

    override fun findClassOrNull(name: String): JcClassOrInterface? {
        return featuresChain.call<JcClasspathExtFeature, JcResolvedClassResult> {
            it.tryFindClass(this, name)
        }?.clazz
    }

    override fun typeOf(
        jcClass: JcClassOrInterface,
        nullability: Boolean?,
        annotations: List<JcAnnotation>
    ): JcRefType {
        return JcClassTypeImpl(
            this,
            jcClass.name,
            jcClass.outerClass?.toType() as? JcClassTypeImpl,
            JcSubstitutorImpl.empty,
            nullability,
            annotations
        )
    }

    override fun arrayTypeOf(elementType: JcType, nullability: Boolean?, annotations: List<JcAnnotation>): JcArrayType {
        return JcArrayTypeImpl(elementType, nullability, annotations)
    }

    override fun toJcClass(source: ClassSource): JcClassOrInterface {
        return JcClassOrInterfaceImpl(this, source, featuresChain)
    }

    override fun findTypeOrNull(name: String): JcType? {
        return featuresChain.call<JcClasspathExtFeature, JcResolvedTypeResult> {
            it.tryFindType(this, name)
        }?.type
    }

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
                            task.process(it, this@JcProjectImpl)
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

    private inner class JcClasspathFeatureImpl : JcClasspathExtFeature {

        override fun tryFindClass(classpath: JcProject, name: String): JcResolvedClassResult? {
            val source = classpathVfs.firstClassOrNull(name)
            val jcClass = source?.let { toJcClass(it.source) }
                ?: db.persistence.findClassSourceByName(classpath, name)?.let {
                    toJcClass(it)
                }
            if (jcClass == null && isResolveAllToUnknown) {
                return null
            }
            return JcResolvedClassResultImpl(name, jcClass)
        }

        override fun tryFindType(classpath: JcProject, name: String): JcResolvedTypeResult? {
            if (name.endsWith("[]")) {
                val targetName = name.removeSuffix("[]")
                return JcResolvedTypeResultImpl(name,
                    findTypeOrNull(targetName)?.let { JcArrayTypeImpl(it, true) }
                )
            }
            val predefined = PredefinedJcPrimitives.of(name, classpath)
            if (predefined != null) {
                return JcResolvedTypeResultImpl(name, predefined)
            }
            return when (val clazz = findClassOrNull(name)) {
                null -> JcResolvedTypeResultImpl(name, null)
                is JcUnknownClass -> null // delegating to UnknownClass feature
                else -> JcResolvedTypeResultImpl(name, typeOf(clazz))
            }
        }

        override fun findClasses(classpath: JcProject, name: String): List<JcClassOrInterface> {
            val vfsClasses = classpathVfs.findClassNodes(name).map { toJcClass(it.source) }
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


