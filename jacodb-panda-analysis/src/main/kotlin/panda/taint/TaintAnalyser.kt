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

package org.jacodb.panda.taint

import org.jacodb.analysis.ifds.SingletonUnit
import org.jacodb.analysis.ifds.UnitResolver
import org.jacodb.analysis.taint.ForwardTaintFlowFunctions
import org.jacodb.analysis.taint.TaintAnalysisOptions
import org.jacodb.analysis.taint.TaintManager
import org.jacodb.analysis.util.PandaTraits
import org.jacodb.panda.dynamic.api.PandaApplicationGraph
import org.jacodb.panda.dynamic.api.PandaApplicationGraphImpl
import org.jacodb.panda.dynamic.api.PandaInst
import org.jacodb.panda.dynamic.api.PandaMethod
import org.jacodb.panda.dynamic.api.PandaProject
import org.jacodb.taint.configuration.*
import kotlin.time.Duration.Companion.seconds

private val logger = mu.KotlinLogging.logger {}

class TaintAnalyzer(val project: PandaProject) {
    companion object : PandaTraits

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
                                    condition = when (sinkPosition) {
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
