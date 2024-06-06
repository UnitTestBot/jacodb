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

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
import org.jacodb.taint.configuration.*
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import parser.loadCaseTaintConfig
import parser.loadIr
import java.io.File
import kotlin.time.Duration.Companion.seconds

private val logger = mu.KotlinLogging.logger {}

class TaintSamples {

    @Serializable
    data class SourceMethodConfig(
        val methodName: String?,
        val markName: String = "TAINT",
        val position: Position = Result,
    )

    @Serializable
    data class CleanerMethodConfig(
        val methodName: String?,
        val markName: String = "TAINT",
        val position: Position = Result,
    )

    @Serializable
    data class SinkMethodConfig(
        val methodName: String?,
        val markName: String = "TAINT",
        val position: Position = if (methodName == "log") AnyArgument else Argument(0),
    )

    @Serializable
    sealed interface TaintBuiltInOption

    @Serializable
    @SerialName("UNTRUSTED_LOOP_BOUND_SINK_CHECK")
    object UntrustedLoopBoundSinkCheck : TaintBuiltInOption

    @Serializable
    @SerialName("UNTRUSTED_ARRAY_SIZE_SINK_CHECK")
    object UntrustedArraySizeSinkCheck : TaintBuiltInOption

    @Serializable
    @SerialName("UNTRUSTED_INDEX_ARRAY_ACCESS_SINK_CHECK")
    object UntrustedIndexArrayAccessSinkCheck : TaintBuiltInOption

    @Serializable
    data class CaseTaintConfig(
        val sourceMethodConfigs: List<SourceMethodConfig> = listOf(),
        val cleanerMethodConfigs: List<CleanerMethodConfig> = listOf(),
        val sinkMethodConfigs: List<SinkMethodConfig> = listOf(),
        val startMethodNamesForAnalysis: List<String>? = null,
        val builtInOptions: List<TaintBuiltInOption> = listOf(),
    )

