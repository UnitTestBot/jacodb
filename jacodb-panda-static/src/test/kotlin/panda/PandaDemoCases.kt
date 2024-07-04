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

package panda

import org.jacodb.analysis.ifds.SingletonUnit
import org.jacodb.analysis.ifds.UnitResolver
import org.jacodb.analysis.taint.ForwardTaintFlowFunctions
import org.jacodb.analysis.taint.TaintAnalysisOptions
import org.jacodb.analysis.taint.TaintManager
import org.jacodb.analysis.util.PandaStaticTraits
import org.jacodb.panda.staticvm.cfg.PandaApplicationGraph
import org.jacodb.panda.staticvm.cfg.PandaInst
import org.jacodb.panda.staticvm.classpath.PandaMethod
import org.jacodb.panda.staticvm.classpath.PandaProject
import org.jacodb.taint.configuration.Argument
import org.jacodb.taint.configuration.AssignMark
import org.jacodb.taint.configuration.ConstantTrue
import org.jacodb.taint.configuration.ContainsMark
import org.jacodb.taint.configuration.Result
import org.jacodb.taint.configuration.TaintConfigurationItem
import org.jacodb.taint.configuration.TaintMark
import org.jacodb.taint.configuration.TaintMethodSink
import org.jacodb.taint.configuration.TaintMethodSource
import org.junit.jupiter.api.condition.EnabledIf
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

private val logger = mu.KotlinLogging.logger {}

class PandaDemoCases {

    companion object : PandaStaticTraits

    private fun loadProject(path: String): PandaProject {
        val program = loadProgram("/$path")
        val project = PandaProject.fromProgramIr(program, withStdLib = true)
        return project
    }

    private fun stdlibAvailable() = EtsStdlib.stdlibAvailable()

    @EnabledIf("stdlibAvailable")
    @Test
    fun `taint analysis on case1`() {
        val path = "cases/case1.ir"
        val project = loadProject(path)
        val graph = PandaApplicationGraph(project)
        val unitResolver = UnitResolver<PandaMethod> { SingletonUnit }
        val getConfigForMethod: ForwardTaintFlowFunctions<PandaMethod, PandaInst>.(PandaMethod) -> List<TaintConfigurationItem>? =
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
                // Return the rules if they are not empty, otherwise return null:
                rules.ifEmpty { null }
            }
        val manager = TaintManager(
            graph = graph,
            unitResolver = unitResolver,
            getConfigForMethod = getConfigForMethod,
        )

        // Enable untrusted loop bounds analysis:
        TaintAnalysisOptions.UNTRUSTED_LOOP_BOUND_SINK = true

        val method = project.classes.flatMap { it.methods }.single { it.name == "onRequest" }
        logger.info { "Method: $method" }
        val sinks = manager.analyze(listOf(method), timeout = 30.seconds)
        logger.info { "Sinks: $sinks" }
        assertTrue(sinks.isNotEmpty())
    }

    @EnabledIf("stdlibAvailable")
    @Test
    fun `taint analysis on case2`() {
        val path = "cases/case2.ir"
        val project = loadProject(path)
        val graph = PandaApplicationGraph(project)
        val unitResolver = UnitResolver<PandaMethod> { SingletonUnit }
        val getConfigForMethod: ForwardTaintFlowFunctions<PandaMethod, PandaInst>.(PandaMethod) -> List<TaintConfigurationItem>? =
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
                    if (method.isConstructor && method.enclosingClass.name == "escompat.ArrayBuffer") add(
                        TaintMethodSink(
                            method = method,
                            ruleNote = "ArrayBuffer constructor",
                            cwe = emptyList(),
                            condition = ContainsMark(position = Argument(1), mark = TaintMark("UNTRUSTED")),
                        )
                    )
                }
                // Return the rules if they are not empty, otherwise return null:
                rules.ifEmpty { null }
            }
        val manager = TaintManager(
            graph = graph,
            unitResolver = unitResolver,
            getConfigForMethod = getConfigForMethod,
        )

        // Enable untrusted loop bounds analysis:
        TaintAnalysisOptions.UNTRUSTED_LOOP_BOUND_SINK = true

        val method = project.classes.single {
            it.name == "Request"
        }.methods.single {
            it.name == "onRemoteMessageRequest"
        }
        logger.info { "Method: $method" }
        val sinks = manager.analyze(listOf(method), timeout = 30.seconds)
        logger.info { "Sinks: $sinks" }
        assertTrue(sinks.isNotEmpty())
    }
}
