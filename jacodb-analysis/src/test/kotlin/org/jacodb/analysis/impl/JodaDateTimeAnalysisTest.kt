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

import org.jacodb.analysis.AnalysisEngineFactory
import org.jacodb.analysis.JcNaivePoints2EngineFactory
import org.jacodb.analysis.JcSimplifiedGraphFactory
import org.jacodb.analysis.NPEAnalysisFactory
import org.jacodb.analysis.UnusedVariableAnalysisFactory
import org.jacodb.analysis.analyzers.NpeAnalyzer
import org.jacodb.analysis.engine.IFDSUnitTraverser
import org.jacodb.analysis.engine.PackageUnitResolver
import org.jacodb.api.ext.findClass
import org.jacodb.impl.features.InMemoryHierarchy
import org.jacodb.impl.features.Usages
import org.jacodb.testing.BaseTest
import org.jacodb.testing.WithDB
import org.joda.time.DateTime
import org.junit.jupiter.api.Test

//@Disabled("Running time is more then 10 minutes")
class JodaDateTimeAnalysisTest : BaseTest() {
    companion object : WithDB(Usages, InMemoryHierarchy)

    fun testOneFactory(factory: AnalysisEngineFactory) {
        val clazz = cp.findClass<DateTime>()

        val graph = JcSimplifiedGraphFactory().createGraph(cp)
        val points2Engine = JcNaivePoints2EngineFactory.createPoints2Engine(graph)
        val ifds = IFDSUnitTraverser(graph, NpeAnalyzer(graph), PackageUnitResolver, points2Engine.obtainDevirtualizer())
        clazz.declaredMethods.forEach { ifds.addStart(it) }
        val result = ifds.analyze().toDumpable().foundVulnerabilities

        result.forEachIndexed { ind, vulnerability ->
            println("VULNERABILITY $ind:")
            println(vulnerability)
        }
    }

    @Test
    fun `test Unused variable analysis`() {
        testOneFactory(UnusedVariableAnalysisFactory())
    }

    @Test
    fun `test NPE analysis`() {
        testOneFactory(NPEAnalysisFactory())
    }
}