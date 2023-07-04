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
import org.jacodb.api.ext.HierarchyExtension
import org.jacodb.impl.features.duplicatedClasses
import org.jacodb.impl.features.hierarchyExt
import org.jacodb.testing.tests.DatabaseEnvTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith


@ExtendWith(CleanDB::class)
class ClassesTest : DatabaseEnvTest() {

    companion object : WithDB()

    override val cp: JcClasspath = runBlocking { db.classpath(allClasspath) }

    override val hierarchyExt: HierarchyExtension
        get() = runBlocking { cp.hierarchyExt() }

    @Test
    fun `diagnostics should work`() {
        val duplicates = runBlocking { cp.duplicatedClasses() }
        println(duplicates.entries.joinToString("\n") { it.key + " found " + it.value + " times"})
        assertTrue(duplicates.isNotEmpty())
        assertTrue(duplicates.values.all { it > 1 })
        duplicates.entries.forEach { (name, count) ->
            val classes = cp.findClasses(name)
            assertEquals(count, classes.size, "Expected count for $name is $count but was ${classes.size}")
        }
    }

}

