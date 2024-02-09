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

package org.jacodb.analysis.ifds2.taint

import io.github.oshai.kotlinlogging.KotlinLogging
import org.jacodb.analysis.config.CallPositionToJcValueResolver
import org.jacodb.analysis.config.FactAwareConditionEvaluator
import org.jacodb.analysis.ifds2.Analyzer
import org.jacodb.analysis.ifds2.Edge
import org.jacodb.analysis.paths.toPath
import org.jacodb.api.JcMethod
import org.jacodb.api.analysis.JcApplicationGraph
import org.jacodb.api.cfg.JcIfInst
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.ext.cfg.callExpr
import org.jacodb.impl.cfg.util.loops
import org.jacodb.taint.configuration.TaintConfigurationFeature
import org.jacodb.taint.configuration.TaintMethodSink

private val logger = KotlinLogging.logger {}

class TaintAnalyzer(
    private val graph: JcApplicationGraph,
) : Analyzer<TaintFact, TaintEvent> {

    override val flowFunctions: ForwardTaintFlowFunctions by lazy {
        ForwardTaintFlowFunctions(graph.classpath, graph)
    }

    private val taintConfigurationFeature: TaintConfigurationFeature?
        get() = flowFunctions.taintConfigurationFeature

    private fun isExitPoint(statement: JcInst): Boolean {
        return statement in graph.exitPoints(statement.location.method)
    }

    private fun isSink(statement: JcInst, fact: TaintFact): Boolean {
        // TODO
        return false
    }

    private val loopsCache: MutableMap<JcMethod, List<JcInst>> = hashMapOf()

    override fun handleNewEdge(
        edge: TaintEdge,
    ): List<TaintEvent> = buildList {
        if (isExitPoint(edge.to.statement)) {
            add(NewSummaryEdge(edge))
        }

        var defaultBehavior = true
        run {
            val callExpr = edge.to.statement.callExpr ?: return@run
            val callee = callExpr.method.method

            val config = taintConfigurationFeature?.let { feature ->
                logger.trace { "Extracting config for $callee" }
                feature.getConfigForMethod(callee)
            } ?: return@run

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
            var triggeredItem: TaintMethodSink? = null
            for (item in config.filterIsInstance<TaintMethodSink>()) {
                defaultBehavior = false
                try {
                    if (item.condition.accept(conditionEvaluator)) {
                        triggeredItem = item
                        break
                    }
                } catch (_: IllegalStateException) {
                }
                // FIXME: unconditionally let it be the sink.
                // triggeredItem = item
                // break
            }
            if (triggeredItem != null) {
                // logger.info { "Found sink at ${edge.to} in ${edge.method} on $triggeredItem" }
                val message = "SINK" // TODO
                val vulnerability = Vulnerability(message, sink = edge.to, edge = edge, rule = triggeredItem)
                // logger.info { "Found sink=${vulnerability.sink} in ${vulnerability.method}" }
                add(NewVulnerability(vulnerability))
                // verticesWithTraceGraphNeeded.add(edge.to)
            }
        }

        if (defaultBehavior) {
            // Default ("config"-less) behavior:
            if (isSink(edge.to.statement, edge.to.fact)) {
                // logger.info { "Found sink at ${edge.to} in ${edge.method}" }
                val message = "SINK" // TODO
                val vulnerability = Vulnerability(message, sink = edge.to, edge = edge)
                // logger.info { "Found sink=${vulnerability.sink} in ${vulnerability.method}" }
                add(NewVulnerability(vulnerability))
                // verticesWithTraceGraphNeeded.add(edge.to)
            }

            if (Globals.TAINTED_LOOP_BOUND_SINK) {
                val statement = edge.to.statement
                val fact = edge.to.fact
                if (statement is JcIfInst && fact is Tainted) {
                    val loopHeads = loopsCache.getOrPut(statement.location.method) {
                        statement.location.method.flowGraph().loops.map { it.head }
                    }
                    if (statement in loopHeads) {
                        for (s in statement.condition.operands) {
                            val p = s.toPath()
                            if (p == fact.variable) {
                                val message = "Tainted loop operand"
                                val vulnerability = Vulnerability(message, sink = edge.to, edge = edge)
                                add(NewVulnerability(vulnerability))
                            }
                        }
                    }
                }
            }
        }
    }

    override fun handleCrossUnitCall(
        caller: TaintVertex,
        callee: TaintVertex,
    ): List<TaintEvent> = buildList {
        add(EdgeForOtherRunner(TaintEdge(callee, callee)))
    }
}

class BackwardTaintAnalyzer(
    private val graph: JcApplicationGraph,
) : Analyzer<TaintFact, TaintEvent> {

    override val flowFunctions: BackwardTaintFlowFunctions by lazy {
        BackwardTaintFlowFunctions(graph.classpath, graph)
    }

    private fun isExitPoint(statement: JcInst): Boolean {
        return statement in graph.exitPoints(statement.location.method)
    }

    private fun isSink(statement: JcInst, fact: TaintFact): Boolean {
        // TODO
        return false
    }

    override fun handleNewEdge(
        edge: TaintEdge,
    ): List<TaintEvent> = buildList {
        if (isExitPoint(edge.to.statement)) {
            add(EdgeForOtherRunner(Edge(edge.to, edge.to)))
        }
    }

    override fun handleCrossUnitCall(
        caller: TaintVertex,
        callee: TaintVertex,
    ): List<TaintEvent> {
        return emptyList()
    }
}
