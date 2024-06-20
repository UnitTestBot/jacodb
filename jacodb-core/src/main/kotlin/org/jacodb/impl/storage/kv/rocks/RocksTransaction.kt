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

package org.jacodb.impl.storage.kv.rocks

import org.jacodb.api.jvm.storage.kv.Cursor
import org.jacodb.api.jvm.storage.kv.NamedMap
import org.jacodb.api.jvm.storage.kv.Transaction
import org.jacodb.api.jvm.storage.kv.withFinishedState
import org.rocksdb.ColumnFamilyHandle
import org.rocksdb.ReadOptions
import org.rocksdb.RocksDBException
import org.rocksdb.RocksIterator

internal interface RocksTransaction : Transaction {
    override val storage: RocksKeyValueStorage

    /// region methods that do NOT encode duplicate keys
    fun rawGet(columnFamilyHandle: ColumnFamilyHandle, key: ByteArray): ByteArray?
    fun rawPut(columnFamilyHandle: ColumnFamilyHandle, key: ByteArray, value: ByteArray)
    fun rawDelete(columnFamilyHandle: ColumnFamilyHandle, key: ByteArray)
    fun rawGetIterator(columnFamilyHandle: ColumnFamilyHandle): RocksIterator
    /// endregion
}

internal class RocksTransactionImpl private constructor(
    override val storage: RocksKeyValueStorage,
    private val rocksTxn: org.rocksdb.Transaction,
    override val isReadonly: Boolean
) : RocksTransaction {

    companion object {
        fun create(storage: RocksKeyValueStorage, rocksTxn: org.rocksdb.Transaction, isReadonly: Boolean): Transaction =
            RocksTransactionImpl(storage, rocksTxn, isReadonly).withFinishedState()
    }

    private val readOptions = ReadOptions().setSnapshot(rocksTxn.snapshot)

    // implemented using `withFinishedState()` (see `create()` in `companion object`)
    override val isFinished: Boolean get() = false

    override fun getNamedMap(name: String): NamedMap = when {
        isReadonly -> storage.getNamedMapOrNull(name) ?: LazyRocksNamedMap(name, storage)
        else -> storage.getOrCreateNamedMap(name)
    }

    override fun get(map: NamedMap, key: ByteArray): ByteArray? = (map as RocksNamedMap).get(this, key)
    override fun put(map: NamedMap, key: ByteArray, value: ByteArray) = (map as RocksNamedMap).put(this, key, value)
    override fun delete(map: NamedMap, key: ByteArray): Boolean = (map as RocksNamedMap).delete(this, key)
    override fun delete(map: NamedMap, key: ByteArray, value: ByteArray): Boolean =
        (map as RocksNamedMap).delete(this, key, value)

    override fun navigateTo(map: NamedMap, key: ByteArray?): Cursor = (map as RocksNamedMap).navigateTo(this, key)

    override fun commit(): Boolean {
        return try {
            rocksTxn.commit()
            true
        } catch (e: RocksDBException) {
            false
        }
    }

    override fun abort() {
        rocksTxn.rollback()
    }

    override fun close() {
        super.close()
        readOptions.close()
        rocksTxn.close()
    }

    override fun rawGet(columnFamilyHandle: ColumnFamilyHandle, key: ByteArray): ByteArray? =
        rocksTxn.get(readOptions, columnFamilyHandle, key)

    override fun rawPut(columnFamilyHandle: ColumnFamilyHandle, key: ByteArray, value: ByteArray) =
        rocksTxn.put(columnFamilyHandle, key, value)

    override fun rawDelete(columnFamilyHandle: ColumnFamilyHandle, key: ByteArray) =
        rocksTxn.delete(columnFamilyHandle, key)

    override fun rawGetIterator(columnFamilyHandle: ColumnFamilyHandle): RocksIterator =
        rocksTxn.getIterator(readOptions, columnFamilyHandle)
}
