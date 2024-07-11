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
import org.jacodb.analysis.util.toPath
import org.jacodb.api.common.CommonMethod
import org.jacodb.api.common.analysis.ApplicationGraph
import org.jacodb.api.common.cfg.CommonInst
import org.jacodb.api.jvm.cfg.JcIfInst
import org.jacodb.impl.cfg.util.loops
import org.jacodb.panda.dynamic.ets.base.EtsArrayAccess
import org.jacodb.panda.dynamic.ets.base.EtsAssignStmt
import org.jacodb.panda.dynamic.ets.base.EtsIfStmt
import org.jacodb.panda.dynamic.ets.base.EtsNewArrayExpr
import org.jacodb.panda.dynamic.ets.base.EtsStmt
import org.jacodb.panda.dynamic.ets.graph.loops
import org.jacodb.panda.dynamic.ets.utils.getOperands
import org.jacodb.panda.staticvm.utils.loops
import org.jacodb.taint.configuration.TaintConfigurationItem
import org.jacodb.taint.configuration.TaintMethodSink
import org.jacodb.panda.staticvm.cfg.PandaIfInst as StaticPandaIfInst

private val logger = mu.KotlinLogging.logger {}

context(Traits<Method, Statement>)
class TaintAnalyzer<Method, Statement>(
    private val graph: ApplicationGraph<Method, Statement>,
    private val getConfigForMethod: (ForwardTaintFlowFunctions<Method, Statement>.(Method) -> List<TaintConfigurationItem>?)? = null,
) : Analyzer<TaintDomainFact, TaintEvent<Statement>, Method, Statement>
    where Method : CommonMethod,
          Statement : CommonInst {

    override val flowFunctions: ForwardTaintFlowFunctions<Method, Statement> by lazy {
        if (getConfigForMethod != null) {
            ForwardTaintFlowFunctions(graph, getConfigForMethod)
        } else {
            ForwardTaintFlowFunctions(graph)
        }
    }

    private fun isExitPoint(statement: Statement): Boolean {
        return statement in graph.exitPoints(graph.methodOf(statement))
    }

    override fun handleNewEdge(
        edge: TaintEdge<Statement>,
    ): List<TaintEvent<Statement>> = buildList {
        if (isExitPoint(edge.to.statement)) {
            add(NewSummaryEdge(edge))
        }

        run {
            val callExpr = edge.to.statement.getCallExpr() ?: return@run

            val callee = callExpr.callee

            val config = with(flowFunctions) { getConfigForMethod(callee) } ?: return@run

            // TODO: not always we want to skip sinks on Zero facts.
            //  Some rules might have ConstantTrue or just true (when evaluated with Zero fact) condition.
            if (edge.to.fact !is Tainted) {
                return@run
            }

            // Determine whether 'edge.to' is a sink via config:
            val conditionEvaluator = FactAwareConditionEvaluator(
                edge.to.fact,
                CallPositionToValueResolver(edge.to.statement),
            )
            for (item in config.filterIsInstance<TaintMethodSink>()) {
                if (item.condition.accept(conditionEvaluator)) {
                    val message = item.ruleNote
                    val vulnerability = TaintVulnerability(message, sink = edge.to, rule = item)
                    logger.info {
                        "Found sink=${vulnerability.sink} in ${vulnerability.method} on $item"
                    }
                    add(NewVulnerability(vulnerability))
                }
            }
        }

        if (TaintAnalysisOptions.UNTRUSTED_LOOP_BOUND_SINK) {
            val statement = edge.to.statement
            val fact = edge.to.fact
            if (fact is Tainted && fact.mark.name == "UNTRUSTED") {
                if (statement is JcIfInst) {
                    val loops = statement.location.method.flowGraph().loops
                    val loopHeads = loops.map { it.head }
                    if (statement in loopHeads) {
                        for (s in statement.condition.operands) {
                            val p = s.toPath()
                            if (p == fact.variable) {
                                val message = "Untrusted loop bound"
                                val vulnerability = TaintVulnerability(message, sink = edge.to)
                                add(NewVulnerability(vulnerability))
                            }
                        }
                    }
                } else if (statement is StaticPandaIfInst) {
                    val loops = statement.location.method.flowGraph().loops
                    if (loops.any { statement in it.instructions }) {
                        for (s in statement.condition.operands) {
                            val p = s.toPath()
                            if (p == fact.variable) {
                                val message = "Untrusted loop bound"
                                val vulnerability = TaintVulnerability(message, sink = edge.to)
                                add(NewVulnerability(vulnerability))
                            }
                        }
                    }
                } else if (statement is EtsIfStmt) {
                    val pandaGraph = statement.location.method.flowGraph()
                    val loops = pandaGraph.loops
                    if (loops.any { statement in it.instructions }) {
                        for (s in statement.condition.getOperands()) {
                            val p = s.toPath()
                            if (p == fact.variable) {
                                val message = "Untrusted loop bound"
                                val vulnerability = TaintVulnerability(message, sink = edge.to)
                                add(NewVulnerability(vulnerability))
                            }
                        }
                    }
                }
            }
        }
        if (TaintAnalysisOptions.UNTRUSTED_ARRAY_SIZE_SINK) {
            val statement = edge.to.statement
            val fact = edge.to.fact
            if (fact is Tainted && fact.mark.name == "UNTRUSTED") {
                if (statement is EtsAssignStmt) {
                    val expr = statement.rhv
                    if (expr is EtsNewArrayExpr) {
                        val arg = expr.size
                        if (arg.toPathOrNull() == fact.variable) {
                            val message = "Untrusted array size"
                            val vulnerability = TaintVulnerability(message, sink = edge.to)
                            add(NewVulnerability(vulnerability))
                        }
                    }
                }
            }
        }
        if (TaintAnalysisOptions.UNTRUSTED_INDEX_ARRAY_ACCESS_SINK) {
            val statement = edge.to.statement
            val fact = edge.to.fact
            if (fact is Tainted && fact.mark.name == "UNTRUSTED") {
                if (statement is EtsStmt) {
                    for (op in statement.getOperands()) {
                        if (op is EtsArrayAccess) {
                            val arg = op.index
                            if (arg.toPathOrNull() == fact.variable) {
                                val message = "Untrusted index for access array"
                                val vulnerability = TaintVulnerability(message, sink = edge.to)
                                add(NewVulnerability(vulnerability))
                            }
                        }
                    }
                }
            }
        }
    }

    override fun handleCrossUnitCall(
        caller: TaintVertex<Statement>,
        callee: TaintVertex<Statement>,
    ): List<TaintEvent<Statement>> = buildList {
        add(EdgeForOtherRunner(TaintEdge(callee, callee), Reason.CrossUnitCall(caller)))
    }
}

context(Traits<Method, Statement>)
class BackwardTaintAnalyzer<Method, Statement>(
    private val graph: ApplicationGraph<Method, Statement>,
) : Analyzer<TaintDomainFact, TaintEvent<Statement>, Method, Statement>
    where Method : CommonMethod,
          Statement : CommonInst {

    override val flowFunctions: BackwardTaintFlowFunctions<Method, Statement> by lazy {
        BackwardTaintFlowFunctions(graph)
    }

    private fun isExitPoint(statement: Statement): Boolean {
        return statement in graph.exitPoints(graph.methodOf(statement))
    }

    override fun handleNewEdge(
        edge: TaintEdge<Statement>,
    ): List<TaintEvent<Statement>> = buildList {
        if (isExitPoint(edge.to.statement)) {
            add(EdgeForOtherRunner(Edge(edge.to, edge.to), reason = Reason.External))
        }
    }

    override fun handleCrossUnitCall(
        caller: TaintVertex<Statement>,
        callee: TaintVertex<Statement>,
    ): List<TaintEvent<Statement>> {
        return emptyList()
    }
}
