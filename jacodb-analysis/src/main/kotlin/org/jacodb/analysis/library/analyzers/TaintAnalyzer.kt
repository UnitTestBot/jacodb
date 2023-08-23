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

import org.jacodb.analysis.engine.AbstractAnalyzer
import org.jacodb.analysis.engine.AnalyzerFactory
import org.jacodb.analysis.engine.DomainFact
import org.jacodb.analysis.engine.FlowFunctionsSpace
import org.jacodb.analysis.engine.IfdsEdge
import org.jacodb.analysis.engine.IfdsUnitCommunicator
import org.jacodb.analysis.engine.VulnerabilityLocation
import org.jacodb.analysis.engine.ZEROFact
import org.jacodb.analysis.paths.AccessPath
import org.jacodb.analysis.paths.minus
import org.jacodb.analysis.paths.startsWith
import org.jacodb.analysis.paths.toPath
import org.jacodb.analysis.paths.toPathOrNull
import org.jacodb.api.JcMethod
import org.jacodb.api.analysis.JcApplicationGraph
import org.jacodb.api.cfg.JcArgument
import org.jacodb.api.cfg.JcAssignInst
import org.jacodb.api.cfg.JcCallExpr
import org.jacodb.api.cfg.JcExpr
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.cfg.JcInstanceCallExpr
import org.jacodb.api.cfg.JcValue
import org.jacodb.api.cfg.locals
import org.jacodb.api.cfg.values
import org.jacodb.api.ext.cfg.callExpr

private fun isSourceMethodToGenerates(isSourceMethod: (JcMethod) -> Boolean): (JcInst) -> List<TaintAnalysisNode> {
    return generates@{ inst: JcInst ->
        val callExpr = inst.callExpr?.takeIf { isSourceMethod(it.method.method) } ?: return@generates emptyList()
        if (inst is JcAssignInst && isSourceMethod(callExpr.method.method)) {
            listOf(TaintAnalysisNode(inst.lhv.toPath()))
        } else {
            emptyList()
        }
    }
}

private fun isSinkMethodToSinks(isSinkMethod: (JcMethod) -> Boolean): (JcInst) -> List<TaintAnalysisNode> {
    return sinks@{ inst: JcInst ->
        val callExpr = inst.callExpr?.takeIf { isSinkMethod(it.method.method) } ?: return@sinks emptyList()
        callExpr.values
            .mapNotNull { it.toPathOrNull() }
            .map { TaintAnalysisNode(it) }
    }
}

fun TaintAnalyzerFactory(
    isSourceMethod: (JcMethod) -> Boolean,
    isSanitizeMethod: (JcMethod) -> Boolean,
    isSinkMethod: (JcMethod) -> Boolean,
    maxPathLength: Int
) = AnalyzerFactory { graph ->
    val generates = isSourceMethodToGenerates(isSourceMethod)
    val sinks = isSinkMethodToSinks(isSinkMethod)

    val sanitizes = { expr: JcExpr, fact: TaintNode ->
        if (expr !is JcCallExpr) {
            false
        } else {
            if (isSanitizeMethod(expr.method.method) && fact.activation == null) {
                expr.values.any {
                    it.toPathOrNull().startsWith(fact.variable) || fact.variable.startsWith(it.toPathOrNull())
                }
            } else {
                false
            }
        }
    }

    TaintAnalyzer(graph, generates, sanitizes, sinks, maxPathLength)
}

private val List<String>.asMethodMatchers: (JcMethod) -> Boolean
    get() = { method: JcMethod ->
        any { it.toRegex().matches("${method.enclosingClass.name}#${method.name}") }
    }

fun TaintAnalyzerFactory(
    sourceMethodMatchers: List<String>,
    sanitizeMethodMatchers: List<String>,
    sinkMethodMatchers: List<String>,
    maxPathLength: Int
) = TaintAnalyzerFactory(
    sourceMethodMatchers.asMethodMatchers,
    sanitizeMethodMatchers.asMethodMatchers,
    sinkMethodMatchers.asMethodMatchers,
    maxPathLength
)

