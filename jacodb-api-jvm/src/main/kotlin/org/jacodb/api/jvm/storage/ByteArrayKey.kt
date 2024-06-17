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

package org.jacodb.api.jvm.storage

import kotlin.math.min

class ByteArrayKey(val bytes: ByteArray) : Comparable<ByteArrayKey> {

    override fun compareTo(other: ByteArrayKey): Int {
        val a = bytes
        val b = other.bytes
        if (a === b) return 0
        for (i in 0 until min(a.size, b.size)) {
            val cmp = (a[i].toInt() and 0xff).compareTo(b[i].toInt() and 0xff)
            if (cmp != 0) return cmp
        }
        return a.size - b.size
    }

    override fun equals(other: Any?): Boolean =
        this === other || other is ByteArrayKey && bytes contentEquals other.bytes

    override fun hashCode(): Int = bytes.contentHashCode()
    override fun toString(): String = bytes.contentToString()
}