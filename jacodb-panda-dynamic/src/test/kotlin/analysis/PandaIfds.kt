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
import org.jacodb.analysis.util.PandaTraits
import org.jacodb.panda.dynamic.api.PandaAnyType
import org.jacodb.panda.dynamic.api.PandaApplicationGraphImpl
import org.jacodb.panda.dynamic.api.PandaBasicBlock
import org.jacodb.panda.dynamic.api.PandaClass
import org.jacodb.panda.dynamic.api.PandaInst
import org.jacodb.panda.dynamic.api.PandaInstLocation
import org.jacodb.panda.dynamic.api.PandaInstRef
import org.jacodb.panda.dynamic.api.PandaMethod
import org.jacodb.panda.dynamic.api.PandaNullConstant
import org.jacodb.panda.dynamic.api.PandaProject
import org.jacodb.panda.dynamic.api.PandaReturnInst
import org.jacodb.taint.configuration.AnyArgument
import org.jacodb.taint.configuration.Argument
import org.jacodb.taint.configuration.AssignMark
import org.jacodb.taint.configuration.ConstantTrue
import org.jacodb.taint.configuration.ContainsMark
import org.jacodb.taint.configuration.CopyAllMarks
import org.jacodb.taint.configuration.CopyMark
import org.jacodb.taint.configuration.RemoveMark
import org.jacodb.taint.configuration.Result
import org.jacodb.taint.configuration.TaintConfigurationItem
import org.jacodb.taint.configuration.TaintEntryPointSource
import org.jacodb.taint.configuration.TaintMark
import org.jacodb.taint.configuration.TaintMethodSink
import org.jacodb.taint.configuration.TaintMethodSource
import org.jacodb.taint.configuration.TaintPassThrough
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import parser.loadIr
import java.nio.file.Files
import java.nio.file.Paths

private val logger = mu.KotlinLogging.logger {}

class PandaIfds {

    companion object : PandaTraits {
        private fun loadProjectForSample(programName: String): PandaProject {
            val parser = loadIr("/samples/${programName}.json")
            val project = parser.getProject()
            return project
        }
    }

    private fun projectAvailable(): Boolean {
        val resource = object {}::class.java.getResource("/samples/project1")?.toURI()
        return resource != null && Files.exists(Paths.get(resource))
    }

