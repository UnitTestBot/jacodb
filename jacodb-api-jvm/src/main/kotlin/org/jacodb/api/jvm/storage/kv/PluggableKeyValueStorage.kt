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

package org.jacodb.api.jvm.storage.kv

import org.jacodb.api.jvm.spi.CommonSPI
import org.jacodb.api.jvm.spi.SPILoader
import org.jacodb.api.jvm.storage.ers.EmptyErsSettings
import org.jacodb.api.jvm.storage.ers.ErsSettings
import java.io.Closeable

abstract class PluggableKeyValueStorage : Closeable {

    abstract fun beginTransaction(): Transaction

    abstract fun beginReadonlyTransaction(): Transaction

    fun <T> transactional(action: (Transaction) -> T) = beginTransaction().use(action)

    fun <T> readonlyTransactional(action: (Transaction) -> T) = beginReadonlyTransaction().use(action)

    fun get(map: String, key: ByteArray) = readonlyTransactional { txn -> txn.get(map, key) }

    fun put(map: String, key: ByteArray, value: ByteArray) = transactional { txn -> txn.put(map, key, value) }

    fun delete(map: String, key: ByteArray) = transactional { txn -> txn.delete(map, key) }

    fun mapSize(map: String): Long = transactional { txn -> txn.getNamedMap(map).size(txn) }

    fun all(map: String): List<Pair<ByteArray, ByteArray>> = readonlyTransactional { txn ->
        buildList {
            txn.navigateTo(map).forEach { key, value ->
                add(key to value)
            }
        }
    }

    /**
     * Predicate answering the question if a map with specified name should have key duplicates?
     * The answer should be a 3-value boolean: `true` - yes, `false` - no, `null` - it doesn't matter.
     */
    var isMapWithKeyDuplicates: ((String) -> Boolean?)? = null
}

interface PluggableKeyValueStorageSPI : CommonSPI {

    fun newStorage(location: String?, settings: ErsSettings = EmptyErsSettings): PluggableKeyValueStorage

    companion object : SPILoader() {

        @JvmStatic
        fun getProvider(id: String): PluggableKeyValueStorageSPI {
            return loadSPI(id)
                ?: throw KVStorageException("No PluggableKeyValueStorageSPI implementation found by id = $id")
        }
    }
}

open class KVStorageException(
    message: String? = null,
    cause: Throwable? = null
) : RuntimeException(message, cause)