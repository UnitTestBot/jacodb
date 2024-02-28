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

import org.jacodb.analysis.config.CallPositionToValueResolver
import org.jacodb.analysis.config.FactAwareConditionEvaluator
import org.jacodb.analysis.ifds.Analyzer
import org.jacodb.analysis.ifds.Edge
import org.jacodb.analysis.ifds.Reason
import org.jacodb.analysis.util.Traits
import org.jacodb.api.common.CommonMethod
import org.jacodb.api.common.analysis.ApplicationGraph
import org.jacodb.api.common.cfg.CommonInst
import org.jacodb.api.common.ext.callExpr
import org.jacodb.taint.configuration.TaintConfigurationItem
import org.jacodb.taint.configuration.TaintMethodSink

private val logger = mu.KotlinLogging.logger {}

class TaintAnalyzer<Method, Statement>(
    private val graph: ApplicationGraph<Method, Statement>,
    private val traits: Traits<Method, Statement>,
    private val getConfigForMethod: (ForwardTaintFlowFunctions<Method, Statement>.(Method) -> List<TaintConfigurationItem>?)? = null,
) : Analyzer<TaintDomainFact, TaintEvent<Method, Statement>, Method, Statement>
    where Method : CommonMethod<Method, Statement>,
          Statement : CommonInst<Method, Statement> {

    override val flowFunctions: ForwardTaintFlowFunctions<Method, Statement> by lazy {
        if (getConfigForMethod != null) {
            ForwardTaintFlowFunctions(graph, traits, getConfigForMethod)
        } else {
            ForwardTaintFlowFunctions(graph, traits)
        }
    }

    private fun isExitPoint(statement: Statement): Boolean {
        return statement in graph.exitPoints(statement.location.method)
    }

    override fun handleNewEdge(
        edge: TaintEdge<Method, Statement>,
    ): List<TaintEvent<Method, Statement>> = buildList {
        if (isExitPoint(edge.to.statement)) {
            add(NewSummaryEdge(edge))
        }

        run {
            val callExpr = edge.to.statement.callExpr ?: return@run

            @Suppress("UNCHECKED_CAST")
            val callee = callExpr.callee as Method

            val config = with(flowFunctions) { getConfigForMethod(callee) } ?: return@run

            // TODO: not always we want to skip sinks on Zero facts.
            //  Some rules might have ConstantTrue or just true (when evaluated with Zero fact) condition.
            if (edge.to.fact !is Tainted) {
                return@run
            }

            // Determine whether 'edge.to' is a sink via config:
            val conditionEvaluator = FactAwareConditionEvaluator(
                edge.to.fact,
                traits,
                CallPositionToValueResolver(edge.to.statement),
            )
            for (item in config.filterIsInstance<TaintMethodSink>()) {
                if (item.condition.accept(conditionEvaluator)) {
                    val message = item.ruleNote
                    val vulnerability = TaintVulnerability(message, sink = edge.to, rule = item)
                    logger.info { "Found sink=${vulnerability.sink} in ${vulnerability.method}" }
                    add(NewVulnerability(vulnerability))
                }
            }
        }
    }

    override fun handleCrossUnitCall(
        caller: TaintVertex<Method, Statement>,
        callee: TaintVertex<Method, Statement>,
    ): List<TaintEvent<Method, Statement>> = buildList {
        add(EdgeForOtherRunner(TaintEdge(callee, callee), Reason.CrossUnitCall(caller)))
    }
}

class BackwardTaintAnalyzer<Method, Statement>(
    private val graph: ApplicationGraph<Method, Statement>,
    private val traits: Traits<Method, Statement>,
) : Analyzer<TaintDomainFact, TaintEvent<Method, Statement>, Method, Statement>
    where Method : CommonMethod<Method, Statement>,
          Statement : CommonInst<Method, Statement> {

    override val flowFunctions: BackwardTaintFlowFunctions<Method, Statement> by lazy {
        BackwardTaintFlowFunctions(graph, traits)
    }

    private fun isExitPoint(statement: Statement): Boolean {
        return statement in graph.exitPoints(statement.location.method)
    }

    override fun handleNewEdge(
        edge: TaintEdge<Method, Statement>,
    ): List<TaintEvent<Method, Statement>> = buildList {
        if (isExitPoint(edge.to.statement)) {
            add(EdgeForOtherRunner(Edge(edge.to, edge.to), reason = Reason.External))
        }
    }

    override fun handleCrossUnitCall(
        caller: TaintVertex<Method, Statement>,
        callee: TaintVertex<Method, Statement>,
    ): List<TaintEvent<Method, Statement>> {
        return emptyList()
    }
}
