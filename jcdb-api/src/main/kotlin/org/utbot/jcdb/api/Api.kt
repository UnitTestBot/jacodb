package org.utbot.jcdb.api

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.future
import org.jooq.DSLContext
import java.io.Closeable
import java.io.File

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


/**
 * Compilation database
 *
 * `close` method should be called when database is not needed anymore
 */
interface JCDB : Closeable {

    val locations: List<JcByteCodeLocation>
    val persistence: JCDBPersistence

    /**
     * create classpath instance
     *
     * @param dirOrJars list of byte-code resources to be processed and included in classpath
     * @return new classpath instance associated with specified byte-code locations
     */
    suspend fun classpath(dirOrJars: List<File>): JcClasspath
    fun asyncClasspath(dirOrJars: List<File>) = GlobalScope.future { classpath(dirOrJars) }

    /**
     * process and index single byte-code resource
     * @param dirOrJar build folder or jar file
     * @return current database instance
     */
    suspend fun load(dirOrJar: File): JCDB
    fun asyncLoad(dirOrJar: File) = GlobalScope.future { load(dirOrJar) }

    /**
     * process and index byte-code resources
     * @param dirOrJars list of build folder or jar file
     * @return current database instance
     */
    suspend fun load(dirOrJars: List<File>): JCDB
    fun asyncLoad(dirOrJars: List<File>) = GlobalScope.future { load(dirOrJars) }

    /**
     * load locations
     * @param locations locations to load
     * @return current database instance
     */
    suspend fun loadLocations(locations: List<JcByteCodeLocation>): JCDB
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
    fun watchFileSystemChanges(): JCDB

    /**
     * await background jobs
     */
    suspend fun awaitBackgroundJobs()
    fun asyncAwaitBackgroundJobs() = GlobalScope.future { awaitBackgroundJobs() }

    fun isInstalled(feature: JcFeature<*,*>): Boolean
}


interface JCDBPersistence : Closeable {

    val locations: List<JcByteCodeLocation>

    fun setup()

    fun <T> write(action: (DSLContext) -> T): T
    fun <T> read(action: (DSLContext) -> T): T

    fun persist(location: RegisteredLocation, classes: List<ClassSource>)
    fun findSymbolId(symbol: String): Long?

    fun findClassSourceByName(cp: JcClasspath, locations: List<RegisteredLocation>, fullName: String): ClassSource?
    fun findClassSources(location: RegisteredLocation): List<ClassSource>
}

interface RegisteredLocation {
    val jcLocation: JcByteCodeLocation
    val id: Long
}