    @Disabled("IFDS do not work properly with virtual methods")
    @Test
    fun `test taint analysis on MethodCollision`() {
        val project = loadProjectForSample("MethodCollision")
        val graph = PandaApplicationGraphImpl(project)
        val unitResolver = UnitResolver<PandaMethod> { SingletonUnit }
        val getConfigForMethod: ForwardTaintFlowFunctions<PandaMethod, PandaInst>.(PandaMethod) -> List<TaintConfigurationItem>? =
            { method ->
                val rules = buildList {
                    if (method.name == "isSame" && method.className == "Foo") add(
                        TaintMethodSource(
                            method = mockk(),
                            condition = ConstantTrue,
                            actionsAfter = listOf(
                                AssignMark(mark = TaintMark("TAINT"), position = Result),
                            ),
                        )
                    )
                    if (method.name == "log") add(
                        TaintMethodSink(
                            method = mockk(),
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
        val sinks = manager.analyze(methods)
        logger.info { "Sinks: $sinks" }
        assertTrue(sinks.isNotEmpty())
    }

    @Test
    fun `test taint analysis on TypeMismatch`() {
        val project = loadProjectForSample("TypeMismatch")
        val graph = PandaApplicationGraphImpl(project)
        val unitResolver = UnitResolver<PandaMethod> { SingletonUnit }
        val getConfigForMethod: ForwardTaintFlowFunctions<PandaMethod, PandaInst>.(PandaMethod) -> List<TaintConfigurationItem>? =
            { method ->
                val rules = buildList {
                    if (method.name == "add") add(
                        TaintMethodSource(
                            method = mockk(),
                            condition = ConstantTrue,
                            actionsAfter = listOf(
                                AssignMark(mark = TaintMark("TAINT"), position = Result),
                            ),
                        )
                    )
                    if (method.name == "log") add(
                        TaintMethodSink(
                            method = mockk(), ruleNote = "CUSTOM SINK", // FIXME
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

        val methods = project.classes.flatMap { it.methods }
        logger.info { "Methods: ${methods.size}" }
        for (method in methods) {
            logger.info { "  ${method.name}" }
        }
        val sinks = manager.analyze(methods)
        logger.info { "Sinks: $sinks" }
        assertTrue(sinks.isNotEmpty())
    }

    @Disabled("Cleaner config don't work as expected")
    @Test
    fun `test taint analysis on DataFlowSecurity`() {
        val project = loadProjectForSample("DataFlowSecurity")
        val graph = PandaApplicationGraphImpl(project)
        val unitResolver = UnitResolver<PandaMethod> { SingletonUnit }
        val getConfigForMethod: ForwardTaintFlowFunctions<PandaMethod, PandaInst>.(PandaMethod) -> List<TaintConfigurationItem>? =
            { method ->
                val rules = buildList {
                    if (method.name == "source") add(
                        TaintMethodSource(
                            method = mockk(),
                            condition = ConstantTrue,
                            actionsAfter = listOf(
                                AssignMark(mark = TaintMark("TAINT"), position = Result),
                            ),
                        )
                    )
                    if (method.name == "sink") add(
                        TaintMethodSink(
                            method = mockk(),
                            ruleNote = "SINK", // FIXME
                            cwe = listOf(), // FIXME
                            condition = ContainsMark(position = Argument(0), mark = TaintMark("TAINT"))
                        )
                    )
                    if (method.name == "pass") add(
                        TaintPassThrough(
                            method = mockk(),
                            condition = ConstantTrue,
                            actionsAfter = listOf(
                                CopyAllMarks(from = Argument(0), to = Result)
                            ),
                        )
                    )
                    if (method.name == "validate") add(
                        TaintPassThrough(
                            method = mockk(),
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
        val goodSinks = manager.analyze(listOf(goodMethod))
        logger.info { "Sinks in good(): $goodSinks" }
        assertTrue(goodSinks.isEmpty())

        val badMethod = project.classes.flatMap { it.methods }.single { it.name == "bad" }
        logger.info { "bad() method: $badMethod" }
        val badSinks = manager.analyze(listOf(badMethod))
        logger.info { "Sinks in bad(): $badSinks" }
        assertTrue(badSinks.isNotEmpty())
    }

    @Test
    fun `manually create PandaProject`() {
        val project = PandaProject(
            classes = listOf(
                PandaClass(
                    name = "Sample",
                    superClassName = "std.core.Object",
                    methods = listOf(PandaMethod(name = "source").also {
                        it.blocks = listOf(
                            PandaBasicBlock(
                                id = 0,
                                successors = setOf(1),
                                predecessors = emptySet(),
                                _start = PandaInstRef(0),
                                _end = PandaInstRef(1)
                            ), PandaBasicBlock(
                                id = 1,
                                successors = setOf(),
                                predecessors = setOf(0),
                                _start = PandaInstRef(0),
                                _end = PandaInstRef(1)
                            )
                        )
                        it.instructions = listOf(
                            PandaReturnInst(
                                location = PandaInstLocation(method = it, _index = 0, lineNumber = 3),
                                returnValue = PandaNullConstant
                            )
                        )
                        it.className = "Sample"
                        it.type = PandaAnyType
                    })
                )
            )
        )
        logger.info { "project = $project" }
        logger.info { "classes = ${project.classes}" }
        assertTrue(project.classes.isNotEmpty())
        logger.info { "methods = ${project.classes.flatMap { it.methods }}" }
        assertTrue(project.classes.flatMap { it.methods }.isNotEmpty())
    }

    @Test
    fun `test taint analysis on case1 - untrusted loop bound scenario`() {
        val project = loadProjectForSample("cases/case1")
        val graph = PandaApplicationGraphImpl(project)
        val unitResolver = UnitResolver<PandaMethod> { SingletonUnit }
        val getConfigForMethod: ForwardTaintFlowFunctions<PandaMethod, PandaInst>.(PandaMethod) -> List<TaintConfigurationItem>? =
            { method ->
                val rules = buildList {
                    if (method.name == "readInt") add(
                        TaintMethodSource(
                            method = mockk(),
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
        val sinks = manager.analyze(methods)
        logger.info { "Sinks: $sinks" }
        assertTrue(sinks.isNotEmpty())
    }

    @Test
    fun `test taint analysis on case2 - untrusted array buffer size scenario`() {
        val project = loadProjectForSample("cases/case2")
        val graph = PandaApplicationGraphImpl(project)
        val unitResolver = UnitResolver<PandaMethod> { SingletonUnit }
        val getConfigForMethod: ForwardTaintFlowFunctions<PandaMethod, PandaInst>.(PandaMethod) -> List<TaintConfigurationItem>? =
            { method ->
                val rules = buildList {
                    if (method.name == "readInt") add(
                        TaintMethodSource(
                            method = mockk(),
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
        val sinks = manager.analyze(methods)
        logger.info { "Sinks: $sinks" }
        assertTrue(sinks.isNotEmpty())
    }

    @Disabled("don't work yet")
    @Test
    fun `test taint analysis on case3 - send plain information with sensitive data`() {
        val project = loadProjectForSample("cases/case3")
        val graph = PandaApplicationGraphImpl(project)
        val unitResolver = UnitResolver<PandaMethod> { SingletonUnit }
        val getConfigForMethod: ForwardTaintFlowFunctions<PandaMethod, PandaInst>.(PandaMethod) -> List<TaintConfigurationItem>? =
            { method ->
                val rules = buildList {
                    if (method.name == "getPassword") add(
                        TaintMethodSource(
                            method = mockk(),
                            condition = ConstantTrue,
                            actionsAfter = listOf(
                                AssignMark(mark = TaintMark("TAINT"), position = Result),
                            ),
                        )
                    )
                    if (method.name == "publish") add(
                        TaintMethodSink(
                            method = mockk(), ruleNote = "SINK", // FIXME
                            cwe = listOf(), // FIXME
                            condition = ContainsMark(position = AnyArgument, mark = TaintMark("TAINT"))
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
        val sinks = manager.analyze(methods)
        logger.info { "Sinks: $sinks" }
        assertTrue(sinks.isNotEmpty())
    }

    @EnabledIf("projectAvailable")
    @Test
    fun `test taint analysis on AccountManager`() {
        val project = loadProjectForSample("project1/entry/src/main/ets/base/account/AccountManager")
        val graph = PandaApplicationGraphImpl(project)
        val unitResolver = UnitResolver<PandaMethod> { SingletonUnit }
        val getConfigForMethod: ForwardTaintFlowFunctions<PandaMethod, PandaInst>.(PandaMethod) -> List<TaintConfigurationItem>? =
            { method ->
                val rules = buildList {
                    // adhoc taint second argument (cursor: string)
                    if (method.name == "getDeviceIdListWithCursor") add(
                        TaintEntryPointSource(
                            method = mockk(),
                            condition = ConstantTrue,
                            actionsAfter = listOf(
                                AssignMark(position = Argument(1), mark = TaintMark("UNSAFE")),
                            ),
                        )
                    )
                    // encodeURI*
                    if (method.name.startsWith("encodeURI")) add(
                        TaintMethodSource(
                            method = mockk(),
                            condition = ContainsMark(position = Argument(0), mark = TaintMark("UNSAFE")),
                            actionsAfter = listOf(
                                RemoveMark(position = Result, mark = TaintMark("UNSAFE")),
                            ),
                        )
                    )
                    // RequestOption.setUrl
                    if (method.name == "setUrl") add(
                        TaintMethodSource(
                            method = mockk(),
                            condition = ConstantTrue,
                            actionsAfter = listOf(
                                CopyMark(
                                    mark = TaintMark("UNSAFE"),
                                    from = Argument(0),
                                    to = Result
                                ),
                            ),
                        )
                    )
                    // HttpManager.requestSync
                    if (method.name == "requestSync") add(
                        TaintMethodSink(
                            method = mockk(),
                            ruleNote = "Unsafe request", // FIXME
                            cwe = listOf(), // FIXME
                            condition = ContainsMark(position = Argument(0), mark = TaintMark("UNSAFE"))
                        )
                    )
                    // SyncUtil.requestGet
                    if (method.name == "requestGet") add(
                        TaintMethodSink(
                            method = mockk(),
                            ruleNote = "Unsafe request", // FIXME
                            cwe = listOf(), // FIXME
                            condition = ContainsMark(position = Argument(0), mark = TaintMark("UNSAFE"))
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
        val sinks = manager.analyze(methods)
        logger.info { "Sinks: $sinks" }
        assertTrue(sinks.isNotEmpty())
    }

}
