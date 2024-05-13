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
import org.jacodb.analysis.util.PandaTraits
import org.jacodb.panda.dynamic.api.PandaApplicationGraphImpl
import org.jacodb.panda.dynamic.api.PandaInst
import org.jacodb.panda.dynamic.api.PandaMethod
import org.jacodb.panda.dynamic.parser.IRParser
import org.jacodb.taint.configuration.Argument
import org.jacodb.taint.configuration.AssignMark
import org.jacodb.taint.configuration.ConstantTrue
import org.jacodb.taint.configuration.ContainsMark
import org.jacodb.taint.configuration.CopyMark
import org.jacodb.taint.configuration.RemoveMark
import org.jacodb.taint.configuration.Result
import org.jacodb.taint.configuration.TaintConfigurationItem
import org.jacodb.taint.configuration.TaintEntryPointSource
import org.jacodb.taint.configuration.TaintMark
import org.jacodb.taint.configuration.TaintMethodSink
import org.jacodb.taint.configuration.TaintMethodSource
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import parser.loadIr
import java.nio.file.Files
import java.nio.file.Paths

private val logger = mu.KotlinLogging.logger {}

class PandaIfdsProject {

    companion object : PandaTraits {
        private const val PROJECT_PATH = "/samples/project1"
        private const val BASE_PATH = "$PROJECT_PATH/entry/src/main/ets/"

        private fun load(name: String): IRParser {
            return loadIr(filePath = "$BASE_PATH$name.json")
        }
    }

    private fun projectAvailable(): Boolean {
        val resource = object {}::class.java.getResource(PROJECT_PATH)?.toURI()
        return resource != null && Files.exists(Paths.get(resource))
    }

    @EnabledIf("projectAvailable")
    @Test
    fun `test taint analysis on AccountManager`() {
        val parser = load("base/account/AccountManager")
        val program = parser.getProgram()
        val project = parser.getProject()
        println(program)
        println(project)
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
