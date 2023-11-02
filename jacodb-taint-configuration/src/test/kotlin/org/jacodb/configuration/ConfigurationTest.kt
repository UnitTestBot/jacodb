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

package org.jacodb.configuration

import kotlinx.coroutines.runBlocking
import org.jacodb.api.JcClasspath
import org.jacodb.api.ext.findClass
import org.jacodb.api.ext.methods
import org.jacodb.api.ext.objectType
import org.jacodb.impl.features.classpaths.UnknownClasses
import org.jacodb.impl.features.classpaths.VirtualLocation
import org.jacodb.impl.features.classpaths.virtual.JcVirtualClassImpl
import org.jacodb.impl.features.classpaths.virtual.JcVirtualMethodImpl
import org.jacodb.impl.features.classpaths.virtual.JcVirtualParameter
import org.jacodb.impl.types.TypeNameImpl
import org.jacodb.testing.BaseTest
import org.jacodb.testing.WithDB
import org.jacodb.testing.allClasspath
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ConfigurationTest : BaseTest() {
    companion object : WithDB()

    override val cp: JcClasspath = runBlocking {
        val configPath = "/testJsonConfig.json"
        val testConfig = this::class.java.getResourceAsStream(configPath)
            ?: error("No such resource found: $configPath")
        val configJson = testConfig.bufferedReader().readText()
        val configurationFeature = TaintConfigurationFeature.fromJson(configJson)
        val features = listOf(configurationFeature, UnknownClasses)
        db.classpath(allClasspath, features)
    }

    private val taintFeature = cp.taintConfigurationFeature()

    @Test
    fun testVirtualMethod() {
        val virtualParameter = JcVirtualParameter(0, TypeNameImpl(cp.objectType.typeName))

        val method = JcVirtualMethodImpl(
            name = "setValue",
            returnType = TypeNameImpl(cp.objectType.typeName),
            parameters = listOf(virtualParameter),
            description = ""
        )

        val clazz = JcVirtualClassImpl(
            name = "com.service.model.SimpleRequest",
            initialFields = emptyList(),
            initialMethods = listOf(method)
        )
        clazz.bind(cp, VirtualLocation())

        method.bind(clazz)

        val configs = taintFeature.getConfigForMethod(method)
        val rule = configs.single() as TaintPassThrough

        assertEquals(ConstantTrue, rule.condition)
        assertEquals(2, rule.actionsAfter.size)
    }

    @Test
    fun testSinkMethod() {
        val method = cp.findClass<java.util.Properties>().methods.first { it.name == "store" }
        val rules = taintFeature.getConfigForMethod(method)

        assertTrue(rules.singleOrNull() != null)
    }

    @Test
    fun testSourceMethod() {
        val method = cp.findClass<System>().methods.first { it.name == "getProperty" }
        val rules = taintFeature.getConfigForMethod(method)

        assertTrue(rules.singleOrNull() != null)
    }

    @Test
    fun testCleanerMethod() {
        val method = cp.findClass<java.util.ArrayList<*>>().methods.first() { it.name == "clear" }
        val rules = taintFeature.getConfigForMethod(method)

        assertTrue(rules.singleOrNull() != null)
    }
}
