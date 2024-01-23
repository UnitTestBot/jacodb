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
import org.jacodb.analysis.library.JcSingletonUnitResolver
import org.jacodb.analysis.library.analyzers.JcSqlInjectionAnalyzer
import org.jacodb.analysis.library.newJcSqlInjectionRunnerFactory
import org.jacodb.analysis.runAnalysis
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.cfg.JcInst
import org.jacodb.api.jvm.cfg.JcInstLocation
import org.jacodb.impl.features.InMemoryHierarchy
import org.jacodb.impl.features.Usages
import org.jacodb.testing.WithDB
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class SqlInjectionAnalysisTest : BaseAnalysisTest() {
    companion object : WithDB(Usages, InMemoryHierarchy) {
        @JvmStatic
        fun provideClassesForJuliet89(): Stream<Arguments> = provideClassesForJuliet(89, listOf(
            // Not working yet (#156)
            "s03", "s04"
        ))

        private val vulnerabilityType = JcSqlInjectionAnalyzer.vulnerabilityDescription.ruleId
    }

    @ParameterizedTest
    @MethodSource("provideClassesForJuliet89")
    fun `test on Juliet's CWE 89`(className: String) {
        testSingleJulietClass(vulnerabilityType, className)
    }

    override fun launchAnalysis(methods: List<JcMethod>): List<VulnerabilityInstance<JcMethod, JcInstLocation, JcInst>> {
        val graph = runBlocking {
            cp.newApplicationGraphForAnalysis()
        }
        return runAnalysis(graph, JcSingletonUnitResolver, newJcSqlInjectionRunnerFactory(), methods)
    }
}