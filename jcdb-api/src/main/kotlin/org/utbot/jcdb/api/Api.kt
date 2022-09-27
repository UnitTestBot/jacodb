package org.utbot.jcdb.api

import org.objectweb.asm.tree.ClassNode
import java.io.Closeable
import java.io.File
import java.io.InputStream

/**
 * represents structure which has access modifiers like field, class, method
 */
interface Accessible {
    /** byte-code access value */
    suspend fun access(): Int

}

enum class LocationType {
    RUNTIME,
    APP
}

/**
 * Classes container
 */
interface ClassLoadingContainer : Closeable {

    /** map name -> resources */
    val classes: Map<String, InputStream>

    override fun close() {
    }
}

interface ByteCodeContainer {
    val binary: ByteArray
    val asmNode: ClassNode
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

    /**
     * create classpath instance
     *
     * @param dirOrJars list of byte-code resources to be processed and included in classpath
     * @return new classpath instance associated with specified byte-code locations
     */
    suspend fun classpath(dirOrJars: List<File>): JcClasspath

    /**
     * process and index single byte-code resource
     * @param dirOrJar build folder or jar file
     * @return current database instance
     */
    suspend fun load(dirOrJar: File): JCDB

    /**
     * process and index byte-code resources
     * @param dirOrJars list of build folder or jar file
     * @return current database instance
     */
    suspend fun load(dirOrJars: List<File>): JCDB

    /**
     * load locations
     * @param locations locations to load
     * @return current database instance
     */
    suspend fun loadLocations(locations: List<JcByteCodeLocation>): JCDB

    /**
     * explicitly refreshes the state of resources from file-system.
     * That means that any new classpath created after refresh is done will
     * reference fresh byte-code from file-system. While any classpath created
     * before `refresh` will still reference byte-code which is outdated
     * according to file-system
     */
    suspend fun refresh()

    /**
     * rebuilds indexes
     */
    suspend fun rebuildFeatures()

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

    val persistence: JCDBPersistence
}


interface JCDBPersistence : Closeable {

    val locations: List<JcByteCodeLocation>

    fun setup()

    fun <T> write(newTx: Boolean = true, action: () -> T): T
    fun <T> read(newTx: Boolean = true, action: () -> T): T

    fun persist(location: RegisteredLocation, classes: List<ByteCodeContainer>)
    fun findClassByName(cp: JcClasspath, locations: List<RegisteredLocation>, fullName: String): JcClassOrInterface?
}

interface RegisteredLocation {
    val jcLocation: JcByteCodeLocation
    val id: Long
}