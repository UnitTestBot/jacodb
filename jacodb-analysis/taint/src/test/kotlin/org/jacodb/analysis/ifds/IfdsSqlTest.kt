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

package org.jacodb.analysis.ifds

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jacodb.analysis.ifds.common.defaultBannedPackagePrefixes
import org.jacodb.analysis.ifds.result.buildTraceGraph
import org.jacodb.analysis.ifds.sarif.sarifReportFromVulnerabilities
import org.jacodb.analysis.ifds.sarif.toSarif
import org.jacodb.analysis.ifds.taint.TaintVulnerability
import org.jacodb.analysis.ifds.taint.TaintZeroFact
import org.jacodb.analysis.ifds.taint.taintIfdsFacade
import org.jacodb.api.JcMethod
import org.jacodb.api.ext.findClass
import org.jacodb.api.ext.methods
import org.jacodb.impl.features.InMemoryHierarchy
import org.jacodb.impl.features.Usages
import org.jacodb.testing.WithDB
import org.jacodb.testing.analysis.SqlInjectionExamples
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

private val logger = mu.KotlinLogging.logger {}

class IfdsSqlTest : BaseAnalysisTest() {

    companion object : WithDB(Usages, InMemoryHierarchy) {
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
    fun `simple SQL injection`() = runBlocking {
        val methodName = "bad"
        val method = cp.findClass<SqlInjectionExamples>().declaredMethods.single { it.name == methodName }

        val ifds = taintIfdsFacade("ifds", cp, graph)

        ifds.startAnalysis(method)
        ifds.awaitAnalysis()
        val data = ifds.collectComputationData()
        val sinks = data.findings
        assertTrue(sinks.isNotEmpty())
        val sink = sinks.first()
        val graph = data.buildTraceGraph(sink.vertex, zeroFact = TaintZeroFact)
        val trace = graph.getAllTraces().first()
        assertTrue(trace.isNotEmpty())
    }

    private fun findSinks(method: JcMethod): Collection<TaintVulnerability> = runBlocking {
        val system = taintIfdsFacade("ifds", cp, graph)

        system.startAnalysis(method)
        system.awaitAnalysis()
        system.collectFindings()
    }

    @ParameterizedTest
    @MethodSource("provideClassesForJuliet89")
    fun `test on Juliet's CWE 89`(className: String) {
        testSingleJulietClass(className) { method ->
            findSinks(method)
        }
    }

    @Test
    fun `test on specific Juliet instance`() {
        val className = "juliet.testcases.CWE89_SQL_Injection.s01.CWE89_SQL_Injection__connect_tcp_execute_01"
        testSingleJulietClass(className) { method ->
            findSinks(method)
        }
    }

    @Test
    fun `test trace collection`() = runBlocking {
        val className = "juliet.testcases.CWE89_SQL_Injection.s01.CWE89_SQL_Injection__Environment_executeBatch_51a"
        val clazz = cp.findClass(className)
        val badMethod = clazz.methods.single { it.name == "bad" }

        val ifds = taintIfdsFacade("ifds", cp, graph)

        ifds.startAnalysis(badMethod)
        ifds.awaitAnalysis()

        val data = ifds.collectComputationData()
        val sinks = data.findings
        assertTrue(sinks.isNotEmpty())
        val sink = sinks.first()
        val graph = data.buildTraceGraph(sink.vertex, zeroFact = TaintZeroFact)
        val trace = graph.getAllTraces().first()
        assertTrue(trace.isNotEmpty())

        val sarif = sarifReportFromVulnerabilities(listOf(sink.toSarif(graph)))
        val sarifJson = myJson.encodeToString(sarif)
        logger.info { "SARIF:\n$sarifJson" }
    }
}
