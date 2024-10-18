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

package org.jacodb.impl.storage.kv.lmdb

import org.jacodb.api.storage.kv.PluggableKeyValueStorage
import org.jacodb.api.storage.kv.Transaction
import org.jacodb.api.storage.kv.withFinishedState
import org.jacodb.impl.JcLmdbErsSettings
import org.lmdbjava.Dbi
import org.lmdbjava.Dbi.KeyNotFoundException
import org.lmdbjava.DbiFlags
import org.lmdbjava.Env
import org.lmdbjava.EnvFlags
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap

internal class LmdbKeyValueStorage(location: String, settings: JcLmdbErsSettings) : PluggableKeyValueStorage() {

    private val mapNames: MutableSet<String> = ConcurrentHashMap<String, Boolean>().keySet(true)
    private val env = Env.create().apply {
        setMaxDbs(999999)
        setMaxReaders(9999999)
        setMapSize(settings.mapSize)
    }.open(File(location), EnvFlags.MDB_NOTLS).apply {
        dbiNames.forEach {
            mapNames += String(it)
        }
    }

    override fun beginTransaction(): Transaction {
        return LmdbTransaction(this, env.txnWrite()).withFinishedState()
    }

    override fun beginReadonlyTransaction(): Transaction {
        return LmdbTransaction(this, env.txnRead()).withFinishedState()
    }

    override fun close() {
        env.close()
    }

    internal fun getMap(
        lmdbTxn: org.lmdbjava.Txn<ByteBuffer>,
        map: String,
        create: Boolean
    ): Pair<Dbi<ByteBuffer>, Boolean>? {
        val duplicates = isMapWithKeyDuplicates?.invoke(map) == true
        return if (lmdbTxn.isReadOnly || !create) {
            try {
                env.openDbi(lmdbTxn, map.toByteArray(), null, false) to duplicates
            } catch (_: KeyNotFoundException) {
                null
            }
        } else {
            if (duplicates) {
                env.openDbi(lmdbTxn, map.toByteArray(), null, false, DbiFlags.MDB_CREATE, DbiFlags.MDB_DUPSORT)
            } else {
                env.openDbi(lmdbTxn, map.toByteArray(), null, false, DbiFlags.MDB_CREATE)
            }.also { mapNames += map } to duplicates
        }
    }

    internal fun getMapNames(): Set<String> = mapNames.toSortedSet()
}