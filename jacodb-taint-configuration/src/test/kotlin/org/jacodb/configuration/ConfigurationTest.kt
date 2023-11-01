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
import org.jacodb.impl.features.classpaths.UnknownClasses
import org.jacodb.testing.BaseTest
import org.jacodb.testing.WithDB
import org.jacodb.testing.allClasspath

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


}
