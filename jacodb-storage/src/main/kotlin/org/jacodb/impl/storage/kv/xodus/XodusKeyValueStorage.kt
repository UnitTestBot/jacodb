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

import jetbrains.exodus.env.Environment
import jetbrains.exodus.env.Environments
import jetbrains.exodus.env.Store
import jetbrains.exodus.env.StoreConfig.WITHOUT_DUPLICATES_WITH_PREFIXING
import jetbrains.exodus.env.StoreConfig.WITH_DUPLICATES_WITH_PREFIXING
import jetbrains.exodus.env.TransactionBase
import org.jacodb.api.storage.kv.PluggableKeyValueStorage
import org.jacodb.api.storage.kv.Transaction

internal class XodusKeyValueStorage(location: String) : PluggableKeyValueStorage() {

    private val env: Environment = Environments.newInstance(location,
        environmentConfig {
            logFileSize = 32768
            logCachePageSize = 65536 * 4
            gcStartIn = 600_000
            useVersion1Format = false // use v2 data format, as we use stores with prefixing, i.e., patricia trees
        }
    )

    override var readonly: Boolean
        get() = env.environmentConfig.envIsReadonly
        set(value) {
            env.environmentConfig.envIsReadonly = value
        }

    override fun beginTransaction(): Transaction =
        XodusTransaction(this, env.beginTransaction().withNoStoreGetCache())

    override fun beginReadonlyTransaction(): Transaction =
        XodusTransaction(this, env.beginReadonlyTransaction().withNoStoreGetCache())

    override fun close() {
        env.close()
    }

    internal fun getMap(
        xodusTxn: jetbrains.exodus.env.Transaction,
        map: String,
        create: Boolean
    ): Store? {
        return if (create || env.storeExists(map, xodusTxn)) {
            val duplicates = isMapWithKeyDuplicates?.invoke(map)
            env.openStore(
                map,
                if (duplicates == true) WITH_DUPLICATES_WITH_PREFIXING else WITHOUT_DUPLICATES_WITH_PREFIXING,
                xodusTxn
            )
        } else {
            null
        }
    }

    internal fun getMapNames(xodusTxn: jetbrains.exodus.env.Transaction): Set<String> =
        env.getAllStoreNames(xodusTxn).toSortedSet()

    private fun jetbrains.exodus.env.Transaction.withNoStoreGetCache(): jetbrains.exodus.env.Transaction {
        this as TransactionBase
        isDisableStoreGetCache = true
        return this
    }
}