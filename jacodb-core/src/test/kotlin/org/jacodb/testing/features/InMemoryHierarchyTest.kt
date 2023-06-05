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

package org.jacodb.testing.features

import kotlinx.coroutines.runBlocking
import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcMethod
import org.jacodb.api.ext.findClass
import org.jacodb.impl.features.InMemoryHierarchy
import org.jacodb.impl.features.findSubclassesInMemory
import org.jacodb.impl.features.hierarchyExt
import org.jacodb.impl.storage.jooq.tables.references.CLASSES
import org.jacodb.testing.BaseTest
import org.jacodb.testing.WithDB
import org.jacodb.testing.WithRestoredDB
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.w3c.dom.Document
import java.util.*
import java.util.concurrent.ConcurrentHashMap

abstract class BaseInMemoryHierarchyTest : BaseTest() {

    protected val ext = runBlocking { cp.hierarchyExt() }
    open val isInMemory = true

    @Test
    fun `find subclasses for class`() {
        with(findSubClasses<AbstractMap<*, *>>(allHierarchy = true).toList()) {
            assertTrue(size > 10) {
                "expected more then 10 but got only: ${joinToString { it.name }}"
            }

            assertNotNull(firstOrNull { it.name == EnumMap::class.java.name })
            assertNotNull(firstOrNull { it.name == HashMap::class.java.name })
            assertNotNull(firstOrNull { it.name == WeakHashMap::class.java.name })
            assertNotNull(firstOrNull { it.name == TreeMap::class.java.name })
            assertNotNull(firstOrNull { it.name == ConcurrentHashMap::class.java.name })
        }
    }

    @Test
    fun `find subclasses for interface`() {
        with(findSubClasses<Document>().toList()) {
            assertTrue(isNotEmpty(), "expect not empty result")
        }
    }

    @Test
    fun `find huge number of subclasses`() {
        with(findSubClasses<Runnable>().toList()) {
            assertTrue(size > 10, "expect more then 10 but got $size")
        }
    }

    @Test
    fun `find huge number of method overrides`() {
        val jcClazz = cp.findClass<Runnable>()
        with(findMethodOverrides(jcClazz.declaredMethods.first()).toList()) {
            println("Found: $size")
            assertTrue(size > 10)
        }
    }

    @Test
    fun `find regular method overrides`() {
        val jcClazz = cp.findClass<Document>()
        with(findMethodOverrides(jcClazz.declaredMethods.first()).toList()) {
            assertTrue(size >= 4)
        }
    }


    @Test
    fun `find subclasses of Any`() {
        val numberOfClasses = cp.db.persistence.read { it.fetchCount(CLASSES) }
        assertEquals(numberOfClasses - 1, findSubClasses<Any>(allHierarchy = true).count())
    }

    @Test
    fun `find subclasses of Comparable`() {
        val count = findSubClasses<Comparable<*>>(allHierarchy = true).count()
        assertTrue(count > 100, "expected more then 100 but got $count")
    }


    @Test
    fun `find direct subclasses of Comparable`() {
        val count = findSubClasses<Comparable<*>>(allHierarchy = true).count()
        assertTrue(count > 100, "expected more then 100 but got $count")
    }

    @Test
    fun `find direct subclasses of Any`() {
        val count = findSubClasses<Comparable<*>>(allHierarchy = true).count()
        val subClasses = findSubClasses<Any>(allHierarchy = false).count()
        println(subClasses)
        assertTrue(subClasses > count * 0.75, "expected more then ${count * 0.75} classes")
    }

    private inline fun <reified T> findSubClasses(allHierarchy: Boolean = false): Sequence<JcClassOrInterface> =
        runBlocking {
            when {
                isInMemory -> cp.findSubclassesInMemory(T::class.java.name, allHierarchy, true)
                else -> ext.findSubClasses(T::class.java.name, allHierarchy)
            }
        }

    private fun findMethodOverrides(method: JcMethod) = ext.findOverrides(method)

}

class InMemoryHierarchyTest : BaseInMemoryHierarchyTest() {
    companion object : WithDB(InMemoryHierarchy)
}

class RegularHierarchyTest : BaseInMemoryHierarchyTest() {
    companion object : WithDB()

    override val isInMemory: Boolean
        get() = false
}

class RestoredInMemoryHierarchyTest : BaseInMemoryHierarchyTest() {
    companion object : WithRestoredDB(InMemoryHierarchy)
}