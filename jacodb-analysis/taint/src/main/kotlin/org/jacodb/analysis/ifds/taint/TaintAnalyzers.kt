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

package org.jacodb.analysis.ifds.taint

import org.jacodb.analysis.ifds.common.JcBaseAnalyzer
import org.jacodb.analysis.ifds.config.CallPositionToJcValueResolver
import org.jacodb.analysis.ifds.config.FactAwareConditionEvaluator
import org.jacodb.analysis.ifds.domain.Edge
import org.jacodb.analysis.ifds.domain.RunnerId
import org.jacodb.analysis.ifds.messages.NewFinding
import org.jacodb.analysis.ifds.messages.NewSummaryEdge
import org.jacodb.analysis.ifds.messages.RunnerMessage
import org.jacodb.api.analysis.JcApplicationGraph
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.ext.cfg.callExpr
import org.jacodb.taint.configuration.TaintConfigurationFeature
import org.jacodb.taint.configuration.TaintMethodSink

private val logger = mu.KotlinLogging.logger {}

class ForwardTaintAnalyzer(
    selfRunnerId: RunnerId,
    graph: JcApplicationGraph,
) : JcBaseAnalyzer<TaintDomainFact>(
    selfRunnerId,
    graph,
) {
    private val taintConfigurationFeature: TaintConfigurationFeature? by lazy {
        graph.classpath.features
            ?.singleOrNull { it is TaintConfigurationFeature }
            ?.let { it as TaintConfigurationFeature }
    }

    override val flowFunctions by lazy {
        ForwardTaintFlowFunctions(graph.classpath, graph, taintConfigurationFeature)
    }

    private fun isExitPoint(statement: JcInst): Boolean {
        return statement in graph.exitPoints(statement.location.method)
    }

    override fun MutableList<RunnerMessage>.onNewEdge(newEdge: Edge<JcInst, TaintDomainFact>) {
        if (isExitPoint(newEdge.to.statement)) {
            add(NewSummaryEdge(selfRunnerId, newEdge))
        }

        run {
            val callExpr = newEdge.to.statement.callExpr ?: return@run
            val callee = callExpr.method.method

            val config = taintConfigurationFeature?.getConfigForMethod(callee) ?: return@run

            // TODO: not always we want to skip sinks on Zero facts.
            //  Some rules might have ConstantTrue or just true (when evaluated with Zero fact) condition.
            val fact = newEdge.to.fact
            if (fact !is Tainted) {
                return@run
            }

            // Determine whether 'edge.to' is a sink via config:
            val conditionEvaluator = FactAwareConditionEvaluator(
                fact,
                CallPositionToJcValueResolver(newEdge.to.statement),
            )
            for (item in config.filterIsInstance<TaintMethodSink>()) {
                if (item.condition.accept(conditionEvaluator)) {
                    val message = item.ruleNote
                    val vulnerability = TaintVulnerability(message, sink = newEdge.to, rule = item)
                    logger.info { "Found sink=${vulnerability.sink} in ${vulnerability.sink.statement.location.method}" }
                    add(NewFinding(selfRunnerId, vulnerability))
                }
            }
        }
    }

}
