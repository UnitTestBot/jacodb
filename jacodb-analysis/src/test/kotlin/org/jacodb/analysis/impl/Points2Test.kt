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
import org.jacodb.analysis.TaintAnalysisFactory
import org.jacodb.analysis.analyzers.TaintAnalysisNode
import org.jacodb.analysis.graph.JcApplicationGraphImpl
import org.jacodb.analysis.graph.SimplifiedJcApplicationGraph
import org.jacodb.analysis.paths.toPath
import org.jacodb.analysis.points2.AllOverridesDevirtualizer
import org.jacodb.api.JcMethod
import org.jacodb.api.cfg.JcAssignInst
import org.jacodb.api.ext.cfg.callExpr
import org.jacodb.api.ext.findClass
import org.jacodb.impl.features.InMemoryHierarchy
import org.jacodb.impl.features.Usages
import org.jacodb.impl.features.usagesExt
import org.jacodb.testing.BaseTest
import org.jacodb.testing.WithDB
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.io.File
import java.util.stream.Stream
import kotlin.streams.asStream

class Points2Test : BaseTest() {
    companion object : WithDB(Usages, InMemoryHierarchy) {

        @JvmStatic
        fun provideForPointerBenchBasic(): Stream<Arguments> = listOf(
            Arguments.of("Branching1", listOf("38"), listOf("35")),
            Arguments.of("Interprocedural1", listOf("48", "49"), listOf("41", "42")),
            Arguments.of("Interprocedural2", listOf("51", "52"), listOf("43", "44", "58")),
            Arguments.of("Parameter1", listOf("35", "arg$0"), emptyList<String>()),
            Arguments.of("Parameter2", listOf("37", "arg$0"), emptyList<String>()),
            Arguments.of("ReturnValue1", listOf("41", "42"), emptyList<String>()),
            Arguments.of("ReturnValue2", listOf("43", "45"), listOf("44")),
            Arguments.of("ReturnValue3", listOf("46"), listOf("44", "45", "47")),
            Arguments.of("SimpleAlias1", listOf("37", "38"), emptyList<String>())
            // Loops1 Loops2 and Recursion1 are not tested because it is difficult to represent them as taint problem
        )
            .asSequence()
            .asStream()

        @JvmStatic
        fun provideForPointerBenchGeneralJava(): Stream<Arguments> = listOf(
            Arguments.of("Exception1", listOf("37", "38"), emptyList<String>()),

            // Null1 and Null2 are not tested because it is difficult to represent them as taint problems
            // Exception2 isn't tested because it needs analysis for possibly thrown exceptions

            Arguments.of("Interface1", listOf("40", "45"), listOf("38", "42", "43")),
            Arguments.of("OuterClass1", listOf("55", "51"), listOf("50", "53")),
            Arguments.of("StaticVariables1", listOf("39", "42", "StaticVariables1.a"), emptyList<String>()),
            Arguments.of("SuperClasses1", listOf("38", "42"), listOf("37", "40")),
        )
            .asSequence()
            .asStream()

        @JvmStatic
        fun provideForPointerBenchCornerCases(): Stream<Arguments> = listOf(
            Arguments.of("AccessPath1", listOf("38.f", "39.f"), listOf("38", "39")),
            Arguments.of("ContextSensitivity1", listOf("arg$0", "arg$1"), emptyList<String>()),
            Arguments.of("ContextSensitivity2", listOf("arg$0", "arg$1"), emptyList<String>()),
            Arguments.of("ContextSensitivity3", listOf("arg$0", "arg$1"), emptyList<String>()),
            Arguments.of("FieldSensitivity1", listOf("42", "46"), listOf("43", "44")),
            Arguments.of("FieldSensitivity2", listOf("43", "47"), listOf("44", "45")),
            Arguments.of("ObjectSensitivity1", listOf("39", "45"), listOf("37", "41", "42", "44")),
            Arguments.of("ObjectSensitivity2", listOf("39", "44"), listOf("37", "41", "43")),
            Arguments.of("StrongUpdate1", listOf("43", "44"), listOf("37", "38")),
            Arguments.of("StrongUpdate2", listOf("44"), listOf("41")),
        )
            .asSequence()
            .asStream()
    }

    @ParameterizedTest
    @MethodSource("provideForPointerBenchBasic")
    fun testBasic(className: String, must: List<String>, notMay: List<String>) {
        testPointerBench("pointerbench.basic.$className", must, notMay)
    }


    @ParameterizedTest
    @MethodSource("provideForPointerBenchGeneralJava")
    fun testGeneralJava(className: String, must: List<String>, notMay: List<String>) {
        testPointerBench("pointerbench.generalJava.$className", must, notMay)
    }

    @ParameterizedTest
    @MethodSource("provideForPointerBenchCornerCases")
    fun testCornerCases(className: String, must: List<String>, notMay: List<String>) {
        testPointerBench("pointerbench.cornerCases.$className", must, notMay)
    }

    private fun testPointerBench(className: String, must: List<String>, notMay: List<String>) {
        val main = cp.findClass(className).declaredMethods.single { it.name == "main" }

        val res = findTaints(main)
        println("For $className:\nOur result: $res\nmust: $must\nnotMay: $notMay")

        val notFoundFromMust = must.filter { it !in res }
        assertEquals(emptyList<String>(), notFoundFromMust)

        val foundFromNotMay = notMay.filter { it in res }
        assertEquals(emptyList<String>(), foundFromNotMay)
    }

    private fun findTaints(method: JcMethod): List<String> {
        val graph = runBlocking {
            SimplifiedJcApplicationGraph(
                JcApplicationGraphImpl(cp, cp.usagesExt()),
                SimplifiedJcApplicationGraph.defaultBannedPackagePrefixes +
                        listOf("pointerbench.benchmark.internal")
            )
        }
        val all = AllOverridesDevirtualizer(graph, cp)
        val factory = TaintAnalysisFactory {
            if (it is JcAssignInst &&
                it.callExpr?.method?.name == "taint" &&
                it.callExpr?.method?.method?.enclosingClass?.simpleName == "Benchmark"
            ) {
                listOf(TaintAnalysisNode(it.lhv.toPath()))
            } else {
                emptyList()
            }
        }
        val ifds = factory.createAnalysisEngine(graph, all, File("a"))
        ifds.addStart(method)
        val result = ifds.analyze()
        return result.foundVulnerabilities.map { it.sink }
    }
}