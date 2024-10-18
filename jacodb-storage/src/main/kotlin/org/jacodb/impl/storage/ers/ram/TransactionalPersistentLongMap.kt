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

import jetbrains.exodus.core.dataStructures.persistent.PersistentLong23TreeMap
import jetbrains.exodus.core.dataStructures.persistent.PersistentLongMap
import org.jacodb.api.storage.ers.ERSException

class TransactionalPersistentLongMap<V : Any>(
    private val committed: PersistentLongMap<V> = PersistentLong23TreeMap()
) : MutableContainer<TransactionalPersistentLongMap<V>> {

    private var mutated: PersistentLongMutableMap<V>? = null

    fun keys(): Iterable<Long> = (mutated ?: committed.beginRead()).map { it.key }

    fun entries(): Iterable<PersistentLongMap.Entry<V>> = (mutated ?: committed.beginRead())

    operator fun get(key: Long): V? {
        mutated?.let {
            return it[key]
        }
        return committed[key]
    }

    override val isMutable: Boolean get() = mutated != null

    override fun mutate(): TransactionalPersistentLongMap<V> {
        return if (mutated != null) this else TransactionalPersistentLongMap(committed.clone)
    }

    override fun commit(): TransactionalPersistentLongMap<V> {
        return mutated?.let {
            // the following line is not necessary, but earlier reclaim of used memory
            // looks effective according to benchmarks
            mutated = null
            if (!it.endWrite()) {
                throw ERSException("Failed to commit TransactionalPersistentLongMap")
            }
            TransactionalPersistentLongMap(committed)
        } ?: this
    }

    fun put(key: Long, value: V) {
        mutated().put(key, value)
    }

    fun remove(key: Long) {
        mutated().remove(key)
    }

    private fun mutated(): PersistentLongMutableMap<V> {
        return mutated ?: committed.beginWrite().also { mutated = it }
    }
}