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

import io.mockk.mockk
import org.jacodb.analysis.ifds.SingletonUnit
import org.jacodb.analysis.ifds.UnitResolver
import org.jacodb.analysis.taint.ForwardTaintFlowFunctions
import org.jacodb.analysis.taint.TaintManager
import org.jacodb.analysis.util.PandaStaticTraits
import org.jacodb.panda.staticvm.cfg.PandaApplicationGraph
import org.jacodb.panda.staticvm.cfg.PandaInst
import org.jacodb.panda.staticvm.classpath.PandaMethod
import org.jacodb.panda.staticvm.classpath.PandaProject
import org.jacodb.panda.staticvm.ir.PandaProgramIr
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
import org.junit.jupiter.api.Test

private val logger = mu.KotlinLogging.logger {}

class PandaIfdsTest {

    companion object : PandaStaticTraits

    private fun loadProjectForSample(programName: String): PandaProject {
        val stream = this::class.java.getResourceAsStream("/${programName}.ir")
            ?: error("Could not find resource for program: '$programName'")
        val program = PandaProgramIr.from(stream)
        val project = PandaProject.fromProgramInfo(program)
        return project
    }

    @Test
    fun `test taint analysis on program 2`() {
        runTaintAnalysis("Program2")
    }

    /*@Test
    fun `test taint analysis on program 2 with catch`() {
        runTaintAnalysis("testCatch")
    }*/

    private fun runTaintAnalysis(programName: String) {
        val project = loadProjectForSample(programName)
        val graph = PandaApplicationGraph(project)
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
        Assertions.assertTrue(goodSinks.isEmpty())

        val badMethod = project.classes.flatMap { it.methods }.single { it.name == "bad" }
        logger.info { "bad() method: $badMethod" }
        val badSinks = manager.analyze(listOf(badMethod))
        logger.info { "Sinks in bad(): $badSinks" }
        Assertions.assertTrue(badSinks.isNotEmpty())
    }
}
