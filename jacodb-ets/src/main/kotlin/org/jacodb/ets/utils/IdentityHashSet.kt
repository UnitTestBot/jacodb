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

    override fun iterator(): MutableIterator<T> {
        return map.keys.iterator()
    }
}
