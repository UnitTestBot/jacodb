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

package org.jacodb.impl.storage.kv.xodus

import jetbrains.exodus.ArrayByteIterable
import jetbrains.exodus.ByteIterable
import jetbrains.exodus.core.dataStructures.ObjectCacheBase
import jetbrains.exodus.env.EnvironmentConfig

internal val ByteArray.asByteIterable: ByteIterable get() = ArrayByteIterable(this)

internal val ByteIterable.asByteArray: ByteArray
    get() = bytesUnsafe.let { array -> if (array.size == length) array else array.copyOf(length) }

internal fun environmentConfig(configurer: EnvironmentConfig.() -> Unit) = EnvironmentConfig().apply(configurer)

fun <K : Any, V : Any> ObjectCacheBase<K, V>.getOrElse(key: K, retriever: (K) -> V): V {
    return tryKey(key) ?: retriever(key)
}

fun <K : Any, V : Any> ObjectCacheBase<K, V>.getOrPut(key: K, retriever: (K) -> V): V {
    return getOrElse(key, retriever).also { obj -> cacheObject(key, obj) }
}

fun <K : Any, V : Any> ObjectCacheBase<K, V>.getOrPutConcurrent(key: K, retriever: (K) -> V): V {
    return tryKeyLocked(key) ?: retriever(key).also { obj -> newCriticalSection().use { cacheObject(key, obj) } }
}