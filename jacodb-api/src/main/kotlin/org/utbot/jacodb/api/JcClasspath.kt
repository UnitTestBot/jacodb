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

package org.utbot.jacodb.api

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.future
import java.io.Closeable
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

    fun typeOf(jcClass: JcClassOrInterface): JcRefType

    fun arrayTypeOf(elementType: JcType): JcArrayType
    fun toJcClass(source: ClassSource, withCaching: Boolean = true): JcClassOrInterface

    suspend fun refreshed(closeOld: Boolean): JcClasspath
    fun asyncRefreshed(closeOld: Boolean) = GlobalScope.future { refreshed(closeOld) }

    suspend fun <T : JcClasspathTask> execute(task: T): JcClasspathTask

    fun <T : JcClasspathTask> executeAsync(task: T): Future<JcClasspathTask> = GlobalScope.future { execute(task) }
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
        process(classpath.toJcClass(source, true))
    }

    fun process(clazz: JcClassOrInterface)
}

