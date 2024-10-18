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

package org.jacodb.api.storage.kv

import java.io.Closeable

interface Cursor : Closeable {

    val txn: Transaction

    fun moveNext(): Boolean

    fun movePrev(): Boolean

    val key: ByteArray

    val value: ByteArray
}

object DummyCursor : Cursor {

    override val txn: Transaction get() = throw NotImplementedError()

    override fun moveNext(): Boolean = false

    override fun movePrev(): Boolean = false

    override val key: ByteArray get() = throw NotImplementedError()

    override val value: ByteArray get() = throw NotImplementedError()

    override fun close() {}
}

class EmptyCursor(
    override val txn: Transaction,
) : Cursor {
    override fun moveNext(): Boolean = false
    override fun movePrev(): Boolean = false
    override val key: ByteArray get() = throw NoSuchElementException()
    override val value: ByteArray get() = throw NoSuchElementException()
    override fun close() {}
}

class SingleElementCursor(
    override val txn: Transaction,
    private var _key: ByteArray?,
    private var _value: ByteArray?,
) : Cursor {
    override val key: ByteArray
        get() = _key ?: throw NoSuchElementException()

    override val value: ByteArray
        get() = _value ?: throw NoSuchElementException()

    override fun moveNext(): Boolean {
        _key = null
        _value = null
        return false
    }

    override fun movePrev(): Boolean = moveNext()

    override fun close() {}
}

fun Cursor.forEach(action: (ByteArray, ByteArray) -> Unit) {
    while (moveNext()) {
        action(key, value)
    }
}

fun Cursor.asIterable(): Iterable<Pair<ByteArray, ByteArray>> {
    return Iterable {
        object : Iterator<Pair<ByteArray, ByteArray>> {
            override fun hasNext() = moveNext()
            override fun next() = key to value
        }
    }
}

fun Cursor.forEachWithKey(key: ByteArray, action: (ByteArray, ByteArray) -> Unit) {
    while (moveNext()) {
        val cursorKey = this.key
        if (!cursorKey.contentEquals(key)) {
            break
        }
        action(cursorKey, value)
    }
}

fun Cursor.asIterableWithKey(key: ByteArray): Iterable<Pair<ByteArray, ByteArray>> {
    return Iterable {
        object : Iterator<Pair<ByteArray, ByteArray>> {
            override fun hasNext() = moveNext() && key.contentEquals(this@asIterableWithKey.key)
            override fun next() = key to value
        }
    }
}

fun Cursor.forEachReversed(action: (ByteArray, ByteArray) -> Unit) {
    while (movePrev()) {
        action(key, value)
    }
}

fun Cursor.withFirstMoveSkipped(): Cursor = SkipFirstMoveCursor(this)

fun Cursor.withFirstMovePrevSkipped(): Cursor = SkipFirstMovePrevCursor(this)

abstract class AbstractCursorDecorator(open val decorated: Cursor) : Cursor by decorated

private class SkipFirstMoveCursor(decorated: Cursor) : AbstractCursorDecorator(decorated) {

    private var moveSkipped = false

    override fun moveNext(): Boolean {
        if (moveSkipped) {
            return decorated.moveNext()
        }
        moveSkipped = true
        return true
    }

    override fun movePrev(): Boolean {
        if (moveSkipped) {
            return decorated.movePrev()
        }
        moveSkipped = true
        return true
    }
}

private class SkipFirstMovePrevCursor(decorated: Cursor) : AbstractCursorDecorator(decorated) {

    private var moveSkipped = false

    override fun movePrev(): Boolean {
        if (moveSkipped) {
            return decorated.movePrev()
        }
        moveSkipped = true
        return true
    }
}
