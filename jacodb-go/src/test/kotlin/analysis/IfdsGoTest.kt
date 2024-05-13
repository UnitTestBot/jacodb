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
import org.jacodb.taint.configuration.*
import java.io.File
import org.jacodb.analysis.ifds.*
import org.jacodb.analysis.taint.*
import org.jacodb.analysis.util.GoTraits
import org.jacodb.go.api.*
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.usvm.jacodb.gen.StartDeserializer
import org.usvm.jacodb.gen.ssaToJacoProject
import java.util.zip.GZIPInputStream

private val logger = mu.KotlinLogging.logger {}

class IfdsGoTest {

    companion object : GoTraits

    private fun loadProjectForSample(programName: String): GoProject {
        val sampleFilePath = javaClass.getResource("/${programName}/filled.gzip")?.path ?: ""

        val res = StartDeserializer(GZIPInputStream(File(sampleFilePath).inputStream()).bufferedReader()) as ssaToJacoProject
        val jcdb = res.createJacoDBProject()

        return jcdb
    }

    @Test
    fun `test taint analysis on TypeMismatch`() {
        val project = loadProjectForSample("TypeMismatch")
        val graph = GoApplicationGraphImpl(project)
        val unitResolver = UnitResolver<GoMethod> { SingletonUnit }
        val getConfigForMethod: ForwardTaintFlowFunctions<GoMethod, GoInst>.(GoMethod) -> List<TaintConfigurationItem>? =
            { method ->
                val rules = buildList {
                    if (method.metName == "add") {
                        add(
                            TaintMethodSource(
                                method = mockk(),
                                condition = ConstantTrue,
                                actionsAfter = listOf(
                                    AssignMark(mark = TaintMark("TAINT"), position = Result),
                                ),
                            )
                        )
                    }
                    if (method.metName == "log") {
                        add(
                            TaintMethodSink(
                                method = mockk(), ruleNote = "CUSTOM SINK", // FIXME
                                cwe = listOf(), // FIXME
                                condition = ContainsMark(position = Argument(0), mark = TaintMark("TAINT"))
                            )
                        )
                    }
                }
                rules.ifEmpty { null }
            }

        val manager = TaintManager(
            graph = graph,
            unitResolver = unitResolver,
            getConfigForMethod = getConfigForMethod
        )
        val methods = project.methods
        logger.info { "Methods: ${methods.size}" }
        for (method in methods) {
            logger.info { "  ${method.metName}" }
        }
        val sinks = manager.analyze(methods)
        logger.info { "Sinks: $sinks" }
        assertTrue(sinks.isNotEmpty())
    }

    @Test
    fun `test taint analysis on SQLI`() {
        val project = loadProjectForSample("SQLI")
        val graph = GoApplicationGraphImpl(project)
        val unitResolver = UnitResolver<GoMethod> { SingletonUnit }
        val getConfigForMethod: ForwardTaintFlowFunctions<GoMethod, GoInst>.(GoMethod) -> List<TaintConfigurationItem>? =
            { method ->
                val rules = buildList {
                    if (method.metName == "Sprintf" && method.packageName == "fmt") {
                        add(
                            TaintMethodSource(
                                method = mockk(),
                                condition = ConstantTrue,
                                actionsAfter = listOf(
                                    AssignMark(mark = TaintMark("TAINT"), position = Result),
                                ),
                            )
                        )
                    }
                    if (method.metName == "Query" && method.packageName == "sql") {
                        add(
                            TaintMethodSink(
                                method = mockk(), ruleNote = "CUSTOM SINK", // FIXME
                                cwe = listOf(), // FIXME
                                condition = ContainsMark(position = Argument(0), mark = TaintMark("TAINT"))
                            )
                        )
                    }
                }
                rules.ifEmpty { null }
            }

        val manager = TaintManager(
            graph = graph,
            unitResolver = unitResolver,
            getConfigForMethod = getConfigForMethod
        )
        val methods = project.methods.filter {
            it.packageName == "main" || it.packageName == "fmt" || it.packageName == "sql"
        }
        logger.info { "Methods: ${methods.size}" }
        for (method in methods) {
            logger.info { "  ${method.metName}" }
        }
        val sinks = manager.analyze(methods)
        logger.info { "Sinks: $sinks" }
        assertTrue(sinks.isNotEmpty())
    }

    @Test
    fun `manually create GoProject`() {
        val project = GoProject(
            methods = listOf(
                GoFunction(
                    metName = "Sample",
                    packageName = "std.core.Object",
                    type = InterfaceType(),
                    returnTypes = listOf(
                        LongType()
                    ),
                    operands = listOf(
                        GoParameter(
                            0, "a", LongType()
                        ),
                        GoParameter(
                            0, "b", LongType()
                        )
                    ),
                    blocks = listOf() // After
                )
            )
        )
        (project.methods[0] as GoFunction).blocks = listOf(
            GoBasicBlock(
                id = 0,
                successors = listOf(1),
                predecessors = emptyList(),
                insts = listOf<GoInst>(
                    GoReturnInst(
                        location = GoInstLocationImpl(
                            -1, -1, project.methods[0]
                        ),
                        retValue = listOf(
                            GoAddExpr(
                                type = LongType(),
                                lhv = GoParameter(
                                    0, "a", LongType()
                                ),
                                rhv = GoParameter(
                                    1, "b", LongType()
                                ),
                                location = GoInstLocationImpl(
                                    index = 0,
                                    lineNumber = 0,
                                    method = project.methods[0],
                                ),
                                name = "t0"
                            )
                        )
                    )
                )
            )
        )
        logger.info { "project = $project" }
        logger.info { "methods = ${project.methods}" }
        assertTrue(project.methods.isNotEmpty())
        logger.info { "operands = ${project.methods.flatMap { it.operands }}" }
        assertTrue(project.methods.flatMap { it.operands }.isNotEmpty())
        logger.info { "blocks = ${project.methods.flatMap { it.blocks }}" }
        assertTrue(project.methods.flatMap { it.blocks }.isNotEmpty())
    }

}
