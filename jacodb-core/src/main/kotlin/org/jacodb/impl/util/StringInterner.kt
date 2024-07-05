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

package org.jacodb.impl.util


val String.interned: String get() = StringInterner.intern(this)

object StringInterner {

    private val internerSize = Integer.getInteger("org.jacodb.impl.util.internerSize", 131072).also {
        if ((it and (it - 1)) != 0) {
            throw IllegalArgumentException("Interner size must be a power of 2")
        }
    }
    private val mask = internerSize - 1
    private val strings = Array(internerSize) { "" }

    fun intern(s: String): String {
        val i = s.hashCode().let { h -> h xor (h shr 16) } and mask
        val interned = strings[i]
        if (s == interned) {
            return interned
        }
        return s.also { strings[i] = s }
    }
}