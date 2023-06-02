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
import org.jacodb.analysis.NPEAnalysisFactory
import org.jacodb.analysis.VulnerabilityInstance
import org.jacodb.analysis.analyzers.NpeAnalyzer
import org.jacodb.analysis.graph.JcApplicationGraphImpl
import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcMethod
import org.jacodb.api.ext.constructors
import org.jacodb.api.ext.findClass
import org.jacodb.api.ext.methods
import org.jacodb.impl.features.InMemoryHierarchy
import org.jacodb.impl.features.Usages
import org.jacodb.impl.features.hierarchyExt
import org.jacodb.impl.features.usagesExt
import org.jacodb.testing.BaseTest
import org.jacodb.testing.WithDB
import org.jacodb.testing.allClasspath
import org.jacodb.testing.analysis.NPEExamples
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.*
import java.util.stream.Stream
import kotlin.streams.asStream

class NpeAnalysisTest : BaseTest() {
    companion object : WithDB(Usages, InMemoryHierarchy) {
        @JvmStatic
        fun provideClassesForJuliet476(): Stream<Arguments> = runBlocking {
            val cp = db.classpath(allClasspath)
            val hierarchyExt = cp.hierarchyExt()
            val baseClass = cp.findClass<AbstractTestCase>()
            val classes = hierarchyExt.findSubClasses(baseClass, false)
            classes.toArguments("CWE476")
        }

        @JvmStatic
        fun provideClassesForJuliet690(): Stream<Arguments> = runBlocking {
            val cp = db.classpath(allClasspath)
            val hierarchyExt = cp.hierarchyExt()
            val baseClass = cp.findClass<AbstractTestCase>()
            val classes = hierarchyExt.findSubClasses(baseClass, false)
            classes.toArguments("CWE690")
        }

        private fun Sequence<JcClassOrInterface>.toArguments(cwe: String): Stream<Arguments> = map { it.name }
            .filter { it.contains(cwe) }
            .filterNot { className -> bannedTests.any { className.contains(it) } }
//            .filter { it.contains("_45") }
            .sorted()
            .map { Arguments.of(it) }
            .asStream()

        private val bannedTests = listOf(
            // not NPE problems
            "null_check_after_deref",

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

    @Test
    fun `fields resolving should work through interfaces`() = runBlocking {
        val graph = JcApplicationGraphImpl(cp, cp.usagesExt())
        val callers = graph.callers(cp.findClass<StringTokenizer>().constructors[2])
        println(callers.toList().size)
    }

    @Test
    fun `analyze simple NPE`() {
        testOneMethod<NPEExamples>("npeOnLength", listOf("%3 = %2.length()"))
    }

    @Test
    fun `analyze no NPE`() {
        testOneMethod<NPEExamples>("noNPE", emptyList())
    }

    @Test
    fun `analyze NPE after fun with two exits`() {
        testOneMethod<NPEExamples>(
            "npeAfterTwoExits",
            listOf("%4 = %2.length()", "%5 = %3.length()")
        )
    }

    @Test
    fun `no NPE after checked access`() {
        testOneMethod<NPEExamples>("checkedAccess", emptyList())
    }

    @Test
    fun `consecutive NPEs handled properly`() {
        testOneMethod<NPEExamples>(
            "consecutiveNPEs",
            listOf("%2 = arg$0.length()", "%4 = arg$0.length()")
        )
    }

    @Test
    fun `npe on virtual call when possible`() {
        testOneMethod<NPEExamples>(
            "possibleNPEOnVirtualCall",
            listOf("%0 = arg\$0.length()")
        )
    }

    @Test
    fun `no npe on virtual call when impossible`() {
        testOneMethod<NPEExamples>(
            "noNPEOnVirtualCall",
            emptyList()
        )
    }

    @Test
    fun `basic test for NPE on fields`() {
        testOneMethod<NPEExamples>("simpleNPEOnField", listOf("%8 = %6.length()"))
    }

    @Test
    fun `simple points-to analysis`() {
        testOneMethod<NPEExamples>("simplePoints2", listOf("%5 = %4.length()"))
    }

    @Test
    fun `complex aliasing`() {
        testOneMethod<NPEExamples>("complexAliasing", listOf("%6 = %5.length()"))
    }

    @Test
    fun `context injection in points-to`() {
        testOneMethod<NPEExamples>(
            "contextInjection",
            listOf("%6 = %5.length()", "%3 = %2.length()")
        )
    }

    @Test
    fun `activation points maintain flow sensitivity`() {
        testOneMethod<NPEExamples>("flowSensitive", listOf("%8 = %7.length()"))
    }

    @Test
    fun `overridden null assignment in callee don't affect next caller's instructions`() {
        testOneMethod<NPEExamples>("overriddenNullInCallee", emptyList())
    }

    @Test
    fun `recursive classes handled correctly`() {
        testOneMethod<NPEExamples>(
            "recursiveClass",
            listOf("%10 = %9.toString()", "%15 = %14.toString()")
        )
    }

    @Test
    fun `NPE on uninitialized array element dereferencing`() {
        testOneMethod<NPEExamples>("simpleArrayNPE", listOf("%5 = %4.length()"))
    }

    @Test
    fun `no NPE on array element dereferencing after initialization`() {
        testOneMethod<NPEExamples>("noNPEAfterArrayInit", emptyList())
    }

    @Test
    fun `array aliasing`() {
        testOneMethod<NPEExamples>("arrayAliasing", listOf("%5 = %4.length()"))
    }

    @Test
    fun `mixed array and class aliasing`() {
        testOneMethod<NPEExamples>("mixedArrayClassAliasing", listOf("%13 = %12.length()"))
    }

    @Test
    fun `dereferencing field of null object`() {
        testOneMethod<NPEExamples>("npeOnFieldDeref", listOf("%1 = %0.field"))
    }

    @Test
    fun `dereferencing copy of value saved before null assignment produce no npe`() {
        testOneMethod<NPEExamples>("copyBeforeNullAssignment", emptyList())
    }

    @Test
    fun `assigning null to copy doesn't affect original value`() {
        testOneMethod<NPEExamples>("nullAssignmentToCopy", emptyList())
    }

    @ParameterizedTest
    @MethodSource("provideClassesForJuliet476")
    fun `test on Juliet's CWE 476`(className: String) {
        testJuliet(className)
    }

    @ParameterizedTest
    @MethodSource("provideClassesForJuliet690")
    fun `test on Juliet's CWE 690`(className: String) {
        testJuliet(className)
    }

    private fun testJuliet(className: String) {
        val clazz = cp.findClass(className)
        val goodMethod = clazz.methods.single { it.name == "good" }
        val badMethod = clazz.methods.single { it.name == "bad" }

        val goodNPE = findNpeSources(goodMethod)
        val badNPE = findNpeSources(badMethod)

        assertTrue(badNPE.isNotEmpty())
        assertTrue(goodNPE.isEmpty())
    }

    private inline fun <reified T> testOneMethod(methodName: String, expectedLocations: Collection<String>) {
        val method = cp.findClass<T>().declaredMethods.single { it.name == methodName }
        val npes = findNpeSources(method)

        // TODO: think about better assertions here
        assertEquals(expectedLocations.toSet(), npes.map { it.sink }.toSet())
    }

    @Test
    fun `analyse something`() {
        val testingMethod = cp.findClass<NPEExamples>().declaredMethods.single { it.name == "id" }
        val results = testingMethod.flowGraph()
        print(results)
    }

    private fun findNpeSources(method: JcMethod): List<VulnerabilityInstance> {
        val graph = JcSimplifiedGraphFactory().createGraph(cp)
        val points2Engine = JcNaivePoints2EngineFactory.createPoints2Engine(graph)
        val ifds = NPEAnalysisFactory().createAnalysisEngine(graph, points2Engine)
        ifds.addStart(method)
        val result = ifds.analyze()
        return result.foundVulnerabilities.filter { it.vulnerabilityType == NpeAnalyzer.value }
    }
}