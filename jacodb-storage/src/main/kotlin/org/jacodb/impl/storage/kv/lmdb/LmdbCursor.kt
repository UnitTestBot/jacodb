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

import org.jacodb.api.storage.kv.Cursor
import org.jacodb.api.storage.kv.Transaction
import java.nio.ByteBuffer

class LmdbCursor(
    override val txn: Transaction,
    private val cursor: org.lmdbjava.Cursor<ByteBuffer>
) : Cursor {

    override fun moveNext(): Boolean = cursor.next()

    override fun movePrev(): Boolean = cursor.prev()

    override val key: ByteArray get() = cursor.key().asArray

    override val value: ByteArray
        get() = cursor.`val`().asArray

    override fun close() {
        cursor.close()
    }
}