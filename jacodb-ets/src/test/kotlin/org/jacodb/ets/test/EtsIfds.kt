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

package org.jacodb.ets.test

import org.jacodb.ets.test.utils.loadEtsFileFromResource
import org.jacodb.analysis.ifds.SingletonUnit
import org.jacodb.analysis.ifds.UnitResolver
import org.jacodb.analysis.taint.ForwardTaintFlowFunctions
import org.jacodb.analysis.taint.TaintAnalysisOptions
import org.jacodb.analysis.taint.TaintManager
import org.jacodb.analysis.util.EtsTraits
import org.jacodb.ets.base.EtsStmt
import org.jacodb.ets.graph.EtsApplicationGraph
import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.model.EtsMethod
import org.jacodb.taint.configuration.Argument
import org.jacodb.taint.configuration.AssignMark
import org.jacodb.taint.configuration.ConstantTrue
import org.jacodb.taint.configuration.ContainsMark
import org.jacodb.taint.configuration.CopyAllMarks
import org.jacodb.taint.configuration.RemoveMark
import org.jacodb.taint.configuration.Result
import org.jacodb.taint.configuration.TaintConfigurationItem
import org.jacodb.taint.configuration.TaintMark
import org.jacodb.taint.configuration.TaintMethodSink
import org.jacodb.taint.configuration.TaintMethodSource
import org.jacodb.taint.configuration.TaintPassThrough
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.toPath
import kotlin.time.Duration.Companion.seconds

private val logger = mu.KotlinLogging.logger {}

@Disabled("Have several issues with EtsIR")
class EtsIfds {

    companion object : EtsTraits {
        private const val BASE_PATH = "/etsir/samples"

        private fun loadSample(programName: String): EtsFile {
            return loadEtsFileFromResource("$BASE_PATH/${programName}.ts.json")
        }
    }

    private fun projectAvailable(): Boolean {
        val resource = object {}::class.java.getResource("/samples/project1")?.toURI()
        return resource != null && resource.toPath().exists()
    }

