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

package org.jacodb.impl.storage.ers.ram

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.toPersistentHashMap
import kotlin.collections.Map.Entry

internal class TransactionalPersistentMap<K, V>(private val committed: PersistentMap<K, V> = mapOf<K, V>().toPersistentHashMap()) {

    private var mutated: PersistentMap.Builder<K, V>? = null

    operator fun get(key: K): V? {
        mutated?.let {
            return it[key]
        }
        return committed[key]
    }

    fun keys(): Iterable<K> = (mutated ?: committed).entries.map { it.key }

    fun entries(): Iterable<Entry<K, V>> = (mutated ?: committed).entries

    fun put(key: K, value: V) {
        mutated()[key] = value
    }

    fun getClone(): TransactionalPersistentMap<K, V> {
        return if (mutated != null) this else TransactionalPersistentMap(committed)
    }

    fun commit(): TransactionalPersistentMap<K, V> {
        return mutated?.let {
            // the following line is not necessary, but earlier reclaim of used memory
            // looks effective according to benchmarks
            mutated = null
            TransactionalPersistentMap(it.build())
        } ?: this
    }

    private fun mutated(): PersistentMap.Builder<K, V> {
        return mutated ?: committed.builder().also { mutated = it }
    }
}