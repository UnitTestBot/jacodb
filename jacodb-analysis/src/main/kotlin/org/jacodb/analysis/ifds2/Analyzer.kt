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

package org.jacodb.analysis.ifds2

import io.github.oshai.kotlinlogging.KotlinLogging
import org.jacodb.analysis.config.CallPositionToJcValueResolver
import org.jacodb.analysis.config.FactAwareConditionEvaluator
import org.jacodb.api.JcMethod
import org.jacodb.api.analysis.JcApplicationGraph
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.ext.cfg.callExpr
import org.jacodb.taint.configuration.TaintMethodSink

private val logger = KotlinLogging.logger {}

interface Analyzer {
    val flowFunctions: FlowFunctionsSpace

    fun isSkipped(method: JcMethod): Boolean = false

    fun handleNewEdge(edge: Edge): List<Event>
    fun handleCrossUnitCall(caller: Vertex, callee: Vertex): List<Event>
}

class TaintAnalyzer(
    private val graph: JcApplicationGraph,
) : Analyzer {
    override val flowFunctions: FlowFunctionsSpace by lazy {
        TaintForwardFlowFunctions(graph.classpath, graph)
    }

    private fun isExitPoint(statement: JcInst): Boolean {
        return statement in graph.exitPoints(statement.location.method)
    }

    private fun isSink(statement: JcInst, fact: Fact): Boolean {
        // TODO
        return false
    }

    override fun handleNewEdge(edge: Edge): List<Event> = buildList {
        if (isExitPoint(edge.to.statement)) {
            add(NewSummaryEdge(edge))
        }

        val configOk = run {
            val callExpr = edge.to.statement.callExpr ?: return@run false
            val callee = callExpr.method.method

            val config = (flowFunctions as TaintForwardFlowFunctions)
                .taintConfigurationFeature?.let { feature ->
                    // logger.debug { "Extracting config for $callee" }
                    feature.getConfigForMethod(callee)
                } ?: return@run false

            // TODO: not always we want to skip sinks on Zero facts.
            //  Some rules might have ConstantTrue or just true (when evaluated with Zero fact) condition.
            if (edge.to.fact !is Tainted) {
                return@run false
            }

            // Determine whether 'edge.to' is a sink via config:
            val conditionEvaluator = FactAwareConditionEvaluator(
                edge.to.fact,
                CallPositionToJcValueResolver(edge.to.statement),
            )
            var triggeredItem: TaintMethodSink? = null
            for (item in config.filterIsInstance<TaintMethodSink>()) {
                if (item.condition.accept(conditionEvaluator)) {
                    triggeredItem = item
                    break
                }
                // FIXME: unconditionally let it be the sink.
                // triggeredItem = item
                // break
            }
            if (triggeredItem != null) {
                // logger.info { "Found sink at ${edge.to} in ${edge.method} on $triggeredItem" }
                val message = "SINK" // TODO
                val vulnerability = Vulnerability(message, sink = edge.to, edge = edge, rule = triggeredItem)
                // logger.info { "Found $vulnerability in ${vulnerability.method}" }
                logger.info { "Found sink=${vulnerability.sink} in ${vulnerability.method}" }
                add(NewVulnerability(vulnerability))
                // verticesWithTraceGraphNeeded.add(edge.to)
            }
            true
        }

        if (!configOk) {
            // "config"-less behavior:
            if (isSink(edge.to.statement, edge.to.fact)) {
                // logger.info { "Found sink at ${edge.to} in ${edge.method}" }
                val message = "SINK" // TODO
                val vulnerability = Vulnerability(message, sink = edge.to, edge = edge)
                logger.info { "Found $vulnerability in ${vulnerability.method}" }
                add(NewVulnerability(vulnerability))
                // verticesWithTraceGraphNeeded.add(edge.to)
            }
        }
    }

    override fun handleCrossUnitCall(caller: Vertex, callee: Vertex): List<Event> = buildList {
        add(EdgeForOtherRunner(Edge(callee, callee)))
    }
}
