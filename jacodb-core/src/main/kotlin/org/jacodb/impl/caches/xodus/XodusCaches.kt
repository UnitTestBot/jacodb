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

package org.jacodb.impl.caches.xodus

import jetbrains.exodus.core.dataStructures.ConcurrentObjectCache
import jetbrains.exodus.core.dataStructures.ObjectCacheBase
import jetbrains.exodus.core.dataStructures.SoftConcurrentObjectCache
import org.jacodb.impl.ValueStoreType
import org.jacodb.impl.caches.*

const val XODUS_CACHE_PROVIDER_ID = "org.jacodb.impl.caches.xodus.XodusCacheProvider"

class XodusCacheProvider : PluggableCacheProvider {

    override val id = XODUS_CACHE_PROVIDER_ID

    override fun <K : Any, V : Any> newBuilder(): PluggableCacheBuilder<K, V> = XodusCacheBuilder()
}

private class XodusCacheBuilder<K : Any, V : Any> : PluggableCacheBuilder<K, V>() {
    override fun build(): PluggableCache<K, V> {
        if (valueRefType == ValueStoreType.WEAK) {
            throw PluggableCacheException("XodusCache doesn't support weak references to values")
        }
        return XodusCache(
            if (valueRefType == ValueStoreType.SOFT) {
                SoftConcurrentObjectCache(maximumSize)
            } else {
                ConcurrentObjectCache(maximumSize)
            }
        )
    }
}

/**
 * Generally, Xodus' [ObjectCacheBase] is not synchronized, but [XodusCacheBuilder] creates
 * its "concurrent" implementations which do not require synchronization. If this ever changes,
 * [XodusCache] should be synchronized.
 */
private class XodusCache<K : Any, V : Any>(private val cache: ObjectCacheBase<K, V>) : PluggableCache<K, V> {

    override fun get(key: K): V? = cache.tryKey(key)

    override fun set(key: K, value: V) {
        cache.cacheObject(key, value)
    }

    override fun remove(key: K) {
        cache.remove(key)
    }

    override fun getStats(): PluggableCacheStats = object : PluggableCacheStats {

        override val hitRate: Double = cache.hitRate().toDouble()

        override val requestCount: Long = cache.attempts.toLong()
    }
}