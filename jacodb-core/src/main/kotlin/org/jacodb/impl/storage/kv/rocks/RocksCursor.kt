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
import org.jacodb.api.jvm.storage.kv.EmptyCursor
import org.jacodb.api.jvm.storage.kv.withFirstMovePrevSkipped
import org.jacodb.api.jvm.storage.kv.withFirstMoveSkipped
import org.rocksdb.RocksIterator

internal fun seekNoDuplicateKeyCursor(txn: RocksTransaction, iterator: RocksIterator, key: ByteArray): Cursor {
    val result = RocksCursor(txn, iterator)
    iterator.seek(key)
    if (iterator.isValid) {
        return result.withFirstMoveSkipped()
    }
    iterator.seekToLast()
    return if (iterator.isValid) result.withFirstMovePrevSkipped() else EmptyCursor(txn)
}

internal fun seekDuplicateKeyCursor(txn: RocksTransaction, iterator: RocksIterator, key: ByteArray): Cursor {
    val result = RocksCursor(txn, iterator).decodeDuplicateKeys()
    iterator.seek(ByteArrayPairUtils.makePair(key, ByteArray(0)))
    if (iterator.isValid) {
        return result.withFirstMoveSkipped()
    }
    iterator.seekToLast()
    return if (iterator.isValid) result.withFirstMovePrevSkipped() else EmptyCursor(txn)

}

internal fun seekFirstOrLastCursor(txn: RocksTransaction, iterator: RocksIterator): Cursor {
    return FirstSeekCursor(RocksCursor(txn, iterator))
}

private class RocksCursor(
    override val txn: RocksTransaction,
    val iterator: RocksIterator
) : Cursor {

    override fun moveNext(): Boolean {
        iterator.next()
        val valid = iterator.isValid
        if (!valid) close()
        return valid
    }

    override fun movePrev(): Boolean {
        iterator.prev()
        val valid = iterator.isValid
        if (!valid) close()
        return valid
    }

    override val key: ByteArray get() = iterator.key()

    override val value: ByteArray get() = iterator.value()

    override fun close() = iterator.close()
}

internal fun Cursor.decodeDuplicateKeys(): Cursor = DecodingDuplicateKeysCursorDecorator(this)

private class DecodingDuplicateKeysCursorDecorator(
    private val decorated: Cursor
) : Cursor by decorated {
    override val key: ByteArray
        get() = ByteArrayPairUtils.getFirst(decorated.key)

    override val value: ByteArray
        get() = ByteArrayPairUtils.getSecond(decorated.key)
}

private class FirstSeekCursor(private val decorated: RocksCursor) : Cursor by decorated {

    private var firstMove = true

    override fun moveNext(): Boolean {
        if (firstMove) {
            firstMove = false
            return decorated.iterator.let {
                it.seekToFirst()
                it.isValid
            }
        }
        return decorated.moveNext()
    }

    override fun movePrev(): Boolean {
        if (firstMove) {
            firstMove = false
            return decorated.iterator.let {
                it.seekToLast()
                it.isValid
            }
        }
        return decorated.movePrev()
    }
}