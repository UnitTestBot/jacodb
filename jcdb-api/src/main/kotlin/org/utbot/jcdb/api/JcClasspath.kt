package org.utbot.jcdb.api

import java.io.Closeable
import java.io.Serializable

/**
 * Represents classpath, i.e. number of locations of byte code.
 *
 * Classpath **must be** closed when it's not needed anymore.
 * This will release references from database to possibly outdated libraries
 */
interface JcClasspath : Closeable {

    /** locations of this classpath */
    val db: JCDB

    /** locations of this classpath */
    val locations: List<JcByteCodeLocation>

    /**
     *  @param name full name of the type
     *
     * @return class or interface or null if there is no such class found in locations
     */
    suspend fun findClassOrNull(name: String): JcClassOrInterface?

    /**
     *  @param name full name of the type
     *
     * @return class or interface or null if there is no such class found in locations
     */
    suspend fun findTypeOrNull(name: String): JcType?

    suspend fun typeOf(jcClass: JcClassOrInterface): JcRefType

    suspend fun arrayTypeOf(elementType: JcType): JcArrayType

    suspend fun refreshed(closeOld: Boolean): JcClasspath

    /**
     * @param name full name of the class
     * @param allHierarchy search will return all subclasses through all hierarchy
     * @return list of direct subclasses if they are exists in classpath or empty list
     */
    suspend fun findSubClasses(name: String, allHierarchy: Boolean = false): List<JcClassOrInterface>

    /**
     * @param jcClass class of super class
     * @param allHierarchy search will return all subclasses through all hierarchy
     * @return list of direct subclasses if they are exists in classpath or empty list
     */
    suspend fun findSubClasses(jcClass: JcClassOrInterface, allHierarchy: Boolean = false): List<JcClassOrInterface>

    /**
     * query index by specified term
     *
     * @param key index key
     * @param req term for index
     */
    suspend fun <RES : Serializable, REQ : Serializable> query(key: String, req: REQ): Sequence<RES>
}