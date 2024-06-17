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

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jacodb.analysis.ifds.ClassUnitResolver
import org.jacodb.analysis.ifds.SingletonUnitResolver
import org.jacodb.analysis.sarif.sarifReportFromVulnerabilities
import org.jacodb.analysis.taint.TaintManager
import org.jacodb.analysis.taint.toSarif
import org.jacodb.api.jvm.ext.findClass
import org.jacodb.api.jvm.ext.methods
import org.jacodb.impl.features.InMemoryHierarchy
import org.jacodb.impl.features.Usages
import org.jacodb.testing.WithDB
import org.jacodb.testing.WithRAMDB
import org.jacodb.testing.analysis.SqlInjectionExamples
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.time.Duration.Companion.seconds

private val logger = mu.KotlinLogging.logger {}

abstract class IfdsSqlTest : BaseAnalysisTest() {

    companion object {
        @JvmStatic
        fun provideClassesForJuliet89(): Stream<Arguments> = provideClassesForJuliet(89, specificBansCwe89)

        private val specificBansCwe89: List<String> = listOf(
            // Not working yet (#156)
            "s03", "s04"
        )
    }

    private val myJson = Json {
        prettyPrint = true
    }

    @Test
    fun `simple SQL injection`() {
        val methodName = "bad"
        val method = cp.findClass<SqlInjectionExamples>().declaredMethods.single { it.name == methodName }
        val methods = listOf(method)
        val unitResolver = SingletonUnitResolver
        val manager = TaintManager(graph, unitResolver)
        val sinks = manager.analyze(methods, timeout = 30.seconds)
        assertTrue(sinks.isNotEmpty())
        val sink = sinks.first()
        val graph = manager.vulnerabilityTraceGraph(sink)
        val trace = graph.getAllTraces().first()
        assertTrue(trace.isNotEmpty())
    }

    @ParameterizedTest
    @MethodSource("provideClassesForJuliet89")
    fun `test on Juliet's CWE 89`(className: String) {
        testSingleJulietClass(className) { method ->
            val unitResolver = SingletonUnitResolver
            val manager = TaintManager(graph, unitResolver)
            manager.analyze(listOf(method), timeout = 30.seconds)
        }
    }

    @Test
    fun `test on specific Juliet instance`() {
        val className = "juliet.testcases.CWE89_SQL_Injection.s01.CWE89_SQL_Injection__connect_tcp_execute_01"
        testSingleJulietClass(className) { method ->
            val unitResolver = SingletonUnitResolver
            val manager = TaintManager(graph, unitResolver)
            manager.analyze(listOf(method), timeout = 30.seconds)
        }
    }

    @Test
    fun `test bidirectional runner and other stuff`() {
        val className = "juliet.testcases.CWE89_SQL_Injection.s01.CWE89_SQL_Injection__Environment_executeBatch_51a"
        val clazz = cp.findClass(className)
        val badMethod = clazz.methods.single { it.name == "bad" }
        val unitResolver = ClassUnitResolver(true)
        val manager = TaintManager(graph, unitResolver, useBidiRunner = true)
        val sinks = manager.analyze(listOf(badMethod), timeout = 30.seconds)
        assertTrue(sinks.isNotEmpty())
        val sink = sinks.first()
        val graph = manager.vulnerabilityTraceGraph(sink)
        val trace = graph.getAllTraces().first()
        assertTrue(trace.isNotEmpty())
        val sarif = sarifReportFromVulnerabilities(listOf(sink.toSarif(graph)))
        val sarifJson = myJson.encodeToString(sarif)
        logger.info { "SARIF:\n$sarifJson" }
    }
}

class IfdsSqlSqlTest : IfdsSqlTest() {
    companion object : WithDB(Usages, InMemoryHierarchy)
}

class IfdsSqlRAMTest : IfdsSqlTest() {
    companion object : WithRAMDB(Usages, InMemoryHierarchy)
}