open class TaintAnalyzer(
    graph: JcApplicationGraph,
    generates: (JcInst) -> List<DomainFact>,
    sanitizes: (JcExpr, TaintNode) -> Boolean,
    val sinks: (JcInst) -> List<TaintAnalysisNode>,
    maxPathLength: Int
) : AbstractAnalyzer(graph) {
    override val flowFunctions: FlowFunctionsSpace = TaintForwardFunctions(graph, maxPathLength, generates, sanitizes)

    override val saveSummaryEdgesAndCrossUnitCalls: Boolean
        get() = true

    companion object {
        const val vulnerabilityType: String = "taint analysis"
    }

    override fun handleNewEdge(edge: IfdsEdge, manager: IfdsUnitCommunicator) {
        if (edge.v.domainFact in sinks(edge.v.statement)) {
            manager.uploadSummaryFact(VulnerabilityLocation(vulnerabilityType, edge.v))
            verticesWithTraceGraphNeeded.add(edge.v)
        }
    }
}

fun TaintBackwardAnalyzerFactory(
    isSourceMethod: (JcMethod) -> Boolean,
    isSinkMethod: (JcMethod) -> Boolean,
    maxPathLength: Int
) = AnalyzerFactory { graph ->
    val generates = isSourceMethodToGenerates(isSourceMethod)
    val sinks = isSinkMethodToSinks(isSinkMethod)

    TaintBackwardAnalyzer(graph, generates, sinks, maxPathLength)
}

fun TaintBackwardAnalyzerFactory(
    sourceMethodMatchers: List<String>,
    sinkMethodMatchers: List<String>,
    maxPathLength: Int
) = TaintBackwardAnalyzerFactory(
    sourceMethodMatchers.asMethodMatchers,
    sinkMethodMatchers.asMethodMatchers,
    maxPathLength
)

private class TaintBackwardAnalyzer(
    val graph: JcApplicationGraph,
    generates: (JcInst) -> List<DomainFact>,
    sinks: (JcInst) -> List<TaintAnalysisNode>,
    maxPathLength: Int
) : AbstractAnalyzer(graph) {
    override val saveSummaryEdgesAndCrossUnitCalls: Boolean
        get() = false

    override val flowFunctions: FlowFunctionsSpace = TaintBackwardFunctions(graph, generates, sinks, maxPathLength)

    override fun handleNewEdge(edge: IfdsEdge, manager: IfdsUnitCommunicator) {
        if (edge.v.statement in graph.exitPoints(edge.method)) {
            manager.addEdgeForOtherRunner(IfdsEdge(edge.v, edge.v))
        }
    }
}

