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

package org.jacodb.analysis.library.analyzers

import org.jacodb.analysis.engine.AnalyzerFactory
import org.jacodb.analysis.engine.DomainFact
import org.jacodb.analysis.engine.IfdsResult
import org.jacodb.analysis.engine.IfdsUnitCommunicator
import org.jacodb.analysis.engine.IfdsVertex
import org.jacodb.analysis.engine.VulnerabilityLocation
import org.jacodb.analysis.paths.FieldAccessor
import org.jacodb.api.analysis.JcApplicationGraph
import org.jacodb.api.cfg.JcArgument
import org.jacodb.api.cfg.JcExpr
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.cfg.JcLocal
import org.jacodb.api.cfg.values

fun AliasAnalyzerFactory(
    generates: (JcInst) -> List<DomainFact>,
    sanitizes: (JcExpr, TaintNode) -> Boolean,
    sinks: (JcInst) -> List<TaintAnalysisNode>,
    maxPathLength: Int = 5
) = AnalyzerFactory { graph ->
    AliasAnalyzer(graph, generates, sanitizes, sinks, maxPathLength)
}

private class AliasAnalyzer(
    graph: JcApplicationGraph,
    generates: (JcInst) -> List<DomainFact>,
    sanitizes: (JcExpr, TaintNode) -> Boolean,
    sinks: (JcInst) -> List<TaintAnalysisNode>,
    maxPathLength: Int,
) : TaintAnalyzer(graph, generates, sanitizes, sinks, maxPathLength) {

    override fun handleIfdsResult(ifdsResult: IfdsResult, manager: IfdsUnitCommunicator) {
        ifdsResult.resultFacts.forEach { (inst, facts) ->
            facts.filterIsInstance<TaintAnalysisNode>().forEach { fact ->
                if (fact in sinks(inst)) {
                    fact.variable.let {
                        val name = when (val x = it.value) {
                            is JcArgument -> x.name
                            is JcLocal -> inst.location.method.flowGraph().instructions
                                .first { x in it.operands.flatMap { it.values } }
                                .lineNumber
                                .toString()
                            null -> (it.accesses[0] as FieldAccessor).field.enclosingClass.simpleName
                            else -> error("Unknown local type")
                        }

                        val fullPath = buildString {
                            append(name)
                            if (it.accesses.isNotEmpty()) {
                                append(".")
                            }
                            append(it.accesses.joinToString("."))
                        }

                        manager.uploadSummaryFact(
                            VulnerabilityLocation(
                                vulnerabilityType,
                                IfdsVertex(inst, fact)
                            )
                        )
                        verticesWithTraceGraphNeeded.add(IfdsVertex(inst, fact))
                    }
                }
            }
        }
    }
}