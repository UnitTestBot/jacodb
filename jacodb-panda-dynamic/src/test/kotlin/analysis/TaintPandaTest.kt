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
import org.jacodb.analysis.taint.TaintAnalysisOptions
import org.jacodb.analysis.taint.TaintManager
import org.jacodb.analysis.taint.TaintVulnerability
import org.jacodb.analysis.util.PandaTraits
import org.jacodb.panda.dynamic.api.PandaApplicationGraph
import org.jacodb.panda.dynamic.api.PandaApplicationGraphImpl
import org.jacodb.panda.dynamic.api.PandaInst
import org.jacodb.panda.dynamic.api.PandaMethod
import org.jacodb.panda.dynamic.api.PandaProject
import org.jacodb.taint.configuration.Argument
import org.jacodb.taint.configuration.AssignMark
import org.jacodb.taint.configuration.ConstantTrue
import org.jacodb.taint.configuration.ContainsMark
import org.jacodb.taint.configuration.CopyAllMarks
import org.jacodb.taint.configuration.Position
import org.jacodb.taint.configuration.RemoveMark
import org.jacodb.taint.configuration.Result
import org.jacodb.taint.configuration.TaintConfigurationItem
import org.jacodb.taint.configuration.TaintMark
import org.jacodb.taint.configuration.TaintMethodSink
import org.jacodb.taint.configuration.TaintMethodSource
import org.jacodb.taint.configuration.TaintPassThrough
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import parser.loadIr

private val logger = mu.KotlinLogging.logger {}

class TaintPandaTest {

    data class SourceMethodConfig(
        val methodName: String,
        val markName: String = "TAINT",
        val position: Position = Result,
    )

    data class CaseTaintConfig(
        val sourceMethodConfig: SourceMethodConfig,
        val cleanerMethodName: String? = null,
        val sinkMethodName: String? = null,
        val startMethodNamesForAnalysis: List<String>? = null,
    )

    class FileTaintAnalyzer(programName: String) {
        companion object : PandaTraits

        private fun loadProjectForSample(programName: String): PandaProject {
            val parser = loadIr("/samples/${programName}.json")
            val project = parser.getProject()
            return project
        }

        private val project: PandaProject = loadProjectForSample(programName)
        private val graph: PandaApplicationGraph = PandaApplicationGraphImpl(this.project)

//        init {
//            graph.project.classes.flatMap { it.methods }.single { it.name == "forLoop" }.flowGraph().view("dot", "C:\\\\Program Files\\\\Google\\\\Chrome\\\\Application\\\\chrome")
//        }

        fun analyseOneCase(caseTaintConfig: CaseTaintConfig): List<TaintVulnerability<PandaMethod, PandaInst>> {
//            println(TaintAnalysisOptions.UNTRUSTED_LOOP_BOUND_SINK)
            val unitResolver = UnitResolver<PandaMethod> { SingletonUnit }
            val (sourceMethodName, markName, sourcePosition) = caseTaintConfig.sourceMethodConfig
            val getConfigForMethod: ForwardTaintFlowFunctions<PandaMethod, PandaInst>.(PandaMethod) -> List<TaintConfigurationItem>? =
                { method ->
                    val rules = buildList {
                        if (method.name == sourceMethodName) add(
                            TaintMethodSource(
                                method = mockk(),
                                condition = ConstantTrue,
                                actionsAfter = listOf(
                                    AssignMark(mark = TaintMark(markName), position = sourcePosition),
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
                                actionsAfter = List(method.parameters.size) { index ->
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
            val filteredMethods = caseTaintConfig.startMethodNamesForAnalysis?.let { names ->
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
                    sourceMethodConfig = SourceMethodConfig(
                        methodName = "getUserPassword",
                    ),
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
                    sourceMethodConfig = SourceMethodConfig(
                        methodName = "getUserPassword",
                    ),
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
                    sourceMethodConfig = SourceMethodConfig(
                        methodName = "getUserPassword",
                    ),
                    cleanerMethodName = "encryptPassword",
                    sinkMethodName = "log",
                    startMethodNamesForAnalysis = listOf("case2")
                )
            )
            assert(sinks.isEmpty())
        }

    }

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
                    sourceMethodConfig = SourceMethodConfig(
                        methodName = "getUserData",
                        markName = "UNTRUSTED"
                    ),
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
                    sourceMethodConfig = SourceMethodConfig(
                        methodName = "getUserData",
                        markName = "UNTRUSTED"
                    ),
                    startMethodNamesForAnalysis = listOf("doWhileLoop")
                )
            )
            assert(sinks.size == 1)
        }

        @Test
        fun `counterexample - untrusted bound in while loop`() {
            val sinks = fileTaintAnalyzer.analyseOneCase(
                CaseTaintConfig(
                    sourceMethodConfig = SourceMethodConfig(
                        methodName = "getUserData",
                        markName = "UNTRUSTED"
                    ),
                    startMethodNamesForAnalysis = listOf("whileLoop")
                )
            )
            assert(sinks.size == 1)
        }

    }

