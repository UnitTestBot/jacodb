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

package org.jacodb.analysis.impl

import kotlinx.coroutines.runBlocking
import org.jacodb.analysis.graph.newApplicationGraphForAnalysis
import org.jacodb.analysis.ifds.SingletonUnitResolver
import org.jacodb.analysis.npe.NpeManager
import org.jacodb.analysis.taint.TaintManager
import org.jacodb.analysis.unused.UnusedVariableManager
import org.jacodb.analysis.util.JcTraits
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.analysis.JcApplicationGraph
import org.jacodb.api.jvm.ext.findClass
import org.jacodb.taint.configuration.TaintConfigurationFeature
import org.jacodb.testing.BaseTest
import org.jacodb.testing.WithGlobalDB
import org.jacodb.testing.allClasspath
import org.joda.time.DateTime
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

private val logger = mu.KotlinLogging.logger {}

class JodaDateTimeAnalysisTest : BaseTest() {

    companion object : WithGlobalDB()

    override val cp: JcClasspath = runBlocking {
        val configFileName = "config_small.json"
        val configResource = this.javaClass.getResourceAsStream("/$configFileName")
        if (configResource != null) {
            val configJson = configResource.bufferedReader().readText()
            val configurationFeature = TaintConfigurationFeature.fromJson(configJson)
            db.classpath(allClasspath, listOf(configurationFeature) + classpathFeatures)
        } else {
            super.cp
        }
    }

    private val graph: JcApplicationGraph by lazy {
        runBlocking {
            cp.newApplicationGraphForAnalysis()
        }
    }

    @Test
    fun `test taint analysis`() {
        val clazz = cp.findClass<DateTime>()
        val methods = clazz.declaredMethods
        val unitResolver = SingletonUnitResolver
        val manager = TaintManager(graph, JcTraits, unitResolver)
        val sinks = manager.analyze(methods, timeout = 60.seconds)
        logger.info { "Vulnerabilities found: ${sinks.size}" }
    }

    @Test
    fun `test NPE analysis`() {
        val clazz = cp.findClass<DateTime>()
        val methods = clazz.declaredMethods
        val unitResolver = SingletonUnitResolver
        val manager = NpeManager(graph, JcTraits, unitResolver)
        val sinks = manager.analyze(methods, timeout = 60.seconds)
        logger.info { "Vulnerabilities found: ${sinks.size}" }
    }

    @Test
    fun `test unused variables analysis`() {
        val clazz = cp.findClass<DateTime>()
        val methods = clazz.declaredMethods
        val unitResolver = SingletonUnitResolver
        val manager = UnusedVariableManager(graph, unitResolver)
        val sinks = manager.analyze(methods, timeout = 60.seconds)
        logger.info { "Unused variables found: ${sinks.size}" }
    }
}
