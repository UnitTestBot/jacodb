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
import org.jacodb.testing.BaseTest
import org.jacodb.testing.WithDB
import org.jacodb.testing.builders.Hierarchy.HierarchyInterface
import org.jacodb.testing.builders.Interfaces.Interface
import org.jacodb.testing.builders.Simple
import org.jooq.DSLContext
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledOnJre
import org.junit.jupiter.api.condition.JRE
import org.utbot.jacodb.api.JcMethod
import org.utbot.jacodb.api.ext.findClass
import org.utbot.jacodb.impl.features.Builders
import org.utbot.jacodb.impl.features.InMemoryHierarchy
import org.utbot.jacodb.impl.features.buildersExtension
import javax.xml.parsers.DocumentBuilderFactory

class BuildersTest : BaseTest() {

    companion object : WithDB(InMemoryHierarchy, Builders)

    private val ext = runBlocking {
        cp.buildersExtension()
    }

    @Test
    fun `simple find builders`() {
        val builders = ext.findBuildMethods(cp.findClass<Simple>()).toList()
        assertEquals(1, builders.size)
        assertEquals("build", builders.first().name)
    }

    @Test
    fun `java package is not indexed`() {
        val builders = ext.findBuildMethods(cp.findClass<ArrayList<*>>())
        assertFalse(builders.iterator().hasNext())
    }

    @Test
    fun `method parameters is took into account`() {
        val builders = ext.findBuildMethods(cp.findClass<Interface>()).toList()
        assertEquals(1, builders.size)
        assertEquals("build1", builders.first().name)
    }

    @Test
    @DisabledOnJre(JRE.JAVA_8)
    fun `works for DocumentBuilderFactory`() {
        val builders = ext.findBuildMethods(cp.findClass<DocumentBuilderFactory>()).toList()
        val expected = builders.map { it.loggable }
        assertTrue(expected.contains("javax.xml.parsers.DocumentBuilderFactory#newDefaultInstance"))
        assertTrue(expected.contains("javax.xml.parsers.DocumentBuilderFactory#newInstance"))
    }

    @Test
    fun `works for DocumentBuilderFactory for java 8`() {
        val builders = ext.findBuildMethods(cp.findClass<DocumentBuilderFactory>()).toList()
        val expected = builders.map { it.loggable }
        assertTrue(expected.contains("javax.xml.parsers.DocumentBuilderFactory#newInstance"))
    }

    @Test
    fun `works for jooq`() {
        val builders = ext.findBuildMethods(cp.findClass<DSLContext>()).toList()
        assertEquals("org.jooq.impl.DSL#using", builders.first().loggable)
    }

    @Test
    fun `works for methods returns subclasses`() {
        val builders = ext.findBuildMethods(cp.findClass<HierarchyInterface>(), includeSubclasses = true).toList()
        assertEquals(1, builders.size)
        assertEquals("org.jacodb.testing.builders.Hierarchy#build", builders.first().loggable)
    }

    private val JcMethod.loggable get() = enclosingClass.name + "#" + name
}