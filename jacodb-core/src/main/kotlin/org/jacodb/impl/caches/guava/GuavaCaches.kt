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

package org.jacodb.impl.caches.guava

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import org.jacodb.impl.ValueStoreType
import org.jacodb.impl.caches.PluggableCache
import org.jacodb.impl.caches.PluggableCacheBuilder
import org.jacodb.impl.caches.PluggableCacheProvider
import org.jacodb.impl.caches.PluggableCacheStats
import java.time.Duration

const val GUAVA_CACHE_PROVIDER_ID = "org.jacodb.impl.caches.guava.GuavaCacheProvider"

class GuavaCacheProvider : PluggableCacheProvider {

    override val id = GUAVA_CACHE_PROVIDER_ID

    override fun <K : Any, V : Any> newBuilder(): PluggableCacheBuilder<K, V> = GuavaCacheBuilder()
}

private class GuavaCacheBuilder<K : Any, V : Any> : PluggableCacheBuilder<K, V>() {

    override fun build(): PluggableCache<K, V> {
        return GuavaCache(
            CacheBuilder.newBuilder()
                .maximumSize(maximumSize.toLong())
                .apply {
                    expirationDuration.let {
                        if (it != Duration.ZERO) {
                            expireAfterAccess(it)
                        }
                    }
                    when (valueRefType) {
                        ValueStoreType.WEAK -> weakValues()
                        ValueStoreType.SOFT -> softValues()
                        ValueStoreType.STRONG -> {} // do nothing
                    }
                }
                .recordStats()
                .build()
        )
    }
}

private class GuavaCache<K : Any, V : Any>(private val guavaCache: Cache<K, V>) : PluggableCache<K, V> {

    override fun get(key: K): V? = guavaCache.getIfPresent(key)

    override fun set(key: K, value: V) = guavaCache.put(key, value)

    override fun remove(key: K) = guavaCache.invalidate(key)

    override fun getStats() = object : PluggableCacheStats {

        val guavaStats = guavaCache.stats()

        override val hitRate: Double get() = guavaStats.hitRate()

        override val requestCount: Long get() = guavaStats.requestCount()
    }
}