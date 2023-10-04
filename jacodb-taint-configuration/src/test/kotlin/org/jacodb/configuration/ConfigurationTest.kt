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
import org.jacodb.testing.BaseTest
import org.jacodb.testing.WithDB
import org.jacodb.testing.allClasspath
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

class ConfigurationTest : BaseTest() {
    companion object : WithDB()

    override val cp: JcClasspath = runBlocking {
        val defaultConfigResource = TaintConfigurationFeature::class.java.getResourceAsStream("/defaultTaintConfig.json")
        val configJson = defaultConfigResource.bufferedReader().readText()
        val configurationFeature = TaintConfigurationFeature.fromJson(configJson)
        val features = listOf(configurationFeature)
        db.classpath(allClasspath, features)
    }

    @Test
    @Disabled
    fun test() {
        val feature = cp.taintConfigurationFeature()

        val clazz = cp.findClass<java.net.URL>()
        val rules = clazz.methods.associateWith { feature.getConfigForMethod(it) }

        assertTrue(rules.any { it.value.isNotEmpty() })
    }
}
