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

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import org.jacodb.analysis.engine.Manager
import org.jacodb.analysis.engine.SingletonUnitResolver
import org.jacodb.analysis.engine.VulnerabilityInstance
import org.jacodb.analysis.graph.newApplicationGraphForAnalysis
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcMethod
import org.jacodb.api.ext.findClass
import org.jacodb.api.ext.methods
import org.jacodb.impl.features.InMemoryHierarchy
import org.jacodb.impl.features.Usages
import org.jacodb.taint.configuration.TaintConfigurationFeature
import org.jacodb.testing.BaseTest
import org.jacodb.testing.WithDB
import org.jacodb.testing.allClasspath
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private val logger = KotlinLogging.logger {}

class IfdsTaintTest : BaseTest() {
    companion object : WithDB(Usages, InMemoryHierarchy) {
        //
    }

    override val cp: JcClasspath = runBlocking {
        val defaultConfigResource = this.javaClass.getResourceAsStream("/config.json")
        if (defaultConfigResource != null) {
            val configJson = defaultConfigResource.bufferedReader().readText()
            val configurationFeature = TaintConfigurationFeature.fromJson(configJson)
            db.classpath(allClasspath, listOf(configurationFeature) + classpathFeatures)
        } else {
            super.cp
        }
    }

    @Test
    fun `test on Juliet's CWE 89 Environment executeBatch 01`() {
        val className = "juliet.testcases.CWE89_SQL_Injection.s01.CWE89_SQL_Injection__Environment_executeBatch_01"
        testSingleJulietClass(className)
    }

    @Test
    fun `test on Juliet's CWE 89 database prepareStatement 01`() {
        val className = "juliet.testcases.CWE89_SQL_Injection.s01.CWE89_SQL_Injection__database_prepareStatement_01"
        testSingleJulietClass(className)
    }

    @Test
    fun `test on specific Juliet instance`() {
        //
        val className = "juliet.testcases.CWE89_SQL_Injection.s01.CWE89_SQL_Injection__Environment_executeBatch_01"
        //

        testSingleJulietClass(className)
    }

    private inline fun <reified T> testOneAnalysisOnOneMethod(
        methodName: String,
        expectedLocations: Collection<String>,
    ) {
        val method = cp.findClass<T>().declaredMethods.single { it.name == methodName }
        val sinks = findSinks(method)

        // TODO: think about better assertions here
        assertEquals(expectedLocations.size, sinks.size)
        expectedLocations.forEach { expected ->
            assertTrue(sinks.map { it.traceGraph.sink.toString() }.any { it.contains(expected) })
        }
    }

    private fun testSingleJulietClass(className: String) {
        println(className)

        val clazz = cp.findClass(className)
        val goodMethod = clazz.methods.single { it.name == "good" }
        val badMethod = clazz.methods.single { it.name == "bad" }

        val goodIssues = findSinks(goodMethod)
        logger.info { "goodIssues: ${goodIssues.size} total" }
        for (issue in goodIssues) {
            logger.debug { "  - $issue" }
        }

        val badIssues = findSinks(badMethod)
        logger.info { "badIssues: ${badIssues.size} total" }
        for (issue in badIssues) {
            logger.debug { "  - $issue" }
        }

        assertTrue(goodIssues.isEmpty())
        assertTrue(badIssues.isNotEmpty())
    }

    private fun findSinks(method: JcMethod): Set<VulnerabilityInstance> {
        val vulnerabilities = launchAnalysis(listOf(method))
        return vulnerabilities.toSet()
    }

    private fun launchAnalysis(methods: List<JcMethod>): List<VulnerabilityInstance> {
        val graph = runBlocking {
            cp.newApplicationGraphForAnalysis()
        }
        val manager = Manager(graph, SingletonUnitResolver)
        return manager.analyze(methods)
    }
}
