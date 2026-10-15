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

package org.jacodb.ets.utils

import java.util.IdentityHashMap

class IdentityHashSet<T>(
    private val map: IdentityHashMap<T, Unit> = IdentityHashMap(),
) : AbstractMutableSet<T>() {

    override val size: Int
        get() = map.size

    override fun add(element: T): Boolean {
        return map.put(element, Unit) == null
    }

    override fun contains(element: T): Boolean {
        return map.containsKey(element)
    }

    override fun remove(element: T): Boolean {
        return map.remove(element) != null
    }

    override fun removeAll(elements: Collection<T>): Boolean {
        val elementsByIdentity = IdentityHashMap<T, Unit>()
        elements.forEach { elementsByIdentity[it] = Unit }

        return removeKeysWhere { elementsByIdentity.containsKey(it) }
    }

    override fun retainAll(elements: Collection<T>): Boolean {
        val elementsByIdentity = IdentityHashMap<T, Unit>()
        elements.forEach { elementsByIdentity[it] = Unit }

        return removeKeysWhere { !elementsByIdentity.containsKey(it) }
    }

    override fun iterator(): MutableIterator<T> {
        return map.keys.iterator()
    }

    private fun removeKeysWhere(predicate: (T) -> Boolean): Boolean {
        val iterator = map.keys.iterator()
        var changed = false

        while (iterator.hasNext()) {
            if (predicate(iterator.next())) {
                iterator.remove()
                changed = true
            }
        }

        return changed
    }
}
