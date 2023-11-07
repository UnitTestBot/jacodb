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
import org.jacodb.analysis.engine.VulnerabilityInstance
import org.jacodb.analysis.graph.newApplicationGraphForAnalysis
import org.jacodb.analysis.library.SingletonUnitResolver
import org.jacodb.analysis.library.analyzers.SqlInjectionAnalyzer
import org.jacodb.analysis.library.newSqlInjectionRunnerFactory
import org.jacodb.analysis.runAnalysis
import org.jacodb.api.JcMethod
import org.jacodb.impl.features.InMemoryHierarchy
import org.jacodb.impl.features.Usages
import org.jacodb.testing.WithDB
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class SqlInjectionAnalysisTest : BaseAnalysisTest() {
    companion object : WithDB(Usages, InMemoryHierarchy) {
        @JvmStatic
        fun provideClassesForJuliet89(): Stream<Arguments> =
            provideClassesForJuliet(89, specificBansCwe89)

        private val vulnerabilityType = SqlInjectionAnalyzer.vulnerabilityDescription.ruleId
        private val specificBansCwe89: List<String> = listOf(
            // Not working yet (#156)
            "s03", "s04"
        )
    }

    @ParameterizedTest
    @MethodSource("provideClassesForJuliet89")
    fun `test on Juliet's CWE 89`(className: String) {
        testSingleJulietClass(vulnerabilityType, className)
    }

    @Test
    fun `test on specific Juliet's CWE 89`() {
        val className = "juliet.testcases.CWE89_SQL_Injection.s01.CWE89_SQL_Injection__Environment_executeBatch_01"
        // val className = "juliet.testcases.CWE89_SQL_Injection.s02.CWE89_SQL_Injection__File_executeQuery_02"
        // val className = "juliet.testcases.CWE89_SQL_Injection.s01.CWE89_SQL_Injection__Environment_executeBatch_13"
        testSingleJulietClass(vulnerabilityType, className)

        for (className in getJulietClasses(89, specificBansCwe89).take(10)) {
            testSingleJulietClass(vulnerabilityType, className)
        }
    }

    override fun launchAnalysis(methods: List<JcMethod>): List<VulnerabilityInstance> {
        val graph = runBlocking {
            cp.newApplicationGraphForAnalysis()
        }
        return runAnalysis(graph, SingletonUnitResolver, newSqlInjectionRunnerFactory(), methods)
    }
}
