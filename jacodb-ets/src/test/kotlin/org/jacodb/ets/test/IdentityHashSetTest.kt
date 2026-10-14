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
package org.jacodb.ets.test

import org.jacodb.ets.model.EtsLocal
import org.jacodb.ets.utils.IdentityHashSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

class IdentityHashSetTest {

    @Test
    fun `membership and removal distinguish equal instances`() {
        val first = EtsLocal(name = "x")
        val equalButDistinct = EtsLocal(name = "x")
        val set = IdentityHashSet<EtsLocal>()

        set.add(first)

        assertNotSame(first, equalButDistinct)
        assertTrue(first in set)
        assertFalse(equalButDistinct in set)
        assertFalse(set.remove(equalButDistinct))
        assertEquals(expected = 1, actual = set.size)
        assertTrue(set.remove(first))
        assertTrue(set.isEmpty())
    }

    @Test
    fun `bulk removal and retention use identity including self collections`() {
        val first = EtsLocal(name = "x")
        val equalButDistinct = EtsLocal(name = "x")
        val other = EtsLocal(name = "y")
        val set = IdentityHashSet<EtsLocal>()

        set.addAll(listOf(first, equalButDistinct, other))

        assertTrue(set.removeAll(listOf(equalButDistinct)))
        assertTrue(first in set)
        assertFalse(equalButDistinct in set)
        assertTrue(other in set)

        assertTrue(set.retainAll(listOf(first)))
        assertEquals(expected = 1, actual = set.size)
        assertTrue(first in set)

        assertFalse(set.retainAll(set))
        assertTrue(set.retainAll(listOf(equalButDistinct)))
        assertTrue(set.isEmpty())

        set.add(first)

        assertTrue(set.removeAll(set))
        assertTrue(set.isEmpty())
    }

}
