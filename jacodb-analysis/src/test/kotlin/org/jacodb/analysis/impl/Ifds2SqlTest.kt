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
import mu.KotlinLogging
import org.jacodb.analysis.engine.SingletonUnitResolver
import org.jacodb.analysis.graph.newApplicationGraphForAnalysis
import org.jacodb.analysis.ifds2.taint.TaintManager
import org.jacodb.analysis.ifds2.taint.Vulnerability
import org.jacodb.analysis.impl.BaseAnalysisTest.Companion.provideClassesForJuliet
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
import org.jacodb.testing.analysis.SqlInjectionExamples
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}

class Ifds2SqlTest : BaseTest() {
    companion object : WithDB(Usages, InMemoryHierarchy) {
        @JvmStatic
        fun provideClassesForJuliet89(): Stream<Arguments> = provideClassesForJuliet(89, specificBansCwe89)

        private val specificBansCwe89: List<String> = listOf(
            // Not working yet (#156)
            "s03", "s04"
        )
    }

    override val cp: JcClasspath = runBlocking {
        val taintConfigFileName = "config_small.json"
        val defaultConfigResource = this.javaClass.getResourceAsStream("/$taintConfigFileName")
        if (defaultConfigResource != null) {
            val configJson = defaultConfigResource.bufferedReader().readText()
            val configurationFeature = TaintConfigurationFeature.fromJson(configJson)
            db.classpath(allClasspath, listOf(configurationFeature) + classpathFeatures)
        } else {
            super.cp
        }
    }

    @Test
    fun `simple SQL injection`() {
        val methodName = "bad"
        val method = cp.findClass<SqlInjectionExamples>().declaredMethods.single { it.name == methodName }
        val sinks = findSinks(method)
        assertTrue(sinks.isNotEmpty())
    }

    @ParameterizedTest
    @MethodSource("provideClassesForJuliet89")
    fun `test on Juliet's CWE 89`(className: String) {
        testSingleJulietClass(className)
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
        // val className = "juliet.testcases.CWE89_SQL_Injection.s01.CWE89_SQL_Injection__Environment_executeBatch_45"
        val className = "juliet.testcases.CWE89_SQL_Injection.s01.CWE89_SQL_Injection__connect_tcp_execute_01"

        testSingleJulietClass(className)
    }

    private fun testSingleJulietClass(className: String) {
        logger.info { className }

        val clazz = cp.findClass(className)
        val badMethod = clazz.methods.single { it.name == "bad" }
        val goodMethod = clazz.methods.single { it.name == "good" }

        logger.info { "Searching for sinks in BAD method: $badMethod" }
        val badIssues = findSinks(badMethod)
        logger.info { "Total ${badIssues.size} issues in BAD method" }
        for (issue in badIssues) {
            logger.debug { "  - $issue" }
        }
        assertTrue(badIssues.isNotEmpty()) { "Must find some sinks in 'bad' for $className" }

        logger.info { "Searching for sinks in GOOD method: $goodMethod" }
        val goodIssues = findSinks(goodMethod)
        logger.info { "Total ${goodIssues.size} issues in GOOD method" }
        for (issue in goodIssues) {
            logger.debug { "  - $issue" }
        }
        assertTrue(goodIssues.isEmpty()) { "Must NOT find any sinks in 'good' for $className" }
    }

    private fun findSinks(method: JcMethod): List<Vulnerability> {
        val vulnerabilities = launchAnalysis(listOf(method))
        return vulnerabilities
    }

    private fun launchAnalysis(methods: List<JcMethod>): List<Vulnerability> {
        val graph = runBlocking {
            cp.newApplicationGraphForAnalysis()
        }
        val unitResolver = SingletonUnitResolver
        val manager = TaintManager(graph, unitResolver)
        return manager.analyze(methods, timeout = 30.seconds)
    }
}
