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
import org.jacodb.api.jvm.storage.kv.Transaction

internal class XodusCursor(
    override val txn: Transaction,
    private val cursor: jetbrains.exodus.env.Cursor
) : Cursor {

    private var isClosed: Boolean = false
    private var cachedKey: ByteArray? = null
    private var cachedValue: ByteArray? = null

    override fun moveNext(): Boolean = cursorChecked.next.also { invalidateCached() }

    override fun movePrev(): Boolean = cursorChecked.prev.also { invalidateCached() }

    override val key: ByteArray get() = cachedKey ?: cursorChecked.key.asByteArray.also { cachedKey = it }

    override val value: ByteArray get() = cachedValue ?: cursorChecked.value.asByteArray.also { cachedValue = it }

    override fun close() {
        if (!isClosed) {
            isClosed = true
            cursor.close()
        }
    }

    internal fun invalidateCached() {
        cachedKey = null
        cachedValue = null
    }

    private val cursorChecked: jetbrains.exodus.env.Cursor
        get() = if (isClosed) throw IllegalStateException("Cursor is already closed") else cursor
}