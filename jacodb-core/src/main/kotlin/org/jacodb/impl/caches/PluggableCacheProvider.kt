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

package org.jacodb.impl.caches

import org.jacodb.api.spi.CommonSPI
import org.jacodb.api.spi.SPILoader

/**
 * Service Provider Interface to load pluggable implementation of [PluggableCacheBuilder] and [PluggableCache]
 */
interface PluggableCacheProvider : CommonSPI {

    /**
     * Id of [PluggableCacheProvider] which is used to select particular cache implementation.
     * It can be an arbitrary unique string, but use of fully qualified name of the class
     * implementing [PluggableCacheProvider] is preferable.
     */
    override val id: String

    /**
     * Creates new instance of [PluggableCacheBuilder].
     */
    fun <K : Any, V : Any> newBuilder(): PluggableCacheBuilder<K, V>

    /**
     * Creates a cache instance using internally created [PluggableCacheBuilder] with respect to
     * specified lambda which is used to configure the builder.
     */
    fun <K : Any, V : Any> newCache(configurator: PluggableCacheBuilder<K, V>.() -> Unit): PluggableCache<K, V> {
        val builder = newBuilder<K, V>().apply(configurator)
        return builder.build()
    }

    companion object : SPILoader() {

        @JvmStatic
        fun getProvider(id: String): PluggableCacheProvider {
            return loadSPI(id) ?: throw PluggableCacheException("No PluggableCacheProvider found by id = $id")
        }
    }
}