    @Test
    fun `test taint analysis on MethodCollision`() {
        val project = loadSample("MethodCollision")
        val graph = EtsApplicationGraph(project)
        val unitResolver = UnitResolver<EtsMethod> { SingletonUnit }
        val getConfigForMethod: ForwardTaintFlowFunctions<EtsMethod, EtsStmt>.(EtsMethod) -> List<TaintConfigurationItem>? =
            { method ->
                val rules = buildList {
                    if (method.name == "isSame" && method.signature.enclosingClass.name == "Foo") add(
                        TaintMethodSource(
                            method = method,
                            condition = ConstantTrue,
                            actionsAfter = listOf(
                                AssignMark(mark = TaintMark("TAINT"), position = Result),
                            ),
                        )
                    )
                    if (method.name == "log") add(
                        TaintMethodSink(
                            method = method,
                            ruleNote = "CUSTOM SINK", // FIXME
                            cwe = listOf(), // FIXME
                            condition = ContainsMark(position = Argument(0), mark = TaintMark("TAINT"))
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

        val methods = project.classes.flatMap { it.methods }.filter { it.name == "main" }
        logger.info { "Methods: ${methods.size}" }
        for (method in methods) {
            logger.info { "  ${method.name}" }
        }
        val sinks = manager.analyze(methods, timeout = 60.seconds)
        logger.info { "Sinks: $sinks" }
        Assertions.assertTrue(sinks.isNotEmpty())
    }

    @Test
    fun `test taint analysis on TypeMismatch`() {
        val project = loadSample("TypeMismatch")
        val graph = EtsApplicationGraph(project)
        val unitResolver = UnitResolver<EtsMethod> { SingletonUnit }
        val getConfigForMethod: ForwardTaintFlowFunctions<EtsMethod, EtsStmt>.(EtsMethod) -> List<TaintConfigurationItem>? =
            { method ->
                val rules = buildList {
                    if (method.name == "add") add(
                        TaintMethodSource(
                            method = method,
                            condition = ConstantTrue,
                            actionsAfter = listOf(
                                AssignMark(mark = TaintMark("TAINT"), position = Result),
                            )
                        )
                    )
                    if (method.name == "log") add(
                        TaintMethodSink(
                            method = method,
                            ruleNote = "CUSTOM SINK", // FIXME
                            cwe = listOf(), // FIXME
                            condition = ContainsMark(position = Argument(1), mark = TaintMark("TAINT"))
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

        val methods = project.classes.flatMap { it.methods }
        logger.info { "Methods: ${methods.size}" }
        for (method in methods) {
            logger.info { "  ${method.name}" }
        }
        val sinks = manager.analyze(methods, timeout = 60.seconds)
        logger.info { "Sinks: $sinks" }
        Assertions.assertTrue(sinks.isNotEmpty())
    }

    @Disabled("TODO: Sink should be detected in the 'good' method")
    @Test
    fun `test taint analysis on DataFlowSecurity`() {
        val project = loadSample("DataFlowSecurity")
        val graph = EtsApplicationGraph(project)
        val unitResolver = UnitResolver<EtsMethod> { SingletonUnit }
        val getConfigForMethod: ForwardTaintFlowFunctions<EtsMethod, EtsStmt>.(EtsMethod) -> List<TaintConfigurationItem>? =
            { method ->
                val rules = buildList {
                    if (method.name == "source") add(
                        TaintMethodSource(
                            method = method,
                            condition = ConstantTrue,
                            actionsAfter = listOf(
                                AssignMark(mark = TaintMark("TAINT"), position = Result),
                            ),
                        )
                    )
                    if (method.name == "sink") add(
                        TaintMethodSink(
                            method = method,
                            ruleNote = "SINK", // FIXME
                            cwe = listOf(), // FIXME
                            condition = ContainsMark(position = Argument(0), mark = TaintMark("TAINT"))
                        )
                    )
                    if (method.name == "pass") add(
                        TaintPassThrough(
                            method = method,
                            condition = ConstantTrue,
                            actionsAfter = listOf(
                                CopyAllMarks(from = Argument(0), to = Result)
                            ),
                        )
                    )
                    if (method.name == "validate") add(
                        TaintPassThrough(
                            method = method,
                            condition = ConstantTrue,
                            actionsAfter = listOf(
                                RemoveMark(mark = TaintMark("TAINT"), position = Argument(0))
                            ),
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

        val goodMethod = project.classes.flatMap { it.methods }.single { it.name == "good" }
        logger.info { "good() method: $goodMethod" }
        val goodSinks = manager.analyze(listOf(goodMethod), timeout = 60.seconds)
        logger.info { "Sinks in good(): $goodSinks" }
        Assertions.assertTrue(goodSinks.isEmpty())

        val badMethod = project.classes.flatMap { it.methods }.single { it.name == "bad" }
        logger.info { "bad() method: $badMethod" }
        val badSinks = manager.analyze(listOf(badMethod), timeout = 60.seconds)
        logger.info { "Sinks in bad(): $badSinks" }
        Assertions.assertTrue(badSinks.isNotEmpty())
    }

    @Test
    fun `test taint analysis on case1 - untrusted loop bound scenario`() {
        val project = loadSample("cases/case1")
        val graph = EtsApplicationGraph(project)
        val unitResolver = UnitResolver<EtsMethod> { SingletonUnit }
        val getConfigForMethod: ForwardTaintFlowFunctions<EtsMethod, EtsStmt>.(EtsMethod) -> List<TaintConfigurationItem>? =
            { method ->
                val rules = buildList {
                    if (method.name == "readInt") add(
                        TaintMethodSource(
                            method = method,
                            condition = ConstantTrue,
                            actionsAfter = listOf(
                                AssignMark(mark = TaintMark("UNTRUSTED"), position = Result),
                            ),
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
        TaintAnalysisOptions.UNTRUSTED_LOOP_BOUND_SINK = true

        val methods = project.classes.flatMap { it.methods }
        logger.info { "Methods: ${methods.size}" }
        for (method in methods) {
            logger.info { "  ${method.name}" }
        }
        val sinks = manager.analyze(methods, timeout = 60.seconds)
        logger.info { "Sinks: $sinks" }
        Assertions.assertTrue(sinks.isNotEmpty())
    }

    @Test
    fun `test taint analysis on case2 - untrusted array buffer size scenario`() {
        val project = loadSample("cases/case2")
        val graph = EtsApplicationGraph(project)
        val unitResolver = UnitResolver<EtsMethod> { SingletonUnit }
        val getConfigForMethod: ForwardTaintFlowFunctions<EtsMethod, EtsStmt>.(EtsMethod) -> List<TaintConfigurationItem>? =
            { method ->
                val rules = buildList {
                    if (method.name == "readInt") add(
                        TaintMethodSource(
                            method = method,
                            condition = ConstantTrue,
                            actionsAfter = listOf(
                                AssignMark(mark = TaintMark("UNTRUSTED"), position = Result),
                            ),
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
        TaintAnalysisOptions.UNTRUSTED_ARRAY_SIZE_SINK = true

        val methods = project.classes.flatMap { it.methods }
        logger.info { "Methods: ${methods.size}" }
        for (method in methods) {
            logger.info { "  ${method.name}" }
        }
        val sinks = manager.analyze(methods, timeout = 60.seconds)
        logger.info { "Sinks: $sinks" }
        Assertions.assertTrue(sinks.isNotEmpty())
    }

    // TODO(): support AnyArgument Position type for more flexible configs
    @Test
    fun `test taint analysis on case3 - send plain information with sensitive data`() {
        val project = loadSample("cases/case3")
        val graph = EtsApplicationGraph(project)
        val unitResolver = UnitResolver<EtsMethod> { SingletonUnit }
        val getConfigForMethod: ForwardTaintFlowFunctions<EtsMethod, EtsStmt>.(EtsMethod) -> List<TaintConfigurationItem>? =
            { method ->
                val rules = buildList {
                    if (method.name == "getPassword") add(
                        TaintMethodSource(
                            method = method,
                            condition = ConstantTrue,
                            actionsAfter = listOf(
                                AssignMark(mark = TaintMark("TAINT"), position = Result),
                            ),
                        )
                    )
                    if (method.name == "publishEvent") add(
                        TaintMethodSink(
                            method = method, ruleNote = "SINK", // FIXME
                            cwe = listOf(), // FIXME
                            condition = ContainsMark(position = Argument(1), mark = TaintMark("TAINT"))
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

        val methods = project.classes.flatMap { it.methods }
        logger.info { "Methods: ${methods.size}" }
        for (method in methods) {
            logger.info { "  ${method.name}" }
        }
        val sinks = manager.analyze(methods, timeout = 60.seconds)
        logger.info { "Sinks: $sinks" }
        Assertions.assertTrue(sinks.isNotEmpty())
    }

    @EnabledIf("projectAvailable")
    @Test
    fun `test taint analysis on AccountManager`() {
        val project = loadEtsFileFromResource("/etsir/project1/entry/src/main/ets/base/account/AccountManager.ts.json")
        val graph = EtsApplicationGraph(project)
        val unitResolver = UnitResolver<EtsMethod> { SingletonUnit }
        val getConfigForMethod: ForwardTaintFlowFunctions<EtsMethod, EtsStmt>.(EtsMethod) -> List<TaintConfigurationItem>? =
            { method ->
                val rules = buildList {
                    // adhoc taint second argument (cursor: string)
                    if (method.name == "taintSink") add(
                        TaintMethodSink(
                            method = method,
                            cwe = listOf(),
                            ruleNote = "SINK",
                            condition = ContainsMark(position = Argument(0), mark = TaintMark("TAINT")),
                        )
                    )
//                    // encodeURI*
//                    if (method.name.startsWith("encodeURI")) add(
//                        TaintMethodSource(
//                            method = method,
//                            condition = ContainsMark(position = Argument(0), mark = TaintMark("UNSAFE")),
//                            actionsAfter = listOf(
//                                RemoveMark(position = Result, mark = TaintMark("UNSAFE")),
//                            ),
//                        )
//                    )
//                    // RequestOption.setUrl
//                    if (method.name == "setUrl") add(
//                        TaintMethodSource(
//                            method = method,
//                            condition = ConstantTrue,
//                            actionsAfter = listOf(
//                                CopyMark(
//                                    mark = TaintMark("UNSAFE"),
//                                    from = Argument(0),
//                                    to = Result
//                                ),
//                            ),
//                        )
//                    )
//                    // HttpManager.requestSync
//                    if (method.name == "requestSync") add(
//                        TaintMethodSink(
//                            method = method,
//                            ruleNote = "Unsafe request", // FIXME
//                            cwe = listOf(), // FIXME
//                            condition = ContainsMark(position = Argument(0), mark = TaintMark("UNSAFE"))
//                        )
//                    )
                    // SyncUtil.requestGet
                    if (method.name == "requestGet") add(
                        TaintMethodSource(
                            method = method,
                            condition = ConstantTrue,
                            actionsAfter = listOf(AssignMark(position = Result, mark = TaintMark("TAINT")))
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

        val methodNames = setOf(
            "getDeviceIdListWithCursor",
            "requestGet",
            "taintRun",
            "taintSink"
        )

        val methods = project.classes.flatMap { it.methods }.filter { it.name in methodNames }
        logger.info { "Methods: ${methods.size}" }
        for (method in methods) {
            logger.info { "  ${method.name}" }
        }
        val sinks = manager.analyze(methods, timeout = 60.seconds)
        logger.info { "Sinks: $sinks" }
        Assertions.assertTrue(sinks.isNotEmpty())
    }

}
