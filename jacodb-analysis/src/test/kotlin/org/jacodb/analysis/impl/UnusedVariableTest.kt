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

import juliet.testcasesupport.AbstractTestCase
import kotlinx.coroutines.runBlocking
import org.jacodb.analysis.JcNaivePoints2EngineFactory
import org.jacodb.analysis.JcSimplifiedGraphFactory
import org.jacodb.analysis.UnusedVariableAnalysisFactory
import org.jacodb.analysis.VulnerabilityInstance
import org.jacodb.analysis.analyzers.UnusedVariableAnalyzer
import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcMethod
import org.jacodb.api.ext.findClass
import org.jacodb.api.ext.methods
import org.jacodb.api.ext.packageName
import org.jacodb.impl.features.InMemoryHierarchy
import org.jacodb.impl.features.Usages
import org.jacodb.impl.features.hierarchyExt
import org.jacodb.testing.BaseTest
import org.jacodb.testing.WithDB
import org.jacodb.testing.allClasspath
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.*
import java.util.stream.Stream
import kotlin.streams.asStream

class UnusedVariableTest : BaseTest() {
    companion object : WithDB(Usages, InMemoryHierarchy) {
        @JvmStatic
        fun provideClassesForJuliet563(): Stream<Arguments> = runBlocking {
            val cp = db.classpath(allClasspath)
            val hierarchyExt = cp.hierarchyExt()
            val baseClass = cp.findClass<AbstractTestCase>()
            val classes = hierarchyExt.findSubClasses(baseClass, false)
            classes.toArguments("CWE563")
        }

        private fun Sequence<JcClassOrInterface>.toArguments(cwe: String): Stream<Arguments> = map { it.name }
            .filter { it.contains(cwe) }
            .filterNot { className -> bannedTests.any { className.contains(it) } }
            .sorted()
            .map { Arguments.of(it) }
            .asStream()

        private val bannedTests = listOf(
            // Unused variables are already optimized out by cfg
            "unused_uninit_variable_", "unused_init_variable_int", "unused_init_variable_long", "unused_init_variable_String_",

            // Unused variable is generated by cfg (!!)
            "unused_value_StringBuilder_17",

            // Expected answers are strange, seems to be problem in tests
            "_12",

            // TODO: containers not supported
            "_72", "_73", "_74",

            // TODO/Won't fix(?): dead parts of switches shouldn't be analyzed
            "_15",

            // TODO/Won't fix(?): passing through channels not supported
            "_75",

            // TODO/Won't fix(?): constant private/static methods not analyzed
            "_11", "_08",

            // TODO/Won't fix(?): unmodified non-final private variables not analyzed
            "_05", "_07",

            // TODO/Won't fix(?): unmodified non-final static variables not analyzed
            "_10", "_14"
        )
    }

    @ParameterizedTest
    @MethodSource("provideClassesForJuliet563")
    fun `test on Juliet's CWE 563`(className: String) {
        testJuliet(className)
    }

    private fun testJuliet(className: String) {
        val clazz = cp.findClass(className)
        val goodMethod = clazz.methods.single { it.name == "good" }
        val badMethod = clazz.methods.single { it.name == "bad" }

        val good = findUnusedVariables(goodMethod)
        val bad = findUnusedVariables(badMethod)
        val message = clazz.packageName + "" + className
        assertTrue(bad.isNotEmpty(), "not found problem in bad method of $message")
        assertTrue(good.isEmpty(), "found problem in good method of $message")
    }

    private fun findUnusedVariables(method: JcMethod): List<VulnerabilityInstance> {
        val graph = JcSimplifiedGraphFactory().createGraph(cp)
        val points2Engine = JcNaivePoints2EngineFactory.createPoints2Engine(graph)
        val ifds = UnusedVariableAnalysisFactory().createAnalysisEngine(graph, points2Engine)
        ifds.addStart(method)
        val result = ifds.analyze()
        return result.foundVulnerabilities.filter { it.vulnerabilityType == UnusedVariableAnalyzer.value }
    }
}