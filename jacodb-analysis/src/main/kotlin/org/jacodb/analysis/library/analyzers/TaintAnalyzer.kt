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
import org.jacodb.analysis.engine.AnalysisDependentEvent
import org.jacodb.analysis.engine.DomainFact
import org.jacodb.analysis.engine.EdgeForOtherRunnerQuery
import org.jacodb.analysis.engine.FlowFunctionsSpace
import org.jacodb.analysis.engine.IfdsEdge
import org.jacodb.analysis.engine.IfdsVertex
import org.jacodb.analysis.engine.NewSummaryFact
import org.jacodb.analysis.engine.VulnerabilityLocation
import org.jacodb.analysis.engine.ZEROFact
import org.jacodb.analysis.paths.AccessPath
import org.jacodb.analysis.paths.minus
import org.jacodb.analysis.paths.startsWith
import org.jacodb.analysis.paths.toPath
import org.jacodb.analysis.paths.toPathOrNull
import org.jacodb.analysis.sarif.VulnerabilityDescription
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.analysis.JcApplicationGraph
import org.jacodb.api.jvm.cfg.JcArgument
import org.jacodb.api.jvm.cfg.JcAssignInst
import org.jacodb.api.jvm.cfg.JcCallExpr
import org.jacodb.api.jvm.cfg.JcExpr
import org.jacodb.api.jvm.cfg.JcInst
import org.jacodb.api.jvm.cfg.JcInstanceCallExpr
import org.jacodb.api.jvm.cfg.JcValue
import org.jacodb.api.jvm.cfg.locals
import org.jacodb.api.jvm.cfg.values
import org.jacodb.api.jvm.ext.cfg.callExpr

fun isSourceMethodToGenerates(isSourceMethod: (JcMethod) -> Boolean): (JcInst) -> List<TaintAnalysisNode> {
    return generates@{ inst: JcInst ->
        val callExpr = inst.callExpr?.takeIf { isSourceMethod(it.method.method) } ?: return@generates emptyList()
        if (inst is JcAssignInst && isSourceMethod(callExpr.method.method)) {
            listOf(TaintAnalysisNode(inst.lhv.toPath()))
        } else {
            emptyList()
        }
    }
}

fun isSinkMethodToSinks(isSinkMethod: (JcMethod) -> Boolean): (JcInst) -> List<TaintAnalysisNode> {
    return sinks@{ inst: JcInst ->
        val callExpr = inst.callExpr?.takeIf { isSinkMethod(it.method.method) } ?: return@sinks emptyList()
        callExpr.values
            .mapNotNull { it.toPathOrNull() }
            .map { TaintAnalysisNode(it) }
    }
}

fun isSanitizeMethodToSanitizes(isSanitizeMethod: (JcMethod) -> Boolean): (JcExpr, TaintNode) -> Boolean {
    return { expr: JcExpr, fact: TaintNode ->
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
}

internal val List<String>.asMethodMatchers: (JcMethod) -> Boolean
    get() = { method: JcMethod ->
        any { it.toRegex().matches("${method.enclosingClass.name}#${method.name}") }
    }

abstract class TaintAnalyzer(
    graph: JcApplicationGraph,
    maxPathLength: Int
) : AbstractAnalyzer(graph) {
    abstract val generates: (JcInst) -> List<DomainFact>
    abstract val sanitizes: (JcExpr, TaintNode) -> Boolean
    abstract val sinks: (JcInst) -> List<TaintAnalysisNode>

    override val flowFunctions: FlowFunctionsSpace by lazy {
        TaintForwardFunctions(graph, maxPathLength, generates, sanitizes)
    }

    override val isMainAnalyzer: Boolean
        get() = true

    protected abstract fun generateDescriptionForSink(sink: IfdsVertex): VulnerabilityDescription

    override fun handleNewEdge(edge: IfdsEdge): List<AnalysisDependentEvent> = buildList {
        if (edge.v.domainFact in sinks(edge.v.statement)) {
            val desc = generateDescriptionForSink(edge.v)
            add(NewSummaryFact(VulnerabilityLocation(desc, edge.v)))
            verticesWithTraceGraphNeeded.add(edge.v)
        }
    }
}

abstract class TaintBackwardAnalyzer(
    val graph: JcApplicationGraph,
    maxPathLength: Int
) : AbstractAnalyzer(graph) {
    abstract val generates: (JcInst) -> List<DomainFact>
    abstract val sinks: (JcInst) -> List<TaintAnalysisNode>

    override val isMainAnalyzer: Boolean
        get() = false

    override val flowFunctions: FlowFunctionsSpace by lazy {
        TaintBackwardFunctions(graph, generates, sinks, maxPathLength)
    }

    override fun handleNewEdge(edge: IfdsEdge): List<AnalysisDependentEvent> = buildList {
        if (edge.v.statement in graph.exitPoints(edge.method)) {
            add(EdgeForOtherRunnerQuery(IfdsEdge(edge.v, edge.v)))
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

        if (from.values.any { it.toPathOrNull().startsWith(fact.variable) || fact.variable.startsWith(it.toPathOrNull()) }) {
            val instanceOrNull = (from as? JcInstanceCallExpr)?.instance
            if (instanceOrNull != null && !sanitizes(from, fact)) {
                val instancePath = instanceOrNull.toPathOrNull()
                if (instancePath != null) {
                    return default + newPossibleTaint + fact.moveToOtherPath(instancePath)
                }
            }
            return default + newPossibleTaint
        } else if (fact.variable.startsWith(toPath)) {
            return emptyList()
        }
        return default
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