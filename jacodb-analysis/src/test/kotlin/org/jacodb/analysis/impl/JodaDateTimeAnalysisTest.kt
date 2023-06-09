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

import org.jacodb.analysis.JcNaivePoints2EngineFactory
import org.jacodb.analysis.JcSimplifiedGraphFactory
import org.jacodb.analysis.analyzers.NpeAnalyzer
import org.jacodb.analysis.analyzers.UnusedVariableAnalyzer
import org.jacodb.analysis.engine.Analyzer
import org.jacodb.analysis.engine.BidiIFDSForTaintAnalysis
import org.jacodb.analysis.engine.ClassUnitResolver
import org.jacodb.analysis.engine.IFDSInstanceProvider
import org.jacodb.analysis.engine.IFDSUnitInstance
import org.jacodb.analysis.engine.IFDSUnitTraverser
import org.jacodb.analysis.engine.UnitResolver
import org.jacodb.api.ext.findClass
import org.jacodb.impl.features.InMemoryHierarchy
import org.jacodb.impl.features.Usages
import org.jacodb.testing.BaseTest
import org.jacodb.testing.WithDB
import org.joda.time.DateTime
import org.junit.jupiter.api.Test

class JodaDateTimeAnalysisTest : BaseTest() {
    companion object : WithDB(Usages, InMemoryHierarchy)

    fun testOne(analyzer: Analyzer, unitResolver: UnitResolver<*>, ifdsInstanceProvider: IFDSInstanceProvider) {
        val clazz = cp.findClass<DateTime>()

        val graph = JcSimplifiedGraphFactory().createGraph(cp)
        val points2Engine = JcNaivePoints2EngineFactory.createPoints2Engine(graph)
        val engine = IFDSUnitTraverser(graph, analyzer, unitResolver, points2Engine.obtainDevirtualizer(), ifdsInstanceProvider)
        clazz.declaredMethods.forEach { engine.addStart(it) }
        val result = engine.analyze().toDumpable().foundVulnerabilities

        result.forEachIndexed { ind, vulnerability ->
            println("VULNERABILITY $ind:")
            println(vulnerability)
        }
    }

    @Test
    fun `test Unused variable analysis`() {
        testOne(UnusedVariableAnalyzer(JcSimplifiedGraphFactory().createGraph(cp)), ClassUnitResolver(true), IFDSUnitInstance)
    }

    @Test
    fun `test NPE analysis`() {
        testOne(NpeAnalyzer(JcSimplifiedGraphFactory().createGraph(cp)), ClassUnitResolver(true), BidiIFDSForTaintAnalysis)
    }
}