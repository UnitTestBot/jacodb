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
import org.jacodb.analysis.engine.IfdsUnitRunnerFactory
import org.jacodb.analysis.engine.UnitResolver
import org.jacodb.analysis.graph.newApplicationGraphForAnalysis
import org.jacodb.analysis.library.MethodUnitResolver
import org.jacodb.analysis.library.UnusedVariableRunnerFactory
import org.jacodb.analysis.library.getClassUnitResolver
import org.jacodb.analysis.library.newNpeRunnerFactory
import org.jacodb.analysis.runAnalysis
import org.jacodb.analysis.sarif.SarifReport
import org.jacodb.api.ext.findClass
import org.jacodb.impl.features.InMemoryHierarchy
import org.jacodb.impl.features.Usages
import org.jacodb.testing.BaseTest
import org.jacodb.testing.WithDB
import org.joda.time.DateTime
import org.junit.jupiter.api.Test

class JodaDateTimeAnalysisTest : BaseTest() {
    companion object : WithDB(Usages, InMemoryHierarchy)

    private fun <UnitType> testOne(unitResolver: UnitResolver<UnitType>, ifdsUnitRunnerFactory: IfdsUnitRunnerFactory) {
        val clazz = cp.findClass<DateTime>()
        val result = runAnalysis(graph, unitResolver, ifdsUnitRunnerFactory, clazz.declaredMethods, 60000L)

        println("Vulnerabilities found: ${result.size}")
        println("Generated report:")
        SarifReport.fromVulnerabilities(result).encodeToStream(System.out)
    }

    @Test
    fun `test Unused variable analysis`() {
        testOne(getClassUnitResolver(false), UnusedVariableRunnerFactory)
    }

    @Test
    fun `test NPE analysis`() {
        testOne(MethodUnitResolver, newNpeRunnerFactory())
    }

    private val graph = runBlocking {
        cp.newApplicationGraphForAnalysis()
    }
}
