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

package analysis

import io.mockk.mockk
import org.jacodb.analysis.ifds.SingletonUnit
import org.jacodb.analysis.ifds.UnitResolver
import org.jacodb.analysis.taint.ForwardTaintFlowFunctions
import org.jacodb.analysis.taint.TaintManager
import org.jacodb.analysis.taint.TaintVulnerability
import org.jacodb.analysis.util.PandaTraits
import org.jacodb.panda.dynamic.api.PandaApplicationGraphImpl
import org.jacodb.panda.dynamic.api.PandaInst
import org.jacodb.panda.dynamic.api.PandaMethod
import org.junit.jupiter.api.Test
import org.jacodb.panda.dynamic.api.*
import org.jacodb.analysis.taint.TaintAnalysisOptions
import org.jacodb.panda.dynamic.parser.IRParser
import org.jacodb.panda.dynamic.parser.ProgramBasicBlock
import org.jacodb.taint.configuration.*
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Nested
import parser.loadIr

private val logger = mu.KotlinLogging.logger {}

class TaintPandaTest {
    data class CaseTaintConfig(
        val sourceMethodAndMarkNames: Pair<String, String>,
        val cleanerMethodName: String? = null,
        val sinkMethodName: String? = null,
        val startMethodNamesForAnalysis: List<String>? = null
    )

    class FileTaintAnalyzer(programName: String) {
        companion object : PandaTraits

        private fun loadProjectForSample(programName: String): PandaProject {
            val parser = loadIr("/samples/${programName}.json")
            val project = parser.getProject()
            return project
        }

        private val project : PandaProject = loadProjectForSample(programName)
        private val graph : PandaApplicationGraph = PandaApplicationGraphImpl(this.project)

        fun analyseOneCase(caseTaintConfig: CaseTaintConfig) : List<TaintVulnerability<PandaMethod, PandaInst>> {
            println(TaintAnalysisOptions.UNTRUSTED_LOOP_BOUND_SINK)
            val unitResolver = UnitResolver<PandaMethod> { SingletonUnit }
            val (sourceMethodName, markName) = caseTaintConfig.sourceMethodAndMarkNames
            val getConfigForMethod: ForwardTaintFlowFunctions<PandaMethod, PandaInst>.(PandaMethod) -> List<TaintConfigurationItem>? =
                { method ->
                    val rules = buildList {
                        if (method.name == sourceMethodName) add(
                            TaintMethodSource(
                                method = mockk(),
                                condition = ConstantTrue,
                                actionsAfter = listOf(
                                    AssignMark(mark = TaintMark(markName), position = Result),
                                ),
                            )
                        )
                        else if (method.name == caseTaintConfig.cleanerMethodName) add(
                            TaintPassThrough(
                                method = mockk(),
                                condition = ConstantTrue,
                                actionsAfter = listOf(
                                    RemoveMark(mark = TaintMark(markName), position = Argument(0))
                                ),
                            )
                        )
                        else if (method.name == caseTaintConfig.sinkMethodName) add(
                            TaintMethodSink(
                                method = mockk(), ruleNote = "CUSTOM SINK", // FIXME
                                cwe = listOf(), // FIXME
                                condition = ContainsMark(position = Argument(0), mark = TaintMark(markName))
                            )
                        )
                        // TODO(): generalize semantic
                        else add(
                            TaintPassThrough(
                                method = mockk(),
                                condition = ConstantTrue,
                                actionsAfter = List(method.parameters.size) {index ->
                                    CopyAllMarks(from = Argument(index), to = Result)
                                }
                            )
                        )
                    }
                    rules.ifEmpty { null }
                }
            val manager = TaintManager(
                graph = graph,
                unitResolver = unitResolver,
                getConfigForMethod = getConfigForMethod,
            )

            val methods = this.project.classes.flatMap { it.methods }
            val filteredMethods = caseTaintConfig.startMethodNamesForAnalysis?.let {names ->
                methods.filter { method -> names.contains(method.name) }
            } ?: methods


            logger.info { "Methods: ${filteredMethods.size}" }
            for (method in filteredMethods) {
                logger.info { "  ${method.name}" }
            }
            val sinks = manager.analyze(filteredMethods)
            logger.info { "Sinks: $sinks" }
            return sinks
        }
    }



