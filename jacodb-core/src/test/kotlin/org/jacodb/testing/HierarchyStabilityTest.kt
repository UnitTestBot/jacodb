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
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcFeature
import org.jacodb.api.ext.HierarchyExtension
import org.jacodb.impl.features.InMemoryHierarchy
import org.jacodb.impl.features.hierarchyExt
import org.jacodb.impl.jacodb
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test

class HierarchyStabilityTest {


    companion object {
        private var listInheritorsCount: Int = 0
        private var setInheritorsCount: Int = 0

        @BeforeAll
        @JvmStatic
        fun setup() {
            val (sets, lists) = run()
            listInheritorsCount = lists
            setInheritorsCount = sets
        }

        private fun run(vararg features: JcFeature<*,*>): Pair<Int, Int> {
            val jcClasspath: JcClasspath
            val hierarchy: HierarchyExtension

            runBlocking {
                val db = jacodb {
                    useProcessJavaRuntime()
                    loadByteCode(allJars)
                    installFeatures(*features)
                }
                jcClasspath = db.classpath(allJars)

                hierarchy = jcClasspath.hierarchyExt()
            }

            val setSubclasses = hierarchy.findSubClasses("java.util.Set",
                allHierarchy = true, includeOwn = true).toSet()
            val listSubclasses = hierarchy.findSubClasses("java.util.List",
                allHierarchy = true, includeOwn = true).toSet()

            jcClasspath.db.close()
            return setSubclasses.size to listSubclasses.size
        }

    }

    @RepeatedTest(3)
    fun `should be stable`() {
        val (sets, lists) = run()
        assertEquals(listInheritorsCount, lists)
        assertEquals(setInheritorsCount, sets)
    }

    @Test
    fun `should ok with in-memory feature`() {
        val (sets, lists) = run(InMemoryHierarchy)
        assertEquals(listInheritorsCount, lists)
        assertEquals(setInheritorsCount, sets)
    }

}