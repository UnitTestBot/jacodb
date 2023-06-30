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

package org.jacodb.typesolver.table

import kotlinx.coroutines.runBlocking
import org.jacodb.api.JcClasspath
import org.jacodb.impl.features.HierarchyExtensionImpl
import org.jacodb.impl.features.hierarchyExt
import org.jacodb.impl.jacodb
import org.jacodb.testing.allJars
import org.junit.jupiter.api.RepeatedTest

class TestHierarchy {
    @RepeatedTest(20)
    fun test() {
        val jcClasspath: JcClasspath
        val hierarchy: HierarchyExtensionImpl

        runBlocking {
            val db = jacodb {
                useProcessJavaRuntime()
                loadByteCode(allJars)
            }
            jcClasspath = db.classpath(allJars)

            hierarchy = jcClasspath.hierarchyExt()
        }

        val setSubclasses = hierarchy.findSubClassesIncluding("java.util.Set", allHierarchy = true).toSet()
        val listSubclasses = hierarchy.findSubClassesIncluding("java.util.List", allHierarchy = true).toSet()

        val intersect = setSubclasses.filter { it in listSubclasses }

        println(intersect.size)

        jcClasspath.db.close()
    }
}
