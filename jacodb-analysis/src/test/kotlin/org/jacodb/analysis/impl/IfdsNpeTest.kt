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
import org.jacodb.analysis.graph.JcApplicationGraphImpl
import org.jacodb.analysis.ifds.SingletonUnitResolver
import org.jacodb.analysis.npe.NpeManager
import org.jacodb.analysis.taint.TaintVulnerability
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.cfg.JcInst
import org.jacodb.api.jvm.ext.constructors
import org.jacodb.api.jvm.ext.findClass
import org.jacodb.impl.features.InMemoryHierarchy
import org.jacodb.impl.features.Usages
import org.jacodb.impl.features.usagesExt
import org.jacodb.testing.WithDB
import org.jacodb.testing.WithRAMDB
import org.jacodb.testing.analysis.NpeExamples
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.StringTokenizer
import java.util.stream.Stream
import kotlin.time.Duration.Companion.seconds

private val logger = mu.KotlinLogging.logger {}

abstract class IfdsNpeTest : BaseAnalysisTest() {

    companion object {
        @JvmStatic
        fun provideClassesForJuliet476(): Stream<Arguments> =
            provideClassesForJuliet(476, listOf("null_check_after_deref"))

        @JvmStatic
        fun provideClassesForJuliet690(): Stream<Arguments> =
            provideClassesForJuliet(690)
    }

    @Test
    fun `fields resolving should work through interfaces`() = runBlocking {
        val graph = JcApplicationGraphImpl(cp, cp.usagesExt())
        val callers = graph.callers(cp.findClass<StringTokenizer>().constructors[2])
        logger.debug { "callers: ${callers.toList().size}" }
    }

    @Test
    fun `analyze simple NPE`() {
        testOneMethod<NpeExamples>("npeOnLength", listOf("%3 = x.length()"))
    }

    @Test
    fun `analyze no NPE`() {
        testOneMethod<NpeExamples>("noNPE", emptyList())
    }

    @Test
    fun `analyze NPE after fun with two exits`() {
        testOneMethod<NpeExamples>(
            "npeAfterTwoExits",
            listOf("%4 = x.length()", "%5 = y.length()")
        )
    }

    @Test
    fun `no NPE after checked access`() {
        testOneMethod<NpeExamples>("checkedAccess", emptyList())
    }

    @Disabled("Aliasing")
    @Test
    fun `no NPE after checked access with field`() {
        testOneMethod<NpeExamples>("checkedAccessWithField", emptyList())
    }

    @Test
    fun `consecutive NPEs handled properly`() {
        testOneMethod<NpeExamples>(
            "consecutiveNPEs",
            listOf("a = x.length()", "c = x.length()")
        )
    }

    @Test
    fun `npe on virtual call when possible`() {
        testOneMethod<NpeExamples>(
            "possibleNPEOnVirtualCall",
            listOf("%0 = x.length()")
        )
    }

    @Test
    fun `no npe on virtual call when impossible`() {
        testOneMethod<NpeExamples>(
            "noNPEOnVirtualCall",
            emptyList()
        )
    }

    @Test
    fun `basic test for NPE on fields`() {
        testOneMethod<NpeExamples>("simpleNPEOnField", listOf("len2 = second.length()"))
    }

    @Disabled("Flowdroid architecture not supported for async ifds yet")
    @Test
    fun `simple points-to analysis`() {
        testOneMethod<NpeExamples>("simplePoints2", listOf("%5 = %4.length()"))
    }

    @Disabled("Flowdroid architecture not supported for async ifds yet")
    @Test
    fun `complex aliasing`() {
        testOneMethod<NpeExamples>("complexAliasing", listOf("%6 = %5.length()"))
    }

    @Disabled("Flowdroid architecture not supported for async ifds yet")
    @Test
    fun `context injection in points-to`() {
        testOneMethod<NpeExamples>(
            "contextInjection",
            listOf("%6 = %5.length()", "%3 = %2.length()")
        )
    }

