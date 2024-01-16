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
import org.jooq.DSLContext
import java.io.Closeable
import java.io.File
import java.sql.Connection

enum class LocationType {
    RUNTIME,
    APP
}

interface ClassSource {
    val className: String
    val byteCode: ByteArray
    val location: RegisteredLocation
}

/**
 * field usage mode
 */
enum class FieldUsageMode {

    /** search for reads */
    READ,

    /** search for writes */
    WRITE
}

interface JavaVersion {
    val majorVersion: Int
}


/**
 * Compilation database
 *
 * `close` method should be called when database is not needed anymore
 */
@JvmDefaultWithoutCompatibility
interface JcDatabase : Closeable {

    val locations: List<RegisteredLocation>
    val persistence: JcDatabasePersistence

    val runtimeVersion: JavaVersion

    /**
     * create classpath instance
     *
     * @param dirOrJars list of byte-code resources to be processed and included in classpath
     * @return new classpath instance associated with specified byte-code locations
     */
    suspend fun classpath(dirOrJars: List<File>, features: List<JcClasspathFeature>?): JcProject
    suspend fun classpath(dirOrJars: List<File>): JcProject = classpath(dirOrJars, null)
    fun asyncClasspath(dirOrJars: List<File>) = GlobalScope.future { classpath(dirOrJars) }
    fun asyncClasspath(dirOrJars: List<File>, features: List<JcClasspathFeature>?) =
        GlobalScope.future { classpath(dirOrJars, features) }

    fun classpathOf(locations: List<RegisteredLocation>, features: List<JcClasspathFeature>?): JcProject

    /**
     * process and index single byte-code resource
     * @param dirOrJar build folder or jar file
     * @return current database instance
     */
    suspend fun load(dirOrJar: File): JcDatabase
    fun asyncLoad(dirOrJar: File) = GlobalScope.future { load(dirOrJar) }

    /**
     * process and index byte-code resources
     * @param dirOrJars list of build folder or jar file
     * @return current database instance
     */
    suspend fun load(dirOrJars: List<File>): JcDatabase
    fun asyncLoad(dirOrJars: List<File>) = GlobalScope.future { load(dirOrJars) }

    /**
     * load locations
     * @param locations locations to load
     * @return current database instance
     */
    suspend fun loadLocations(locations: List<JcByteCodeLocation>): JcDatabase
    fun asyncLocations(locations: List<JcByteCodeLocation>) = GlobalScope.future { loadLocations(locations) }

    /**
     * explicitly refreshes the state of resources from file-system.
     * That means that any new classpath created after refresh is done will
     * reference fresh byte-code from file-system. While any classpath created
     * before `refresh` will still reference byte-code which is outdated
     * according to file-system
     */
    suspend fun refresh()
    fun asyncRefresh() = GlobalScope.future { refresh() }

    /**
     * rebuilds features data (indexes)
     */
    suspend fun rebuildFeatures()
    fun asyncRebuildFeatures() = GlobalScope.future { rebuildFeatures() }

    /**
     * watch file system for changes and refreshes the state of database in case loaded resources and resources from
     * file systems are different.
     *
     * @return current database instance
     */
    fun watchFileSystemChanges(): JcDatabase

    /**
     * await background jobs
     */
    suspend fun awaitBackgroundJobs()
    fun asyncAwaitBackgroundJobs() = GlobalScope.future { awaitBackgroundJobs() }

    fun isInstalled(feature: JcFeature<*, *>): Boolean = features.contains(feature)

    val features: List<JcFeature<*, *>>
}


interface JcDatabasePersistence : Closeable {

    val locations: List<JcByteCodeLocation>

    fun setup()

    fun <T> write(action: (DSLContext) -> T): T
    fun <T> read(action: (DSLContext) -> T): T

    fun persist(location: RegisteredLocation, classes: List<ClassSource>)
    fun findSymbolId(symbol: String): Long?
    fun findSymbolName(symbolId: Long): String
    fun findLocation(locationId: Long): RegisteredLocation

    val symbolInterner: JCDBSymbolsInterner
    fun findBytecode(classId: Long): ByteArray

    fun findClassSourceByName(cp: JcProject, fullName: String): ClassSource?
    fun findClassSources(db: JcDatabase, location: RegisteredLocation): List<ClassSource>
    fun findClassSources(cp: JcProject, fullName: String): List<ClassSource>

    fun createIndexes() {}
}

interface RegisteredLocation {
    val jcLocation: JcByteCodeLocation?
    val id: Long
    val path: String
    val isRuntime: Boolean
}

interface JCDBSymbolsInterner {
    val jooq: DSLContext
    fun findOrNew(symbol: String): Long
    fun flush(conn: Connection)
}