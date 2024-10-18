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

import org.jacodb.api.storage.kv.NamedMap
import org.jacodb.api.storage.kv.Transaction
import org.jacodb.api.storage.kv.TransactionDecorator
import org.lmdbjava.Dbi
import java.nio.ByteBuffer

internal class LmdbNamedMap(
    val db: Dbi<ByteBuffer>,
    val duplicates: Boolean,
    override val name: String
) : NamedMap {

    override fun size(txn: Transaction): Long {
        var t = txn
        while (t is TransactionDecorator) {
            t = t.decorated
        }
        return db.stat((t as LmdbTransaction).lmdbTxn).entries
    }
}