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
import org.jacodb.analysis.graph.JcApplicationGraphImpl
import org.jacodb.analysis.graph.newApplicationGraphForAnalysis
import org.jacodb.analysis.library.JcSingletonUnitResolver
import org.jacodb.analysis.library.analyzers.JcNpeAnalyzer
import org.jacodb.analysis.library.newJcNpeRunnerFactory
import org.jacodb.analysis.runAnalysis
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.cfg.JcInst
import org.jacodb.api.jvm.cfg.JcInstLocation
import org.jacodb.api.jvm.ext.constructors
import org.jacodb.api.jvm.ext.findClass
import org.jacodb.impl.features.usagesExt
import org.jacodb.testing.analysis.NpeExamples
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.*
import java.util.stream.Stream

class NpeAnalysisTest : BaseAnalysisTest() {
    companion object {
        @JvmStatic
        fun provideClassesForJuliet476(): Stream<Arguments> =
            provideClassesForJuliet(476, listOf("null_check_after_deref"))

        @JvmStatic
        fun provideClassesForJuliet690(): Stream<Arguments> =
            provideClassesForJuliet(690)

        private const val vulnerabilityType = JcNpeAnalyzer.ruleId
    }

    @Test
    fun `fields resolving should work through interfaces`() = runBlocking {
        val graph = JcApplicationGraphImpl(cp, cp.usagesExt())
        val callers = graph.callers(cp.findClass<StringTokenizer>().constructors[2])
        println(callers.toList().size)
    }

    @Test
    fun `analyze simple NPE`() {
        testOneMethod<NpeExamples>("npeOnLength", listOf("%3 = %0.length()"))
    }

    @Test
    fun `analyze no NPE`() {
        testOneMethod<NpeExamples>("noNPE", emptyList())
    }

    @Test
    fun `analyze NPE after fun with two exits`() {
        testOneMethod<NpeExamples>(
            "npeAfterTwoExits",
            listOf("%4 = %0.length()", "%5 = %1.length()")
        )
    }

    @Test
    fun `no NPE after checked access`() {
        testOneMethod<NpeExamples>("checkedAccess", emptyList())
    }

    @Test
    fun `consecutive NPEs handled properly`() {
        testOneMethod<NpeExamples>(
            "consecutiveNPEs",
            listOf("%2 = arg$0.length()", "%4 = arg$0.length()")
        )
    }

    @Test
    fun `npe on virtual call when possible`() {
        testOneMethod<NpeExamples>(
            "possibleNPEOnVirtualCall",
            listOf("%0 = arg\$0.length()")
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
        testOneMethod<NpeExamples>("simpleNPEOnField", listOf("%8 = %6.length()"))
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
        testOneMethod<NpeExamples>("simpleArrayNPE", listOf("%5 = %4.length()"))
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
        testOneMethod<NpeExamples>("npeOnFieldDeref", listOf("%1 = %0.field"))
    }

    @Test
    fun `dereferencing copy of value saved before null assignment produce no npe`() {
        testOneMethod<NpeExamples>("copyBeforeNullAssignment", emptyList())
    }

    @Test
    fun `assigning null to copy doesn't affect original value`() {
        testOneMethod<NpeExamples>("nullAssignmentToCopy", emptyList())
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

    @Test
    fun `analyse something`() {
        val testingMethod = cp.findClass<NpeExamples>().declaredMethods.single { it.name == "id" }
        val results = testingMethod.flowGraph()
        print(results)
    }

    private fun testJuliet(className: String) = testSingleJulietClass(vulnerabilityType, className)

    private inline fun <reified T> testOneMethod(methodName: String, expectedLocations: Collection<String>) =
        testOneAnalysisOnOneMethod<T>(vulnerabilityType, methodName, expectedLocations)

    override fun launchAnalysis(methods: List<JcMethod>): List<VulnerabilityInstance<JcMethod, JcInstLocation, JcInst>> {
        val graph = runBlocking {
            cp.newApplicationGraphForAnalysis()
        }
        return runAnalysis(graph, JcSingletonUnitResolver, newJcNpeRunnerFactory(), methods)
    }
}