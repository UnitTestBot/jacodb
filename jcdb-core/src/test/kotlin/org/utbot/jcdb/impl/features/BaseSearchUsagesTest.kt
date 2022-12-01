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

package org.utbot.jcdb.impl.features

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.utbot.jcdb.api.FieldUsageMode
import org.utbot.jcdb.api.ext.findClass
import org.utbot.jcdb.impl.BaseTest
import org.utbot.jcdb.impl.WithDB
import org.utbot.jcdb.impl.usages.fields.FieldA
import org.utbot.jcdb.impl.usages.fields.FieldB
import org.utbot.jcdb.impl.usages.methods.MethodA
import kotlin.system.measureTimeMillis

abstract class BaseSearchUsagesTest : BaseTest() {

    @Test
    fun `classes read fields`() {
        val usages = fieldsUsages<FieldA>(FieldUsageMode.READ)
        assertEquals(
            sortedMapOf(
                "a" to setOf(
                    "org.utbot.jcdb.impl.usages.fields.FieldA#<init>",
                    "org.utbot.jcdb.impl.usages.fields.FieldA#isPositive",
                    "org.utbot.jcdb.impl.usages.fields.FieldA#useCPrivate",
                    "org.utbot.jcdb.impl.usages.fields.FieldAImpl#hello"
                ),
                "b" to setOf(
                    "org.utbot.jcdb.impl.usages.fields.FieldA#isPositive",
                    "org.utbot.jcdb.impl.usages.fields.FieldA#useA",
                ),
                "fieldB" to setOf(
                    "org.utbot.jcdb.impl.usages.fields.FieldA#useCPrivate",
                )
            ),
            usages
        )
    }

    @Test
    fun `classes write fields`() {
        val usages = fieldsUsages<FieldA>()
        assertEquals(
            sortedMapOf(
                "a" to setOf(
                    "org.utbot.jcdb.impl.usages.fields.FieldA#<init>",
                    "org.utbot.jcdb.impl.usages.fields.FieldA#useA",
                ),
                "b" to setOf(
                    "org.utbot.jcdb.impl.usages.fields.FieldA#<init>"
                ),
                "fieldB" to setOf(
                    "org.utbot.jcdb.impl.usages.fields.FieldA#<init>",
                )
            ),
            usages
        )
    }

    @Test
    fun `classes write fields with rebuild`() {
        val time = measureTimeMillis {
            runBlocking {
                cp.db.rebuildFeatures()
            }
        }
        println("Features rebuild in ${time}ms")
        val usages = fieldsUsages<FieldA>()
        assertEquals(
            sortedMapOf(
                "a" to setOf(
                    "org.utbot.jcdb.impl.usages.fields.FieldA#<init>",
                    "org.utbot.jcdb.impl.usages.fields.FieldA#useA",
                ),
                "b" to setOf(
                    "org.utbot.jcdb.impl.usages.fields.FieldA#<init>"
                ),
                "fieldB" to setOf(
                    "org.utbot.jcdb.impl.usages.fields.FieldA#<init>",
                )
            ),
            usages
        )
    }

    @Test
    fun `classes write fields coupled`() {
        val usages = fieldsUsages<FieldB>()
        assertEquals(
            sortedMapOf(
                "c" to setOf(
                    "org.utbot.jcdb.impl.usages.fields.FakeFieldA#useCPrivate",
                    "org.utbot.jcdb.impl.usages.fields.FieldA#useCPrivate",
                    "org.utbot.jcdb.impl.usages.fields.FieldB#<init>",
                    "org.utbot.jcdb.impl.usages.fields.FieldB#useCPrivate",
                )
            ),
            usages
        )
    }

    @Test
    fun `classes methods usages`() {
        val usages = methodsUsages<MethodA>()
        assertEquals(
            sortedMapOf(
                "<init>" to setOf(
                    "org.utbot.jcdb.impl.usages.methods.MethodB#hoho",
                    "org.utbot.jcdb.impl.usages.methods.MethodC#<init>"
                ),
                "hello" to setOf(
                    "org.utbot.jcdb.impl.usages.methods.MethodB#hoho",
                    "org.utbot.jcdb.impl.usages.methods.MethodC#hello",
                )
            ),
            usages
        )
    }

    @Test
    fun `find usages of Runnable#run method`() {
        runBlocking {
            val ext = cp.usagesExtension()
            val runMethod = cp.findClass<Runnable>().declaredMethods.first()
            assertEquals("run", runMethod.name)
            val result = ext.findUsages(runMethod).toList()
            assertTrue(result.size > 100)
        }
    }

    @Test
    fun `find usages of System#out field`() {
        runBlocking {
            val ext = cp.usagesExtension()
            val invokeStaticField = cp.findClass<System>().declaredFields.first { it.name == "out" }
            val result = ext.findUsages(invokeStaticField, FieldUsageMode.READ).toList()
            assertTrue(result.size > 1_000)
        }
    }

    private inline fun <reified T> fieldsUsages(mode: FieldUsageMode = FieldUsageMode.WRITE): Map<String, Set<String>> {
        return runBlocking {
            with(cp.usagesExtension()) {
                val classId = cp.findClass<T>()

                val fields = classId.declaredFields

                fields.associate {
                    it.name to findUsages(it, mode).map { it.enclosingClass.name + "#" + it.name }.toSortedSet()
                }.filterNot { it.value.isEmpty() }.toSortedMap()
            }
        }
    }

    private inline fun <reified T> methodsUsages(): Map<String, Set<String>> {
        return runBlocking {
            with(cp.usagesExtension()) {
                val classId = cp.findClass<T>()
                val methods = classId.declaredMethods

                methods.map {
                    it.name to findUsages(it).map { it.enclosingClass.name + "#" + it.name }.toSortedSet()
                }
                    .toMap()
                    .filterNot { it.value.isEmpty() }
                    .toSortedMap()
            }
        }
    }

}

class InMemoryHierarchySearchUsagesTest : BaseSearchUsagesTest() {
    companion object : WithDB(Usages, InMemoryHierarchy)
}

class SearchUsagesTest : BaseSearchUsagesTest() {
    companion object : WithDB(Usages)
}