    data class SinkResult(
        val sink: TaintVulnerability<PandaMethod, PandaInst>,
        val trace: List<PandaInst>? = null
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

        // init {
        //     graph.project.classes.flatMap { it.methods }.single { it.name == "forLoop" }.flowGraph()
        //         .view("dot", "C:\\\\Program Files\\\\Google\\\\Chrome\\\\Application\\\\chrome")
        // }

        fun analyseOneCase(caseTaintConfig: CaseTaintConfig, withTrace: Boolean = false): List<SinkResult> {
            val unitResolver = UnitResolver<PandaMethod> { SingletonUnit }
            val getConfigForMethod: ForwardTaintFlowFunctions<PandaMethod, PandaInst>.(PandaMethod) -> List<TaintConfigurationItem>? =
                { method ->
                    val rules = buildList {
                        for (sourceConfig in caseTaintConfig.sourceMethodConfigs) {
                            val (sourceMethodName, markName, sourcePosition) = sourceConfig
                            if (method.name == sourceMethodName) {
                                add(
                                    TaintMethodSource(
                                        method = method,
                                        condition = ConstantTrue,
                                        actionsAfter = listOf(
                                            AssignMark(mark = TaintMark(markName), position = sourcePosition),
                                        ),
                                    )
                                )
                                return@buildList
                            }
                        }
                        for (cleanerConfig in caseTaintConfig.cleanerMethodConfigs) {
                            val (cleanerMethodName, markName, cleanerPosition) = cleanerConfig
                            if (method.name == cleanerMethodName) {
                                add(
                                    TaintPassThrough(
                                        method = method,
                                        condition = ConstantTrue,
                                        actionsAfter = listOf(
                                            RemoveMark(mark = TaintMark(markName), position = cleanerPosition)
                                        ),
                                    )
                                )
                                return@buildList
                            }
                        }
                        for (sinkConfig in caseTaintConfig.sinkMethodConfigs) {
                            val (sinkMethodName, markName, sinkPosition) = sinkConfig
                            if (method.name == sinkMethodName) {
                                add(
                                    TaintMethodSink(
                                        method = method,
                                        ruleNote = "CUSTOM SINK", // FIXME
                                        cwe = listOf(), // FIXME
                                        condition = when(sinkPosition) {
                                            AnyArgument -> Or(List(method.parameters.size) {
                                                ContainsMark(position = Argument(it), mark = TaintMark(markName))
                                            })
                                            else -> ContainsMark(position = sinkPosition, mark = TaintMark(markName))
                                        }
                                    )
                                )
                                return@buildList
                            }
                        }
                        // TODO(): generalize semantic
                        add(
                            TaintPassThrough(
                                method = method,
                                condition = ConstantTrue,
                                actionsAfter = List(method.parameters.size) { index ->
                                    CopyAllMarks(from = Argument(index), to = Result)
                                }
                            )
                        )
                    }
                    rules.ifEmpty { null }
                }

            caseTaintConfig.builtInOptions.forEach { option ->
                when (option) {
                    is UntrustedLoopBoundSinkCheck -> {
                        TaintAnalysisOptions.UNTRUSTED_LOOP_BOUND_SINK = true
                    }

                    is UntrustedArraySizeSinkCheck -> {
                        TaintAnalysisOptions.UNTRUSTED_ARRAY_SIZE_SINK = true
                    }

                    is UntrustedIndexArrayAccessSinkCheck -> {
                        TaintAnalysisOptions.UNTRUSTED_INDEX_ARRAY_ACCESS_SINK = true
                    }
                }
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
            val sinks = manager.analyze(filteredMethods, timeout = 30.seconds)
            logger.info { "Sinks: $sinks" }

            val results = sinks.map { sink ->
                SinkResult(
                    sink,
                    when (withTrace) {
                        false -> null
                        true -> {
                            val graph = manager.vulnerabilityTraceGraph(sink)
                            val trace = graph.getAllTraces().first()
                            Assertions.assertTrue(trace.isNotEmpty())
                            trace.map {
                                it.statement
                            }
                        }
                    }
                )
            }
            return results
        }
    }

    private fun saveSerializedConfig(config: CaseTaintConfig, filename: String) {
        val serializedConfig = Json {
            encodeDefaults = true
            prettyPrint = true
        }.encodeToString(config)
        val fullPath =
            "C:\\Users\\bethi\\IdeaProjects\\jacodb\\jacodb-panda-dynamic\\src\\test\\resources\\samples\\taintConfigs\\${filename}"
        val outputFile = File(fullPath)
        outputFile.writeText(serializedConfig)
    }

    @Nested
    inner class PasswordLeakTest {

        private val fileTaintAnalyzer = FileTaintAnalyzer("taintSamples/passwordLeak")

        @Test
        fun `counterexample - print unencrypted password to console`() {
            val config = loadCaseTaintConfig("passwordLeakTaintConfig1.json")
            val sinkResults = fileTaintAnalyzer.analyseOneCase(config)
            assert(sinkResults.size == 1)
        }

        @Test
        fun `positive example - print encrypted password to console (with forgotten cleaner)`() {
            val config = loadCaseTaintConfig("passwordLeakTaintConfig2.json")
            val sinkResults = fileTaintAnalyzer.analyseOneCase(config)
            assert(sinkResults.size == 1)
        }

        // @Disabled("Cleaner config don't work as expected")
        @Test
        fun `positive example - print encrypted password to console`() {
            val config = CaseTaintConfig(
                sourceMethodConfigs = listOf(
                    SourceMethodConfig(methodName = "getUserPassword")
                ),
                cleanerMethodConfigs = listOf(
                    CleanerMethodConfig(
                        methodName = "encryptPassword",
                        position = Argument(0)
                    )
                ),
                sinkMethodConfigs = listOf(
                    SinkMethodConfig(methodName = "log")
                ),
                startMethodNamesForAnalysis = listOf("case2")
            )
            saveSerializedConfig(config, "passwordLeakTaintConfig3.json")
            // val sinkResults = fileTaintAnalyzer.analyseOneCase(config)
            // assert(sinkResults.isEmpty())
        }

    }

    @Nested
    inner class UntrustedLoopBoundTest {

        private val fileTaintAnalyzer = FileTaintAnalyzer("taintSamples/untrustedLoopBound")

        @Test
        fun `counterexample - untrusted bound in for loop`() {
            val config = loadCaseTaintConfig("untrustedLoopBoundConfig1.json")
            val sinkResults = fileTaintAnalyzer.analyseOneCase(config)
            assert(sinkResults.size == 1)
        }

        @Disabled("Loop do while is not supported yet")
        @Test
        fun `counterexample - untrusted bound in do while loop`() {
            val sinkResults = fileTaintAnalyzer.analyseOneCase(
                CaseTaintConfig(
                    sourceMethodConfigs = listOf(
                        SourceMethodConfig(
                            methodName = "getUserData",
                            markName = "UNTRUSTED"
                        )
                    ),
                    startMethodNamesForAnalysis = listOf("doWhileLoop"),
                    builtInOptions = listOf(UntrustedLoopBoundSinkCheck)
                )
            )
            assert(sinkResults.size == 1)
        }

        @Test
        fun `counterexample - untrusted bound in while loop`() {
            val sinkResults = fileTaintAnalyzer.analyseOneCase(
                CaseTaintConfig(
                    sourceMethodConfigs = listOf(
                        SourceMethodConfig(
                            methodName = "getUserData",
                            markName = "UNTRUSTED"
                        )
                    ),
                    startMethodNamesForAnalysis = listOf("whileLoop"),
                    builtInOptions = listOf(UntrustedLoopBoundSinkCheck)
                )
            )
            assert(sinkResults.size == 1)
        }

    }

    @Nested
    inner class UntrustedArraySizeTest {

        private val fileTaintAnalyzer = FileTaintAnalyzer("taintSamples/untrustedArraySize")

        @Test
        fun `counterexample - untrusted size in array constructor and loop bound`() {
            val config = CaseTaintConfig(
                sourceMethodConfigs = listOf(
                    SourceMethodConfig(
                        methodName = "getNumber",
                        markName = "UNTRUSTED"
                    )
                ),
                startMethodNamesForAnalysis = listOf("main"),
                builtInOptions = listOf(UntrustedLoopBoundSinkCheck, UntrustedArraySizeSinkCheck)
            )
            saveSerializedConfig(config, "untrustedArraySizeConfig1.json")
            // val sinkResults = fileTaintAnalyzer.analyseOneCase(config)
            // assert(sinkResults.size == 2)
        }

    }

    @Nested
    inner class IndexOutOfRangeTest {

        private val fileTaintAnalyzer = FileTaintAnalyzer("taintSamples/indexOutOfRange")

        @Disabled("Taint marks don't pass through as expected")
        @Test
        fun `false positive - potential index out of range alert when it's not feasible`() {
            val sinkResults = fileTaintAnalyzer.analyseOneCase(
                CaseTaintConfig(
                    sourceMethodConfigs = listOf(
                        SourceMethodConfig(
                            methodName = "readUInt",
                            markName = "UNTRUSTED"
                        )
                    ),
                    startMethodNamesForAnalysis = listOf("main"),
                    builtInOptions = listOf(UntrustedIndexArrayAccessSinkCheck)
                )
            )
            assert(sinkResults.size == 2)
        }

    }

    @Nested
    inner class FieldSampleTest {
        private val fileTaintAnalyzer = FileTaintAnalyzer("taintSamples/fieldSample")

        @Disabled("IFDS do not work properly with virtual methods")
        @Test
        fun `test taint analysis on fieldSample`() {
            val sinkResults = fileTaintAnalyzer.analyseOneCase(
                CaseTaintConfig(
                    sourceMethodConfigs = listOf(
                        SourceMethodConfig(
                            methodName = "getNameOption",
                        )
                    ),
                    sinkMethodConfigs = listOf(
                        SinkMethodConfig(
                            methodName = "log",
                        )
                    ),
                )
            )
            assert(sinkResults.size == 2)
        }
    }

    @Nested
    inner class LoopBoundInjectionUseCaseTest {
        private val fileTaintAnalyzer = FileTaintAnalyzer("codeqlSamples/loopBoundInjection")

        private val postHandlerMethodName = "#4722804945120678178#"

        @Disabled("There is no ability to mark method argument as tainted")
        @Test
        fun `counterexample - loop bound injection (could possibly lead to DoS if length is large)`() {
            val sinkResults = fileTaintAnalyzer.analyseOneCase(
                CaseTaintConfig(
                    sourceMethodConfigs = listOf(
                        SourceMethodConfig(
                            methodName = postHandlerMethodName,
                            markName = "UNTRUSTED",
                            position = Argument(0)
                        )
                    ),
                    startMethodNamesForAnalysis = listOf(postHandlerMethodName),
                    builtInOptions = listOf(UntrustedLoopBoundSinkCheck)
                )
            )
            assert(sinkResults.size == 1)
        }
    }

    @Nested
    inner class SQLInjectionTest {
        private val fileTaintAnalyzer = FileTaintAnalyzer("taintSamples/SQLInjection")

        @Test
        fun `counterexample - sql injection that lead to dropping table`() {
            val sinkResults = fileTaintAnalyzer.analyseOneCase(
                CaseTaintConfig(
                    sourceMethodConfigs = listOf(SourceMethodConfig("getUserName")),
                    sinkMethodConfigs = listOf(
                        SinkMethodConfig(
                            methodName = "query",
                        )
                    ),
                )
            )
            assert(sinkResults.size == 1)
        }
    }

    @Nested
    inner class XSSTest {
        private val fileTaintAnalyzer = FileTaintAnalyzer("taintSamples/XSS")

        @Test
        fun `counterexample - not validating user data lead to xss attack`() {
            val sinkResults = fileTaintAnalyzer.analyseOneCase(
                CaseTaintConfig(
                    sourceMethodConfigs = listOf(SourceMethodConfig("getUserComment")),
                    sinkMethodConfigs = listOf(
                        SinkMethodConfig(
                            methodName = "displayComment",
                        )
                    ),
                )
            )
            assert(sinkResults.size == 1)
        }
    }

    @Nested
    inner class PasswordExposureFPTest {
        private val fileTaintAnalyzer = FileTaintAnalyzer("taintSamples/passwordExposureFP")

        @Test
        fun `counterexample - potential exposure of unencrypted password (false positive tho)`() {
            val sinkResults = fileTaintAnalyzer.analyseOneCase(
                CaseTaintConfig(
                    sourceMethodConfigs = listOf(SourceMethodConfig("getUserData")),
                    sinkMethodConfigs = listOf(
                        SinkMethodConfig(
                            methodName = "log",
                        )
                    ),
                    startMethodNamesForAnalysis = listOf("usage1")
                )
            )
            assert(sinkResults.size == 1)
        }

        // TODO("fix config")
        @Test
        fun `counterexample - potential exposure of unencrypted password`() {
            val sinkResults = fileTaintAnalyzer.analyseOneCase(
                CaseTaintConfig(
                    sourceMethodConfigs = listOf(SourceMethodConfig("getUserData")),
                    sinkMethodConfigs = listOf(
                        SinkMethodConfig(
                            methodName = "log",
                        )
                    ),
                    startMethodNamesForAnalysis = listOf("usage2")
                )
            )
            assert(sinkResults.size == 1)
        }

        @Disabled("Temporary test. For debug purposes only.")
        @Test
        fun `debug test`() {
            val sinkResults = fileTaintAnalyzer.analyseOneCase(
                CaseTaintConfig(
                    sourceMethodConfigs = listOf(SourceMethodConfig("getUserData")),
                    sinkMethodConfigs = listOf(
                        SinkMethodConfig(
                            methodName = "log",
                            markName = "WRONG MARK NAME",
                            position = Argument(1)
                        )
                    ),
                    startMethodNamesForAnalysis = listOf("usage3")
                )
            )
            assert(sinkResults.size == 1)
        }
    }
}
