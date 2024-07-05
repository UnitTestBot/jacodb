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

package org.jacodb.impl.storage.ers.kv

import org.jacodb.api.jvm.storage.ByteArrayKey
import org.jacodb.api.jvm.storage.kv.Cursor

internal class TypeIdWithName(val typeId: Int, val name: String) {

    override fun equals(other: Any?): Boolean {
        return other is TypeIdWithName && typeId == other.typeId && name == other.name
    }

    override fun hashCode(): Int {
        return name.hashCode() * 31 + typeId
    }

    override fun toString(): String {
        return "TypeIdWithName(typeId=$typeId, name='$name')"
    }
}

internal infix fun Int.with(name: String) = TypeIdWithName(this, name)

internal fun Cursor.asReversedIterable(maxKey: ByteArray): Iterable<Pair<ByteArray, ByteArray>> {
    val maxKeyComparable = ByteArrayKey(maxKey)
    do {
        if (!movePrev()) return emptyList()
    } while (ByteArrayKey(key) > maxKeyComparable)
    return Iterable {
        object : Iterator<Pair<ByteArray, ByteArray>> {

            private var skipMove = true

            override fun hasNext() = (skipMove || movePrev()).also { skipMove = false }
            override fun next() = key to value
        }
    }
}