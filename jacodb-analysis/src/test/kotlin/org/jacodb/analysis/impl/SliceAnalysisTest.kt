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

import ArgWriteSlice
import FieldWriteSlice
import IfReturnSlice
import IfSlice
import ReturnSlice
import SQL_Injection_01
import SQL_Injection_02
import SQL_Injection_03
import SliceExample
import WhileSlice
import WhileTrueSlice
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import org.jacodb.analysis.engine.IfdsUnitRunnerFactory
import org.jacodb.analysis.engine.UnitResolver
import org.jacodb.analysis.engine.VulnerabilityInstance
import org.jacodb.analysis.graph.newApplicationGraphForAnalysis
import org.jacodb.analysis.graph.reversed
import org.jacodb.analysis.library.*
import org.jacodb.analysis.library.analyzers.SliceTaintAnalyzer
import org.jacodb.analysis.runAnalysis
import org.jacodb.analysis.sarif.sarifReportFromVulnerabilities
import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcMethod
import org.jacodb.api.ext.findClass
import org.jacodb.api.ext.methods
import org.jacodb.impl.features.InMemoryHierarchy
import org.jacodb.impl.features.Usages
import org.jacodb.testing.WithDB
import org.joda.time.DateTime
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class SliceAnalysisTest : BaseAnalysisTest() {
    companion object : WithDB(Usages, InMemoryHierarchy) {
        @JvmStatic
        fun provideClassesForJuliet89(): Stream<Arguments> = provideClassesForJuliet(89, emptyList())
    }

    private inline fun <UnitType> testOne(clazz: JcClassOrInterface, unitResolver: UnitResolver<UnitType>, ifdsUnitRunnerFactory: IfdsUnitRunnerFactory) {
        val result = runAnalysis(graph.reversed, unitResolver, ifdsUnitRunnerFactory, clazz.declaredMethods)
            .sortedBy { it.vulnerabilityDescription.message.substringAfterLast("at location ") }

        println("Vulnerabilities found: ${result.size}")
        println("Generated report:")
        val json = Json {
            prettyPrint = true
            encodeDefaults = false
        }
        json.encodeToStream(sarifReportFromVulnerabilities(result), System.out)

        result.forEach {
            println(it.vulnerabilityDescription.message)
        }
    }

    @Test
    fun `test field writing slice`() {
        val clazz = cp.findClass<FieldWriteSlice>()
        val sinks = setOf(clazz.declaredMethods.get(1).instList.get(9))
        testOne(clazz, SingletonUnitResolver, newSliceTaintRunnerFactory(sinks))
    }

    @Test
    fun `test return slice`() {
        val clazz = cp.findClass<ReturnSlice>()
        val sinks = setOf(clazz.declaredMethods.get(1).instList.get(4))
        testOne(clazz, SingletonUnitResolver, newSliceTaintRunnerFactory(sinks))
    }

    @Test
    fun `test example slice`() {
        val clazz = cp.findClass<SliceExample>()
        val sinks = setOf(clazz.declaredMethods.get(3).instList.get(15))
        testOne(clazz, SingletonUnitResolver, newSliceTaintRunnerFactory(sinks))
    }

    @Test
    fun `test arg writing slice`() {
        val clazz = cp.findClass<ArgWriteSlice>()
        val sinks = setOf(clazz.declaredMethods.get(1).instList.get(4))
        testOne(clazz, SingletonUnitResolver, newSliceTaintRunnerFactory(sinks))
    }

    @Test
    fun `test while slice`() {
        val clazz = cp.findClass<WhileSlice>()
        val sinks = setOf(clazz.declaredMethods.get(1).instList.get(14))
        testOne(clazz, SingletonUnitResolver, newSliceTaintRunnerFactory(sinks))
    }

    @Test
    fun `test if slice`() {
        val clazz = cp.findClass<IfSlice>()
        val sinks = setOf(clazz.declaredMethods.get(1).instList.get(8))
        testOne(clazz, SingletonUnitResolver, newSliceTaintRunnerFactory(sinks))
    }

    @Test
    fun `test if return slice`() {
        val clazz = cp.findClass<IfReturnSlice>()
        val sinks = setOf(clazz.declaredMethods.get(1).instList.get(14))
        testOne(clazz, SingletonUnitResolver, newSliceTaintRunnerFactory(sinks))
    }

    @Test
    fun `test while true slice`() {
        val clazz = cp.findClass<WhileTrueSlice>()
        val sinks = setOf(clazz.declaredMethods.get(1).instList.get(12))
        testOne(clazz, SingletonUnitResolver, newSliceTaintRunnerFactory(sinks))
    }

    @Test
    fun `test sql injection 01`() {
        val clazz = cp.findClass<SQL_Injection_01>()
        val sinks = setOf(clazz.declaredMethods.get(1).instList.get(32))
        testOne(clazz, SingletonUnitResolver, newSliceTaintRunnerFactory(sinks))
    }

    @Test
    fun `test sql injection 02`() {
        val clazz = cp.findClass<SQL_Injection_02>()
        val sinks = setOf(clazz.declaredMethods.get(1).instList.get(31))
        testOne(clazz, SingletonUnitResolver, newSliceTaintRunnerFactory(sinks))
    }

    @Test
    fun `test sql injection 03`() {
        val clazz = cp.findClass<SQL_Injection_03>()
        val sinks = setOf(clazz.declaredMethods.get(1).instList.get(25))
        testOne(clazz, SingletonUnitResolver, newSliceTaintRunnerFactory(sinks))
    }

    @Test
    fun `test npe JodaTime`() {
        val clazz = cp.findClass<DateTime>()
        runAnalysis(graph, MethodUnitResolver, newNpeRunnerFactory(), clazz.declaredMethods).forEach {
            val result = runAnalysis(
                graph.reversed, MethodUnitResolver,
                newSliceTaintRunnerFactory(setOf(it.traceGraph.sink.statement)), clazz.declaredMethods
            )
                .filter { it.vulnerabilityDescription.ruleId == SliceTaintAnalyzer.modifiedId }

            println("Important instructions for sink ${it.traceGraph.sink.statement} found: ${result.size}")
            println("Generated report:")
            for (vulnerability : VulnerabilityInstance in result) {
                println(vulnerability.vulnerabilityDescription.message)
            }
        }
    }

    @ParameterizedTest
    @MethodSource("provideClassesForJuliet89")
    fun `test on Juliet's CWE 89`(className: String) {
        val clazz = cp.findClass(className)

        val goodMethod = clazz.methods.single { it.name == "good" }
        val badMethod = clazz.methods.single { it.name == "bad" }

        launchAnalysis(listOf(goodMethod))
        launchAnalysis(listOf(badMethod))
    }

    override fun launchAnalysis(methods: List<JcMethod>): List<VulnerabilityInstance> {
        val graph = runBlocking {
            cp.newApplicationGraphForAnalysis()
        }
        return buildList {
            runAnalysis(graph, SingletonUnitResolver, newSqlInjectionRunnerFactory(), methods).forEach {
                val result = runAnalysis(
                    graph.reversed, SingletonUnitResolver,
                    newSliceTaintRunnerFactory(setOf(it.traceGraph.sink.statement)), methods
                )
                    .filter { it.vulnerabilityDescription.ruleId == SliceTaintAnalyzer.modifiedId }

                println("Important instructions for sink ${it.traceGraph.sink.statement} found: ${result.size}")
                println("Generated report:")
                for (vulnerability : VulnerabilityInstance in result) {
                    println(vulnerability.vulnerabilityDescription.message)
                }
                addAll(result)
            }
        }
    }

    private val graph = runBlocking {
        cp.newApplicationGraphForAnalysis()
    }
}