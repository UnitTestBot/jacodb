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

import org.jacodb.analysis.config.AdhocFactAwareConditionEvaluator
import org.jacodb.analysis.config.CallPositionToJcValueResolver
import org.jacodb.analysis.config.FactAwareConditionEvaluator
import org.jacodb.analysis.ifds.Analyzer
import org.jacodb.analysis.ifds.Edge
import org.jacodb.analysis.ifds.Reason
import org.jacodb.api.analysis.JcApplicationGraph
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.ext.cfg.callExpr
import org.jacodb.taint.configuration.TaintConfigurationFeature
import org.jacodb.taint.configuration.TaintMethodSink

private val logger = mu.KotlinLogging.logger {}

class TaintAnalyzer(
    private val graph: JcApplicationGraph,
) : Analyzer<TaintDomainFact, TaintEvent> {

    override val flowFunctions: ForwardTaintFlowFunctions by lazy {
        ForwardTaintFlowFunctions(graph.classpath, graph)
    }

    private val taintConfigurationFeature: TaintConfigurationFeature?
        get() = flowFunctions.taintConfigurationFeature

    private fun isExitPoint(statement: JcInst): Boolean {
        return statement in graph.exitPoints(statement.location.method)
    }

    override fun handleNewEdge(
        edge: TaintEdge,
    ): List<TaintEvent> = buildList {
        if (isExitPoint(edge.to.statement)) {
            add(NewSummaryEdge(edge))
        }

        run {
            val callExpr = edge.to.statement.callExpr ?: return@run
            val callee = callExpr.method.method

            val config = taintConfigurationFeature?.getConfigForMethod(callee) ?: return@run

            // TODO: not always we want to skip sinks on Zero facts.
            //  Some rules might have ConstantTrue or just true (when evaluated with Zero fact) condition.
            if (edge.to.fact !is Tainted) {
                return@run
            }

            // Determine whether 'edge.to' is a sink via config:
            val conditionEvaluator = FactAwareConditionEvaluator(
                edge.to.fact,
                CallPositionToJcValueResolver(edge.to.statement),
            )
            for (item in config.filterIsInstance<TaintMethodSink>()) {
                if (item.condition.accept(conditionEvaluator)) {
                    val message = item.ruleNote
                    val vulnerability = TaintVulnerability(message, sink = edge.to, rule = item)
                    logger.info { "Found sink=${vulnerability.sink} in ${vulnerability.method}, rule = ${vulnerability.rule}" }
                    add(NewVulnerability(vulnerability))
                } else {
                    val adhocEvaluator = AdhocFactAwareConditionEvaluator(
                        edge.to.fact,
                        CallPositionToJcValueResolver(edge.to.statement),
                    )
                    for (result in adhocEvaluator.apply(item.condition)) {
                        add(
                            NewVulnerabilityWithAssumptions(
                                message = item.ruleNote,
                                rule = item,
                                edge = edge,
                                assumptions = result.assumptions
                            )
                        )
                    }
                }
            }
        }
    }

    override fun handleCrossUnitCall(
        caller: TaintVertex,
        callee: TaintVertex,
    ): List<TaintEvent> = buildList {
        add(EdgeForOtherRunner(TaintEdge(callee, callee), Reason.CrossUnitCall(caller)))
    }
}

class BackwardTaintAnalyzer(
    private val graph: JcApplicationGraph,
) : Analyzer<TaintDomainFact, TaintEvent> {

    override val flowFunctions: BackwardTaintFlowFunctions by lazy {
        BackwardTaintFlowFunctions(graph.classpath, graph)
    }

    private fun isExitPoint(statement: JcInst): Boolean {
        return statement in graph.exitPoints(statement.location.method)
    }

    override fun handleNewEdge(
        edge: TaintEdge,
    ): List<TaintEvent> = buildList {
        if (isExitPoint(edge.to.statement)) {
            add(EdgeForOtherRunner(Edge(edge.to, edge.to), reason = Reason.External))
        }
    }

    override fun handleCrossUnitCall(
        caller: TaintVertex,
        callee: TaintVertex,
    ): List<TaintEvent> {
        return emptyList()
    }
}