    @Disabled("Flowdroid architecture not supported for async ifds yet")
    @Test
    fun `activation points maintain flow sensitivity`() {
        testOneMethod<NpeExamples>("flowSensitive", listOf("%8 = %7.length()"))
    }

    @Test
    fun `overridden null assignment in callee don't affect next caller's instructions`() {
        testOneMethod<NpeExamples>("overriddenNullInCallee", emptyList())
    }

    @Test
    fun `recursive classes handled correctly`() {
        testOneMethod<NpeExamples>(
            "recursiveClass",
            listOf("%10 = %9.toString()", "%15 = %14.toString()")
        )
    }

    @Test
    fun `NPE on uninitialized array element dereferencing`() {
        testOneMethod<NpeExamples>("simpleArrayNPE", listOf("b = %4.length()"))
    }

    @Test
    fun `no NPE on array element dereferencing after initialization`() {
        testOneMethod<NpeExamples>("noNPEAfterArrayInit", emptyList())
    }

    @Disabled("Flowdroid architecture not supported for async ifds yet")
    @Test
    fun `array aliasing`() {
        testOneMethod<NpeExamples>("arrayAliasing", listOf("%5 = %4.length()"))
    }

    @Disabled("Flowdroid architecture not supported for async ifds yet")
    @Test
    fun `mixed array and class aliasing`() {
        testOneMethod<NpeExamples>("mixedArrayClassAliasing", listOf("%13 = %12.length()"))
    }

    @Test
    fun `dereferencing field of null object`() {
        testOneMethod<NpeExamples>("npeOnFieldDeref", listOf("s = a.field"))
    }

    @Test
    fun `dereferencing copy of value saved before null assignment produce no npe`() {
        testOneMethod<NpeExamples>("copyBeforeNullAssignment", emptyList())
    }

    @Test
    fun `assigning null to copy doesn't affect original value`() {
        testOneMethod<NpeExamples>("nullAssignmentToCopy", emptyList())
    }

    private fun findSinks(method: JcMethod): List<TaintVulnerability<JcInst>> {
        val unitResolver = SingletonUnitResolver
        val manager = NpeManager(graph, unitResolver)
        return manager.analyze(listOf(method), timeout = 30.seconds)
    }

    @ParameterizedTest
    @MethodSource("provideClassesForJuliet476")
    fun `test on Juliet's CWE 476`(className: String) {
        testSingleJulietClass(className, ::findSinks)
    }

    @ParameterizedTest
    @MethodSource("provideClassesForJuliet690")
    fun `test on Juliet's CWE 690`(className: String) {
        testSingleJulietClass(className, ::findSinks)
    }

    @Test
    fun `test on specific Juliet's testcase`() {
        // val className = "juliet.testcases.CWE476_NULL_Pointer_Dereference.CWE476_NULL_Pointer_Dereference__Integer_01"
        // val className = "juliet.testcases.CWE690_NULL_Deref_From_Return.CWE690_NULL_Deref_From_Return__Class_StringBuilder_01"
        val className =
            "juliet.testcases.CWE690_NULL_Deref_From_Return.CWE690_NULL_Deref_From_Return__Properties_getProperty_equals_01"

        testSingleJulietClass(className, ::findSinks)
    }

    private inline fun <reified T> testOneMethod(
        methodName: String,
        expectedLocations: Collection<String>,
    ) {
        val method = cp.findClass<T>().declaredMethods.single { it.name == methodName }
        val sinks = findSinks(method)

        // TODO: think about better assertions here
        Assertions.assertEquals(expectedLocations.size, sinks.size)
        expectedLocations.forEach { expected ->
            Assertions.assertTrue(sinks.map { it.sink.toString() }.any { it.contains(expected) })
        }
    }
}

class IfdsNpeSqlTest : IfdsNpeTest() {
    companion object : WithDB(Usages, InMemoryHierarchy)
}

class IfdsNpeRAMTest : IfdsNpeTest() {
    companion object : WithRAMDB(Usages, InMemoryHierarchy)
}