    @Nested
    inner class PasswordLeakTest {

        private val fileTaintAnalyzer = FileTaintAnalyzer("taintSamples/passwordLeak")

        @Test
        fun `counterexample - print unencrypted password to console`() {
            val sinks = fileTaintAnalyzer.analyseOneCase(
                CaseTaintConfig(
                    sourceMethodAndMarkNames = Pair("getUserPassword", "TAINT"),
                    sinkMethodName = "log",
                    startMethodNamesForAnalysis = listOf("case1")
                )
            )
            assert(sinks.size == 1)
        }

        @Test
        fun `positive example - print encrypted password to console (with forgotten cleaner)`() {
            val sinks = fileTaintAnalyzer.analyseOneCase(
                CaseTaintConfig(
                    sourceMethodAndMarkNames = Pair("getUserPassword", "TAINT"),
                    sinkMethodName = "log",
                    startMethodNamesForAnalysis = listOf("case2")
                )
            )
            assert(sinks.size == 1)
        }

        @Disabled("Cleaner config don't work as expected")
        @Test
        fun `positive example - print encrypted password to console`() {
            val sinks = fileTaintAnalyzer.analyseOneCase(
                CaseTaintConfig(
                    sourceMethodAndMarkNames = Pair("getUserPassword", "TAINT"),
                    cleanerMethodName = "encryptPassword",
                    sinkMethodName = "log",
                    startMethodNamesForAnalysis = listOf("case2")
                )
            )
            assert(sinks.isEmpty())
        }

    }

    @Disabled("UNTRUSTED_LOOP_BOUND_SINK handler for dynamic panda is not implemented yet")
    @Nested
    inner class UntrustedLoopBoundTest {

        private val fileTaintAnalyzer = FileTaintAnalyzer("taintSamples/untrustedLoopBound")

        init {
            TaintAnalysisOptions.UNTRUSTED_LOOP_BOUND_SINK = true
        }

        @Test
        fun `counterexample - untrusted bound in for loop`() {
            val sinks = fileTaintAnalyzer.analyseOneCase(
                CaseTaintConfig(
                    sourceMethodAndMarkNames = Pair("getUserData", "UNTRUSTED"),
                    startMethodNamesForAnalysis = listOf("forLoop")
                )
            )
            assert(sinks.size == 1)
        }

        @Disabled("Loop do while is not supported yet")
        @Test
        fun `counterexample - untrusted bound in do while loop`() {
            val sinks = fileTaintAnalyzer.analyseOneCase(
                CaseTaintConfig(
                    sourceMethodAndMarkNames = Pair("getUserData", "UNTRUSTED"),
                    startMethodNamesForAnalysis = listOf("doWhileLoop")
                )
            )
            assert(sinks.size == 1)
        }


        @Test
        fun `counterexample - untrusted bound in while loop`() {
            val sinks = fileTaintAnalyzer.analyseOneCase(
                CaseTaintConfig(
                    sourceMethodAndMarkNames = Pair("getUserData", "UNTRUSTED"),
                    startMethodNamesForAnalysis = listOf("whileLoop")
                )
            )
            assert(sinks.size == 1)
        }


    }
    @Nested
    inner class FieldSampleTest {
        private val fileTaintAnalyzer = FileTaintAnalyzer("taintSamples/fieldSample")

        @Disabled("IFDS do not work properly with virtual methods")
        @Test
        fun `test taint analysis on fieldSample`() {
            val sinks = fileTaintAnalyzer.analyseOneCase(
                CaseTaintConfig(
                    sourceMethodAndMarkNames = Pair("getNameOption", "TAINT"),
                    sinkMethodName = "log"
                )
            )
            assert(sinks.size == 2)
        }
    }
}

