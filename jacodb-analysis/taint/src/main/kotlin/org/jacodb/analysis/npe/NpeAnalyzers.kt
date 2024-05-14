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

package org.jacodb.analysis.npe

import org.jacodb.analysis.config.CallPositionToJcValueResolver
import org.jacodb.analysis.config.FactAwareConditionEvaluator
import org.jacodb.analysis.ifds.Analyzer
import org.jacodb.analysis.taint.NewSummaryEdge
import org.jacodb.analysis.taint.NewVulnerability
import org.jacodb.analysis.taint.TaintDomainFact
import org.jacodb.analysis.taint.TaintEdge
import org.jacodb.analysis.taint.TaintEvent
import org.jacodb.analysis.taint.TaintVulnerability
import org.jacodb.analysis.taint.Tainted
import org.jacodb.api.analysis.JcApplicationGraph
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.ext.cfg.callExpr
import org.jacodb.taint.configuration.TaintConfigurationFeature
import org.jacodb.taint.configuration.TaintMark
import org.jacodb.taint.configuration.TaintMethodSink

private val logger = mu.KotlinLogging.logger {}

class NpeAnalyzer(
    private val graph: JcApplicationGraph,
) : Analyzer<TaintDomainFact, TaintEvent> {

    override val flowFunctions: ForwardNpeFlowFunctions by lazy {
        ForwardNpeFlowFunctions(graph.classpath, graph)
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

        val fact = edge.to.fact
        if (fact is Tainted && fact.mark == TaintMark.NULLNESS) {
            if (fact.variable.isDereferencedAt(edge.to.statement)) {
                val message = "NPE" // TODO
                val vulnerability = TaintVulnerability(message, sink = edge.to)
                logger.info { "Found sink=${vulnerability.sink} in ${vulnerability.method}" }
                add(NewVulnerability(vulnerability))
            }
        }

        run {
            val callExpr = edge.to.statement.callExpr ?: return@run
            val callee = callExpr.method.method

            val config = taintConfigurationFeature?.getConfigForMethod(callee) ?: return@run

            // TODO: not always we want to skip sinks on Zero facts.
            //  Some rules might have ConstantTrue or just true (when evaluated with Zero fact) condition.
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
                    logger.trace { "Found sink at ${edge.to} in ${edge.from.statement.location.method} on $item" }
                    val message = item.ruleNote
                    val vulnerability = TaintVulnerability(message, sink = edge.to, rule = item)
                    add(NewVulnerability(vulnerability))
                }
            }
        }
    }

}
