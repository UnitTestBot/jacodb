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

package org.jacodb.testing.storage.ers

import org.jacodb.impl.storage.ers.ram.CompactPersistentLongSet
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CompactPersistentLongSetTest {

    @Test
    fun `add remove`() {
        var set = CompactPersistentLongSet()
        set = set.add(1L)
        set = set.add(2L)
        set = set.add(3L)
        assertEquals(set.size, 3)
        assertEquals(setOf(1L, 2L, 3L), set.toSet())
        set = set.remove(2L)
        assertEquals(set.size, 2)
        assertEquals(setOf(1L, 3L), set.toSet())
        set = set.remove(3L)
        assertEquals(set.size, 1)
        assertEquals(setOf(1L), set.toSet())
    }
}