private class TaintForwardFunctions(
    graph: JcApplicationGraph,
    private val maxPathLength: Int,
    private val generates: (JcInst) -> List<DomainFact>,
    private val sanitizes: (JcExpr, TaintNode) -> Boolean
) : AbstractTaintForwardFunctions(graph.classpath) {
    override fun transmitDataFlow(from: JcExpr, to: JcValue, atInst: JcInst, fact: DomainFact, dropFact: Boolean): List<DomainFact> {
        if (fact == ZEROFact) {
            return listOf(ZEROFact) + generates(atInst)
        }

        if (fact !is TaintNode) {
            return emptyList()
        }

        val default = if (dropFact || (sanitizes(from, fact) && fact.variable == (from as? JcInstanceCallExpr)?.instance?.toPath())) {
            emptyList()
        } else {
            listOf(fact)
        }

        val toPath = to.toPathOrNull()?.limit(maxPathLength) ?: return default
        val newPossibleTaint = if (sanitizes(from, fact)) emptyList() else listOf(fact.moveToOtherPath(toPath))

        val fromPath = from.toPathOrNull()
        if (fromPath != null) {
            return if (sanitizes(from, fact)) {
                default
            } else if (fromPath.startsWith(fact.variable)) {
                default + newPossibleTaint
            } else {
                normalFactFlow(fact, fromPath, toPath, dropFact, maxPathLength)
            }
        }

        return if (from.values.any { it.toPathOrNull().startsWith(fact.variable) || fact.variable.startsWith(it.toPathOrNull()) }) {
            val instanceOrNull = (from as? JcInstanceCallExpr)?.instance
            if (instanceOrNull != null && !sanitizes(from, fact)) {
                default + newPossibleTaint + fact.moveToOtherPath(instanceOrNull.toPath())
            } else {
                default + newPossibleTaint
            }
        } else if (fact.variable.startsWith(toPath)) {
            emptyList()
        } else {
            default
        }
    }

    override fun transmitDataFlowAtNormalInst(inst: JcInst, nextInst: JcInst, fact: DomainFact): List<DomainFact> {
        if (fact == ZEROFact) {
            return generates(inst) + listOf(ZEROFact)
        }

        if (fact !is TaintNode) {
            return emptyList()
        }

        val callExpr = inst.callExpr ?: return listOf(fact)
        val instance = (callExpr as? JcInstanceCallExpr)?.instance ?: return listOf(fact)
        val factIsPassed = callExpr.values.any {
            it.toPathOrNull().startsWith(fact.variable) || fact.variable.startsWith(it.toPathOrNull())
        }

        if (instance.toPath() == fact.variable && sanitizes(callExpr, fact)) {
            return emptyList()
        }

        return if (factIsPassed && !sanitizes(callExpr, fact)) {
            listOf(fact) + fact.moveToOtherPath(instance.toPath())
        } else {
            listOf(fact)
        }
    }

    override fun obtainPossibleStartFacts(startStatement: JcInst): Collection<DomainFact> {
        val method = startStatement.location.method

        // Possibly null arguments
        return listOf(ZEROFact) + method.flowGraph().locals
            .filterIsInstance<JcArgument>()
            .map { TaintAnalysisNode(AccessPath.fromLocal(it)) }
    }
}


private class TaintBackwardFunctions(
    graph: JcApplicationGraph,
    val generates: (JcInst) -> List<DomainFact>,
    val sinks: (JcInst) -> List<TaintAnalysisNode>,
    maxPathLength: Int,
) : AbstractTaintBackwardFunctions(graph, maxPathLength) {
    override fun transmitBackDataFlow(from: JcValue, to: JcExpr, atInst: JcInst, fact: DomainFact, dropFact: Boolean): List<DomainFact> {
        if (fact == ZEROFact) {
            return listOf(ZEROFact) + sinks(atInst)
        }

        if (fact !is TaintAnalysisNode) {
            return emptyList()
        }

        val factPath = fact.variable
        val default = if (dropFact || fact in generates(atInst)) emptyList() else listOf(fact)
        val fromPath = from.toPathOrNull() ?: return default
        val toPath = to.toPathOrNull()

        if (toPath != null) {
            val diff = factPath.minus(fromPath)
            if (diff != null) {
                return listOf(fact.moveToOtherPath(AccessPath.fromOther(toPath, diff).limit(maxPathLength)))
            }
        } else if (factPath.startsWith(fromPath) || (to is JcInstanceCallExpr && factPath.startsWith(to.instance.toPath()))) {
            return to.values.mapNotNull { it.toPathOrNull() }.map { TaintAnalysisNode(it) }
        }
        return default
    }

    override fun transmitDataFlowAtNormalInst(inst: JcInst, nextInst: JcInst, fact: DomainFact): List<DomainFact> {
        if (fact == ZEROFact) {
            return listOf(fact) + sinks(inst)
        }
        if (fact !is TaintAnalysisNode) {
            return emptyList()
        }

        val callExpr = inst.callExpr as? JcInstanceCallExpr ?: return listOf(fact)
        if (fact.variable.startsWith(callExpr.instance.toPath())) {
            return inst.values.mapNotNull { it.toPathOrNull() }.map { TaintAnalysisNode(it) }
        }

        return listOf(fact)
    }
}