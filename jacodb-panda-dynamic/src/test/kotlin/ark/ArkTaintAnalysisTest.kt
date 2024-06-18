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

package ark

import org.jacodb.analysis.ifds.SingletonUnit
import org.jacodb.analysis.ifds.UnitResolver
import org.jacodb.analysis.taint.ForwardTaintFlowFunctions
import org.jacodb.analysis.taint.TaintManager
import org.jacodb.analysis.util.ArkTraits
import org.jacodb.panda.dynamic.ark.base.Stmt
import org.jacodb.panda.dynamic.ark.dto.ArkFileDto
import org.jacodb.panda.dynamic.ark.dto.convertToArkFile
import org.jacodb.panda.dynamic.ark.graph.ArkApplicationGraph
import org.jacodb.panda.dynamic.ark.model.ArkFile
import org.jacodb.panda.dynamic.ark.model.ArkMethod
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

private val logger = mu.KotlinLogging.logger {}

class ArkTaintAnalysisTest {

    companion object : ArkTraits {
        private fun loadArkFile(name: String): ArkFile {
            val path = "arkir/$name.ts.json"
            val stream = object {}::class.java.getResourceAsStream("/$path")
                ?: error("Resource not found: $path")
            val arkDto = ArkFileDto.loadFromJson(stream)
            // println("arkDto = $arkDto")
            val ark = convertToArkFile(arkDto)
            // println("ark = $ark")
            return ark
        }
    }

    @Test
    fun `test taint analysis`() {
        val arkFile = loadArkFile("taint")
        ArkTraits.cp = arkFile
        val graph = ArkApplicationGraph(arkFile)
        val unitResolver = UnitResolver<ArkMethod> { SingletonUnit }
        val getConfigForMethod: ForwardTaintFlowFunctions<ArkMethod, Stmt>.(ArkMethod) -> List<TaintConfigurationItem>? =
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

        val methods = arkFile.classes.flatMap { it.methods }.filter { it.name == "bad" }
        logger.info { "Methods: ${methods.size}" }
        for (method in methods) {
            logger.info { "  ${method.name}" }
        }
        val sinks = manager.analyze(methods, timeout = 60.seconds)
        logger.info { "Sinks: $sinks" }
        assertTrue(sinks.isNotEmpty())
    }
}
