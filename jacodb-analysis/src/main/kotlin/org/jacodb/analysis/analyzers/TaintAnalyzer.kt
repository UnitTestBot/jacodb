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

package org.jacodb.analysis.analyzers

import org.jacodb.analysis.engine.Analyzer
import org.jacodb.analysis.engine.AnalyzerFactory
import org.jacodb.analysis.engine.DomainFact
import org.jacodb.analysis.engine.FlowFunctionsSpace
import org.jacodb.analysis.engine.IfdsEdge
import org.jacodb.analysis.engine.IfdsResult
import org.jacodb.analysis.engine.IfdsVertex
import org.jacodb.analysis.engine.SummaryFact
import org.jacodb.analysis.engine.VulnerabilityLocation
import org.jacodb.analysis.engine.ZEROFact
import org.jacodb.analysis.paths.startsWith
import org.jacodb.analysis.paths.toPath
import org.jacodb.analysis.paths.toPathOrNull
import org.jacodb.api.JcMethod
import org.jacodb.api.analysis.JcApplicationGraph
import org.jacodb.api.cfg.JcAssignInst
import org.jacodb.api.cfg.JcExpr
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.cfg.JcInstanceCallExpr
import org.jacodb.api.cfg.JcValue
import org.jacodb.api.cfg.values
import org.jacodb.api.ext.cfg.callExpr

fun TaintAnalyzerFactory(
    isSourceMethod: (JcMethod) -> Boolean,
    isSinkMethod: (JcMethod) -> Boolean,
    maxPathLength: Int,
    spreading: Boolean
) = AnalyzerFactory { graph ->
    val generates = { inst: JcInst ->
        val callExpr = inst.callExpr
        if (inst is JcAssignInst && callExpr != null && isSourceMethod(callExpr.method.method)) {
            listOf(TaintAnalysisNode(inst.lhv.toPath()))
        } else {
            emptyList()
        }
    }

    val isSink = { v: IfdsVertex ->
        val callExpr = v.statement.callExpr
        val fact = v.domainFact

        if (callExpr != null && isSinkMethod(callExpr.method.method) && fact is TaintAnalysisNode && fact.activation == null) {
            callExpr.values.any {
                if (spreading) {
                    it.toPathOrNull().startsWith(fact.variable) || fact.variable.startsWith(it.toPathOrNull())
                } else {
                    fact.variable == it.toPathOrNull()
                }
            }
        } else {
            false
        }
    }
    TaintAnalyzer(graph, generates, isSink, maxPathLength, spreading)
}

fun TaintAnalyzerFactory(
    sourceMethodMatchers: List<String>,
    sinkMethodMatchers: List<String>,
    maxPathLength: Int,
    spreading: Boolean
) = TaintAnalyzerFactory(
    { method: JcMethod -> sourceMethodMatchers.any { it.toRegex().matches("${method.enclosingClass.name}#${method.name}") } },
    { method: JcMethod -> sinkMethodMatchers.any { it.toRegex().matches("${method.enclosingClass.name}#${method.name}") } },
    maxPathLength,
    spreading
)

open class TaintAnalyzer(
    graph: JcApplicationGraph,
    generates: (JcInst) -> List<DomainFact>,
    val isSink: (IfdsVertex) -> Boolean,
    maxPathLength: Int,
    spreading: Boolean,
) : Analyzer {
    override val flowFunctions: FlowFunctionsSpace = TaintForwardFunctions(graph, maxPathLength, spreading, generates)

    companion object {
        const val vulnerabilityType: String = "taint analysis"
    }

    override fun getSummaryFacts(edge: IfdsEdge): List<SummaryFact> {
        return if (isSink(edge.v)) {
            listOf(VulnerabilityLocation(vulnerabilityType, edge.v))
        } else {
            emptyList()
        }
    }
}

fun TaintBackwardAnalyzerFactory(maxPathLength: Int = 5) = AnalyzerFactory { graph ->
    TaintBackwardAnalyzer(graph, maxPathLength)
}

private class TaintBackwardAnalyzer(graph: JcApplicationGraph, maxPathLength: Int) : Analyzer {
    override val saveSummaryEdgesAndCrossUnitCalls: Boolean
        get() = false

    override val flowFunctions: FlowFunctionsSpace = TaintBackwardFunctions(graph, maxPathLength)

    override fun getSummaryFactsPostIfds(ifdsResult: IfdsResult): List<VulnerabilityLocation> {
        error("Do not call sources for backward analyzer instance")
    }
}

private class TaintForwardFunctions(
    private val graph: JcApplicationGraph,
    private val maxPathLength: Int,
    private val spreading: Boolean,
    private val generates: (JcInst) -> List<DomainFact>,
) : AbstractTaintForwardFunctions(graph.classpath) {

    // TODO: this is ad-hoc extension, maybe we need more general approach
    private val JcMethod.isInstanceModifier: Boolean
        get() = false // name.startsWith("add") //|| name.startsWith("set") || name.startsWith("update")

    override fun transmitDataFlow(from: JcExpr, to: JcValue, atInst: JcInst, fact: DomainFact, dropFact: Boolean): List<DomainFact> {
        if (fact == ZEROFact) {
            return listOf(ZEROFact) + generates(atInst)
        }

        if (fact !is TaintAnalysisNode) {
            return emptyList()
        }

        val default = if (dropFact) emptyList() else listOf(fact)
        val toPath = to.toPathOrNull()?.limit(maxPathLength) ?: return default

        if (!spreading) {
            val fromPath = from.toPathOrNull()?.limit(maxPathLength) ?: return default
            return normalFactFlow(fact, fromPath, toPath, dropFact, maxPathLength)
        }

        val fromPath = from.toPathOrNull()
        if (fromPath != null) {
            return if (fromPath.startsWith(fact.variable)) {
                default + fact.moveToOtherPath(toPath)
            } else {
                normalFactFlow(fact, fromPath, toPath, dropFact, maxPathLength)
            }
        }

        return if (from.values.any { it.toPathOrNull().startsWith(fact.variable) || fact.variable.startsWith(it.toPathOrNull()) }) {
            val instanceOrNull = (from as? JcInstanceCallExpr)?.instance
            if (instanceOrNull != null && from.method.method.isInstanceModifier) {
                default + fact.moveToOtherPath(toPath) + fact.moveToOtherPath(instanceOrNull.toPath())
            } else {
                default + fact.moveToOtherPath(toPath)
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

        // TODO: this is very over-approximative, maybe it can be improved
        if (graph.callees(inst).toList().isNotEmpty() || !callExpr.method.method.isInstanceModifier) {
            return listOf(fact)
        }

        return if (callExpr.values.any { it.toPathOrNull().startsWith(fact.variable) || fact.variable.startsWith(it.toPathOrNull()) }) {
            listOf(fact) + fact.moveToOtherPath(instance.toPath())
        } else {
            listOf(fact)
        }
    }

    override fun obtainPossibleStartFacts(startStatement: JcInst): Collection<DomainFact> {
        return listOf(ZEROFact)
    }
}


private class TaintBackwardFunctions(
    graph: JcApplicationGraph,
    maxPathLength: Int,
) : AbstractTaintBackwardFunctions(graph, maxPathLength)