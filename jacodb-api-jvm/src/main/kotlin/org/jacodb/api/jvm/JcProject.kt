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

package org.jacodb.api.jvm

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.future
import org.jacodb.api.core.Project
import org.jacodb.api.jvm.cfg.JcGraph
import org.jacodb.api.core.cfg.InstList
import org.jacodb.api.jvm.cfg.JcRawInst
import org.jacodb.api.jvm.cfg.JcInst
import java.util.concurrent.Future

/**
 * Represents classpath, i.e. number of locations of byte code.
 *
 * Classpath **must be** closed when it's not needed anymore.
 * This will release references from database to possibly outdated libraries
 */
interface JcProject : Project<JcType> {

    /** locations of this classpath */
    val db: JcDatabase

    /** locations of this classpath */
    val locations: List<JcByteCodeLocation>
    val registeredLocations: List<RegisteredLocation>
    val features: List<JcClasspathFeature>?

    /**
     *  @param name full name of the type
     *
     * @return class or interface or null if there is no such class found in locations
     */
    fun findClassOrNull(name: String): JcClassOrInterface?

    /**
     * in case of jar-hell there could be few classes with same name inside classpath
     * @param name - class name
     *
     * @return list of classes with that name
     */
    fun findClasses(name: String): Set<JcClassOrInterface>

    override fun findTypeOrNull(name: String): JcType?

    fun typeOf(
        jcClass: JcClassOrInterface,
        nullability: Boolean? = null,
        annotations: List<JcAnnotation> = listOf()
    ): JcRefType

    fun arrayTypeOf(
        elementType: JcType,
        nullability: Boolean? = null,
        annotations: List<JcAnnotation> = listOf()
    ): JcArrayType

    fun toJcClass(source: ClassSource): JcClassOrInterface

    suspend fun refreshed(closeOld: Boolean): JcProject
    fun asyncRefreshed(closeOld: Boolean) = GlobalScope.future { refreshed(closeOld) }

    suspend fun <T : JcClasspathTask> execute(task: T): T

    fun <T : JcClasspathTask> executeAsync(task: T): Future<T> = GlobalScope.future { execute(task) }

    fun isInstalled(feature: JcClasspathFeature): Boolean
}


interface JcClasspathTask {

    fun before(classpath: JcProject) {
    }

    fun after(classpath: JcProject) {

    }

    fun shouldProcess(registeredLocation: RegisteredLocation): Boolean = true
    fun shouldProcess(classSource: ClassSource): Boolean = true

    fun process(source: ClassSource, classpath: JcProject)

}


interface JcClassProcessingTask : JcClasspathTask {

    override fun process(source: ClassSource, classpath: JcProject) {
        process(classpath.toJcClass(source))
    }

    fun process(clazz: JcClassOrInterface)
}

/**
 * Implementation should be idempotent that means that results should not be changed during time
 * Result of this
 */
@JvmDefaultWithoutCompatibility
interface JcClasspathFeature {

    fun on(event: JcFeatureEvent) {
    }

    fun event(result: Any): JcFeatureEvent? = null

}

interface JcFeatureEvent {
    val feature: JcClasspathFeature
    val result: Any
}

@JvmDefaultWithoutCompatibility
interface JcClasspathExtFeature : JcClasspathFeature {

    interface JcResolvedClassResult {
        val name: String
        val clazz: JcClassOrInterface?
    }

    interface JcResolvedClassesResult {
        val name: String
        val clazz: List<JcClassOrInterface>
    }

    interface JcResolvedTypeResult {
        val name: String
        val type: JcType?
    }

    /**
     * semantic of method is like this:
     * - not empty optional for found class
     * - empty optional for class that we know is not exist in classpath
     * - null for case when we do not know
     */
    fun tryFindClass(classpath: JcProject, name: String): JcResolvedClassResult? = null

    /**
     * semantic is the same as for `tryFindClass` method
     */
    fun tryFindType(classpath: JcProject, name: String): JcResolvedTypeResult? = null

    fun findClasses(classpath: JcProject, name: String): List<JcClassOrInterface>? = null

}

@JvmDefaultWithoutCompatibility
interface JcClassExtFeature : JcClasspathFeature {

    fun fieldsOf(clazz: JcClassOrInterface): List<JcField>? = null
    fun fieldsOf(clazz: JcClassOrInterface, originalFields: List<JcField>): List<JcField>? = fieldsOf(clazz)
    fun methodsOf(clazz: JcClassOrInterface): List<JcMethod>? = null
    fun methodsOf(clazz: JcClassOrInterface, originalMethods: List<JcMethod>): List<JcMethod>? = methodsOf(clazz)

    fun extensionValuesOf(clazz: JcClassOrInterface): Map<String, Any>? = null

}

interface JcLookupExtFeature : JcClasspathFeature {
    fun lookup(clazz: JcClassOrInterface): JcLookup<JcField, JcMethod>
    fun lookup(type: JcClassType): JcLookup<JcTypedField, JcTypedMethod>
}

interface JcGenericsSubstitutionFeature : JcClasspathFeature {

    fun substitute(clazz: JcClassOrInterface, parameters: List<JvmType>, outer: JcSubstitutor?): JcSubstitutor
}

@JvmDefaultWithoutCompatibility
interface JcInstExtFeature : JcClasspathFeature {
    fun transformRawInstList(method: JcMethod, list: InstList<JcRawInst>): InstList<JcRawInst> = list
    fun transformInstList(method: JcMethod, list: InstList<JcInst>): InstList<JcInst> = list
}

@JvmDefaultWithoutCompatibility
interface JcMethodExtFeature : JcClasspathFeature {

    interface JcFlowGraphResult {
        val method: JcMethod
        val flowGraph: JcGraph
    }
    interface JcInstListResult {
        val method: JcMethod
        val instList: InstList<JcInst>
    }
    interface JcRawInstListResult {
        val method: JcMethod
        val rawInstList: InstList<JcRawInst>
    }

    fun flowGraph(method: JcMethod): JcFlowGraphResult? = null
    fun instList(method: JcMethod): JcInstListResult? = null
    fun rawInstList(method: JcMethod): JcRawInstListResult? = null

}