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

package org.jacodb.testing

import kotlinx.coroutines.runBlocking
import org.jacodb.impl.features.hierarchyExt
import org.jacodb.impl.jacodb
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class HierarchyStabilityTest {

    companion object {
        private var listInheritorsCount: Int = 0
        private var setInheritorsCount: Int = 0

        @BeforeAll
        @JvmStatic
        fun setup() {
            val (sets, lists) = runBlocking { run(global = true) }
            listInheritorsCount = lists
            setInheritorsCount = sets
        }

        private suspend fun run(global: Boolean): Pair<Int, Int> {

            val db = when {
                global -> globalDb
                else -> jacodb {
                    useProcessJavaRuntime()
                    loadByteCode(allJars)
                    installFeatures()
                }
            }
            val jcClasspath = db.classpath(allJars)
            val hierarchy = jcClasspath.hierarchyExt()

            val setSubclasses = hierarchy.findSubClasses(
                "java.util.Set",
                allHierarchy = true, includeOwn = true
            ).toSet()
            val listSubclasses = hierarchy.findSubClasses(
                "java.util.List",
                allHierarchy = true, includeOwn = true
            ).toSet()

            if (!global) {
                jcClasspath.db.close()
            }
            return setSubclasses.size to listSubclasses.size
        }

    }

    @Test
    fun `should be ok`() {
        val (sets, lists) = runBlocking { run(global = false) }
        assertEquals(listInheritorsCount, lists)
        assertEquals(setInheritorsCount, sets)
    }

    @Test
    fun `should ok with in-memory feature`() {
        val (sets, lists) = runBlocking { run(global = true) }
        assertEquals(listInheritorsCount, lists)
        assertEquals(setInheritorsCount, sets)
    }

}