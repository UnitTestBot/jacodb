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

package org.jacodb.testing.caches

import org.jacodb.impl.caches.PluggableCache
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

const val CACHE_SIZE = 100

abstract class PluggableCacheTest {

    abstract val cacheId: String

    private lateinit var cache: PluggableCache<Int, String>

    @BeforeEach
    fun instantiateCache() {
        cache = PluggableCache.of(cacheId) {
            maximumSize = CACHE_SIZE
        }
    }

    @Test
    fun `put get`() {
        (0..9).forEach {
            cache[it] = it.toString()
        }
        (0..9).forEach {
            assertEquals(it.toString(), cache[it])
        }
    }

    @Test
    fun `put get evict by size`() {
        (0..CACHE_SIZE).forEach {
            cache[it] = it.toString()
        }
        // at least one pair should be evicted is cache has fair eviction
        assertTrue((0..CACHE_SIZE).firstOrNull { cache[it] == null } != null)
    }

    @Test
    fun `put get evict by size get stats`() {
        (0..CACHE_SIZE).forEach {
            cache[it] = it.toString()
        }
        // at least one pair should be evicted is cache has fair eviction
        val lastEvicted = (0..CACHE_SIZE).lastOrNull { cache[it] == null }
        assertTrue(lastEvicted != null)
        val stats = cache.getStats()
        assertEquals(CACHE_SIZE + 1, stats.requestCount.toInt())
        assertTrue(stats.hitRate > 0)
        assertTrue(stats.hitRate < 1.0)
    }
}