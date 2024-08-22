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

import org.jacodb.api.jvm.storage.kv.Cursor
import org.jacodb.api.jvm.storage.kv.NamedMap
import org.jacodb.api.jvm.storage.kv.Transaction
import org.jacodb.api.jvm.storage.kv.withFirstMovePrevSkipped
import org.jacodb.api.jvm.storage.kv.withFirstMoveSkipped

internal class XodusTransaction(
    override val storage: XodusKeyValueStorage,
    internal val xodusTxn: jetbrains.exodus.env.Transaction
) : Transaction {

    override val isReadonly: Boolean get() = xodusTxn.isReadonly

    override val isFinished: Boolean get() = xodusTxn.isFinished

    override fun getNamedMap(name: String, create: Boolean): NamedMap? {
        return storage.getMap(xodusTxn, name, create)?.let { XodusNamedMap(it) }
    }

    override fun getMapNames(): Set<String> = storage.getMapNames(xodusTxn)

    override fun get(map: NamedMap, key: ByteArray): ByteArray? {
        map as XodusNamedMap
        return map.store.get(xodusTxn, key.asByteIterable)?.asByteArray
    }

    override fun put(map: NamedMap, key: ByteArray, value: ByteArray): Boolean {
        map as XodusNamedMap
        return map.store.put(xodusTxn, key.asByteIterable, value.asByteIterable)
    }

    override fun delete(map: NamedMap, key: ByteArray): Boolean {
        map as XodusNamedMap
        return map.store.delete(xodusTxn, key.asByteIterable)
    }

    override fun delete(map: NamedMap, key: ByteArray, value: ByteArray): Boolean {
        map as XodusNamedMap
        map.store.openCursor(xodusTxn).use { cursor ->
            return cursor.getSearchBoth(key.asByteIterable, value.asByteIterable).also { found ->
                if (found) {
                    cursor.deleteCurrent()
                }
            }
        }
    }

    override fun navigateTo(map: NamedMap, key: ByteArray?): Cursor {
        map as XodusNamedMap
        val cursor = map.store.openCursor(xodusTxn)
        val result = XodusCursor(this, cursor)
        if (key == null) {
            return result
        }
        val navigatedValue = cursor.getSearchKeyRange(key.asByteIterable)
        return if (navigatedValue == null) {
            result.apply { movePrev() }.withFirstMovePrevSkipped()
        } else {
            result.withFirstMoveSkipped()
        }
    }

    override fun commit() = xodusTxn.commit()

    override fun abort() {
        xodusTxn.abort()
    }
}