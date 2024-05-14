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

package org.jacodb.analysis.taint

import org.jacodb.analysis.config.CallPositionToJcValueResolver
import org.jacodb.analysis.config.FactAwareConditionEvaluator
import org.jacodb.analysis.ifds.Analyzer
import org.jacodb.api.JcMethod
import org.jacodb.api.analysis.JcApplicationGraph
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.ext.cfg.callExpr
import org.jacodb.ifds.domain.Edge
import org.jacodb.ifds.domain.Reason
import org.jacodb.ifds.domain.RunnerId
import org.jacodb.ifds.messages.NewEdge
import org.jacodb.ifds.messages.NewFinding
import org.jacodb.ifds.messages.NewSummaryEdge
import org.jacodb.ifds.messages.RunnerMessage
import org.jacodb.ifds.taint.TaintVulnerability
import org.jacodb.taint.configuration.TaintConfigurationFeature
import org.jacodb.taint.configuration.TaintMethodSink

private val logger = mu.KotlinLogging.logger {}


class ForwardTaintAnalyzer(
    private val selfRunnerId: RunnerId,
    private val graph: JcApplicationGraph,
) : Analyzer<TaintDomainFact, RunnerMessage> {
    private val cp = graph.classpath

    private val taintConfigurationFeature: TaintConfigurationFeature? by lazy {
        cp.features
            ?.singleOrNull { it is TaintConfigurationFeature }
            ?.let { it as TaintConfigurationFeature }
    }


    override val flowFunctions: ForwardTaintFlowFunctions by lazy {
        ForwardTaintFlowFunctions(cp, graph, taintConfigurationFeature)
    }

    private fun isExitPoint(statement: JcInst): Boolean {
        return statement in graph.exitPoints(statement.location.method)
    }

    override fun obtainPossibleStartFacts(
        method: JcMethod,
    ): Collection<TaintDomainFact> = flowFunctions.obtainPossibleStartFacts(method)

    override fun handleNewEdge(
        edge: TaintEdge,
    ): List<RunnerMessage> = buildList {
        if (isExitPoint(edge.to.statement)) {
            add(NewSummaryEdge(selfRunnerId, edge))
        }

        run {
            val callExpr = edge.to.statement.callExpr ?: return@run
            val callee = callExpr.method.method

            val config = taintConfigurationFeature?.getConfigForMethod(callee) ?: return@run

            // TODO: not always we want to skip sinks on Zero facts.
            //  Some rules might have ConstantTrue or just true (when evaluated with Zero fact) condition.
            val fact = edge.to.fact
            if (fact !is Tainted) {
                return@run
            }

            // Determine whether 'edge.to' is a sink via config:
            val conditionEvaluator = FactAwareConditionEvaluator(
                fact,
                CallPositionToJcValueResolver(edge.to.statement),
            )
            for (item in config.filterIsInstance<TaintMethodSink>()) {
                if (item.condition.accept(conditionEvaluator)) {
                    val message = item.ruleNote
                    val vulnerability = TaintVulnerability(message, sink = edge.to, rule = item)
                    logger.info { "Found sink=${vulnerability.sink} in ${vulnerability.sink.statement.location.method}" }
                    add(NewFinding(selfRunnerId, vulnerability))
                }
            }
        }
    }

}

class BackwardTaintAnalyzer(
    private val selfRunnerId: RunnerId,
    private val otherRunnerId: RunnerId,
    private val graph: JcApplicationGraph,
) : Analyzer<TaintDomainFact, RunnerMessage> {

    override val flowFunctions: BackwardTaintFlowFunctions by lazy {
        BackwardTaintFlowFunctions(graph.classpath, graph)
    }

    override fun obtainPossibleStartFacts(
        method: JcMethod,
    ): Collection<TaintDomainFact> {
        return listOf(TaintZeroFact)
    }

    private fun isExitPoint(statement: JcInst): Boolean {
        return statement in graph.exitPoints(statement.location.method)
    }

    override fun handleNewEdge(
        edge: TaintEdge,
    ): List<RunnerMessage> = buildList {
        if (isExitPoint(edge.to.statement)) {
            add(NewEdge(otherRunnerId, Edge(edge.to, edge.to), Reason.FromOtherRunner(edge, selfRunnerId)))
        }
    }
}
