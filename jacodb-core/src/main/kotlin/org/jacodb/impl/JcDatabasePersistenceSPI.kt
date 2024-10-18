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

import org.jacodb.api.jvm.JcDatabase
import org.jacodb.api.jvm.JcDatabasePersistence
import org.jacodb.api.spi.CommonSPI
import org.jacodb.api.spi.SPILoader
import org.jacodb.impl.fs.JavaRuntime

class JcDatabaseException(message: String) : RuntimeException(message)

/**
 * Service Provider Interface to load pluggable implementation of [JcDatabasePersistence].
 */
interface JcDatabasePersistenceSPI : CommonSPI {

    /**
     * Id of [JcDatabasePersistence] which is used to select particular persistence implementation.
     * It can be an arbitrary unique string, but use of fully qualified name of the class
     * implementing [JcDatabasePersistenceSPI] is preferable.
     */
    override val id: String

    /**
     * Creates new instance of [JcDatabasePersistence] specified [JavaRuntime] and [JcSettings].
     * @param runtime - Java runtime which database persistence should be created for
     * @param settings - settings to use for creation of [JcDatabasePersistence] instance
     * @return new [JcDatabasePersistence] instance
     */
    fun newPersistence(runtime: JavaRuntime, settings: JcSettings): JcDatabasePersistence

    /**
     * Creates new instance of [LocationsRegistry] and bind it to specified [JcDatabase].
     * Implementation of [LocationsRegistry] is specific to persistence provided by this SPI.
     * [LocationsRegistry] is always being created _after_ corresponding [JcDatabasePersistence]
     * is created.
     * @param jcdb - [JcDatabase] which [LocationsRegistry] is bound to
     */
    fun newLocationsRegistry(jcdb: JcDatabase): LocationsRegistry

    companion object : SPILoader() {

        @JvmStatic
        fun getProvider(id: String): JcDatabasePersistenceSPI {
            return loadSPI(id) ?: throw JcDatabaseException("No JcDatabasePersistenceSPI found by id = $id")
        }
    }
}