    @Nested
    inner class UntrustedArraySizeTest {

        private val fileTaintAnalyzer = FileTaintAnalyzer("taintSamples/untrustedArraySize")

        init {
            TaintAnalysisOptions.UNTRUSTED_ARRAY_SIZE_SINK = true
            TaintAnalysisOptions.UNTRUSTED_LOOP_BOUND_SINK = true
        }

        @Test
        fun `counterexample - untrusted size in array constructor and loop bound`() {
            val sinks = fileTaintAnalyzer.analyseOneCase(
                CaseTaintConfig(
                    sourceMethodConfig = SourceMethodConfig(
                        methodName = "getNumber",
                        markName = "UNTRUSTED"
                    ),
                    startMethodNamesForAnalysis = listOf("main")
                )
            )
            assert(sinks.size == 2)
        }

    }

    @Nested
    inner class IndexOutOfRangeTest {

        private val fileTaintAnalyzer = FileTaintAnalyzer("taintSamples/indexOutOfRange")

        init {
            TaintAnalysisOptions.UNTRUSTED_INDEX_ARRAY_ACCESS_SINK = true
        }

        @Disabled("Taint marks don't pass through as expected")
        @Test
        fun `false positive - potential index out of range alert when it's not feasible`() {
            val sinks = fileTaintAnalyzer.analyseOneCase(
                CaseTaintConfig(
                    sourceMethodConfig = SourceMethodConfig(
                        methodName = "readUInt",
                        markName = "UNTRUSTED"
                    ),
                    startMethodNamesForAnalysis = listOf("main")
                )
            )
            assert(sinks.size == 2)
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
                    sourceMethodConfig = SourceMethodConfig(
                        methodName = "getNameOption",
                    ),
                    sinkMethodName = "log"
                )
            )
            assert(sinks.size == 2)
        }
    }

    @Nested
    inner class LoopBoundInjectionUseCaseTest {
        private val fileTaintAnalyzer = FileTaintAnalyzer("codeqlSamples/loopBoundInjection")

        init {
            TaintAnalysisOptions.UNTRUSTED_LOOP_BOUND_SINK = true
        }

        val postHandlerMethodName = "#4722804945120678178#"

        @Disabled("There is no ability to mark method argument as tainted")
        @Test
        fun `counterexample - loop bound injection (could possibly lead to DoS if length is large)`() {
            val sinks = fileTaintAnalyzer.analyseOneCase(
                CaseTaintConfig(
                    sourceMethodConfig = SourceMethodConfig(
                        methodName = postHandlerMethodName,
                        markName = "UNTRUSTED",
                        position = Argument(0)
                    ),
                    startMethodNamesForAnalysis = listOf(postHandlerMethodName)
                )
            )
            assert(sinks.size == 1)
        }
    }
}
