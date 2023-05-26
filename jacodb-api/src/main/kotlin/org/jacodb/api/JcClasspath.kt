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

package org.jacodb.api

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.future
import org.jacodb.api.cfg.JcGraph
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.cfg.JcInstList
import org.jacodb.api.cfg.JcRawInst
import java.io.Closeable
import java.util.*
import java.util.concurrent.Future

/**
 * Represents classpath, i.e. number of locations of byte code.
 *
 * Classpath **must be** closed when it's not needed anymore.
 * This will release references from database to possibly outdated libraries
 */
interface JcClasspath : Closeable {

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
     *  @param name full name of the type
     *
     * @return class or interface or null if there is no such class found in locations
     */
    fun findTypeOrNull(name: String): JcType?

    fun typeOf(jcClass: JcClassOrInterface, nullability: Boolean? = null, annotations: List<JcAnnotation> = listOf()): JcRefType

    fun arrayTypeOf(elementType: JcType, nullability: Boolean? = null, annotations: List<JcAnnotation> = listOf()): JcArrayType

    fun toJcClass(source: ClassSource): JcClassOrInterface

    suspend fun refreshed(closeOld: Boolean): JcClasspath
    fun asyncRefreshed(closeOld: Boolean) = GlobalScope.future { refreshed(closeOld) }

    suspend fun <T : JcClasspathTask> execute(task: T): T

    fun <T : JcClasspathTask> executeAsync(task: T): Future<T> = GlobalScope.future { execute(task) }
}


interface JcClasspathTask {

    fun before(classpath: JcClasspath) {
    }

    fun after(classpath: JcClasspath) {

    }

    fun shouldProcess(registeredLocation: RegisteredLocation): Boolean = true
    fun shouldProcess(classSource: ClassSource): Boolean = true

    fun process(source: ClassSource, classpath: JcClasspath)

}


interface JcClassProcessingTask : JcClasspathTask {

    override fun process(source: ClassSource, classpath: JcClasspath) {
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

    fun event(result: Any, input: Array<Any>): JcFeatureEvent? = null

}

interface JcFeatureEvent {
    val feature: JcClasspathFeature
    val result: Any
    val input: Array<Any>
}

@JvmDefaultWithoutCompatibility
interface JcClasspathExtFeature : JcClasspathFeature {

    /**
     * semantic of method is like this:
     * - not empty optional for found class
     * - empty optional for class that we know is not exist in classpath
     * - null for case when we do not know
     */
    fun tryFindClass(classpath: JcClasspath, name: String): Optional<JcClassOrInterface>? = null

    /**
     * semantic is the same as for `tryFindClass` method
     */
    fun tryFindType(classpath: JcClasspath, name: String): Optional<JcType>? = null

}

@JvmDefaultWithoutCompatibility
interface JcClassExtFeature : JcClasspathFeature {

    fun fieldsOf(clazz: JcClassOrInterface): List<JcField>? = null
    fun methodsOf(clazz: JcClassOrInterface): List<JcMethod>? = null

    fun extensionValuesOf(clazz: JcClassOrInterface): Map<String, Any>? = null

}

@JvmDefaultWithoutCompatibility
interface JcInstExtFeature : JcClasspathFeature {

    fun transformRawInstList(method: JcMethod, list: JcInstList<JcRawInst>): JcInstList<JcRawInst> = list
    fun transformInstList(method: JcMethod, list: JcInstList<JcInst>): JcInstList<JcInst> = list
}

interface JcMethodExtFeature : JcClasspathFeature {

    fun flowGraph(method: JcMethod): JcGraph? = null
    fun instList(method: JcMethod): JcInstList<JcInst>? = null
    fun rawInstList(method: JcMethod): JcInstList<JcRawInst>? = null

}
