package org.utbot.jcdb.api

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.future
import java.io.Closeable

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

    suspend fun refreshed(closeOld: Boolean): JcClasspath
    fun asyncRefreshed(closeOld: Boolean) = GlobalScope.future { refreshed(closeOld) }

}