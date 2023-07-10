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
import org.jacodb.analysis.engine.DomainFact
import org.jacodb.analysis.engine.FlowFunctionsSpace
import org.jacodb.analysis.engine.IfdsResult
import org.jacodb.analysis.engine.IfdsVertex
import org.jacodb.analysis.engine.SpaceId
import org.jacodb.analysis.engine.VulnerabilityLocation
import org.jacodb.analysis.engine.ZEROFact
import org.jacodb.analysis.paths.toPathOrNull
import org.jacodb.api.JcClasspath
import org.jacodb.api.analysis.JcApplicationGraph
import org.jacodb.api.cfg.JcExpr
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.cfg.JcValue


abstract class TaintAnalyzer(
    cp: JcClasspath,
    generates: (JcInst) -> List<DomainFact>,
    val isSink: (JcInst, DomainFact) -> Boolean,
    maxPathLength: Int = 5
) : Analyzer {
    override val flowFunctions: FlowFunctionsSpace = TaintForwardFunctions(cp, maxPathLength, generates)

    companion object : SpaceId {
        override val value: String = "taint analysis"
    }

    override fun getSummaryFactsPostIfds(ifdsResult: IfdsResult): List<VulnerabilityLocation> {
        val vulnerabilities = mutableListOf<VulnerabilityLocation>()
        ifdsResult.resultFacts.forEach { (inst, facts) ->
            facts.filterIsInstance<TaintAnalysisNode>().forEach { fact ->
                if (isSink(inst, fact)) {
                    fact.variable.let {
                        vulnerabilities.add(VulnerabilityLocation(value, IfdsVertex(inst, fact)))
                    }
                }
            }
        }
        return vulnerabilities
    }
}

class TaintBackwardAnalyzer(graph: JcApplicationGraph, maxPathLength: Int = 5) : Analyzer {
    override val saveSummaryEdgesAndCrossUnitCalls: Boolean
        get() = false

    override val flowFunctions: FlowFunctionsSpace = TaintBackwardFunctions(graph, maxPathLength)

    override fun getSummaryFactsPostIfds(ifdsResult: IfdsResult): List<VulnerabilityLocation> {
        error("Do not call sources for backward analyzer instance")
    }
}

private class TaintForwardFunctions(
    cp: JcClasspath,
    private val maxPathLength: Int,
    private val generates: (JcInst) -> List<DomainFact>,
) : AbstractTaintForwardFunctions(cp) {

    override val inIds: List<SpaceId> get() = listOf(TaintAnalyzer, ZEROFact.id)

    override fun transmitDataFlow(from: JcExpr, to: JcValue, atInst: JcInst, fact: DomainFact, dropFact: Boolean): List<DomainFact> {
        if (fact == ZEROFact) {
            return listOf(ZEROFact) + generates(atInst)
        }

        if (fact !is TaintAnalysisNode) {
            return emptyList()
        }

        val default = if (dropFact) emptyList() else listOf(fact)
        val toPath = to.toPathOrNull()?.limit(maxPathLength) ?: return default
        val fromPath = from.toPathOrNull()?.limit(maxPathLength) ?: return default

        return normalFactFlow(fact, fromPath, toPath, dropFact, maxPathLength)
    }

    override fun transmitDataFlowAtNormalInst(inst: JcInst, nextInst: JcInst, fact: DomainFact): List<DomainFact> {
        if (fact == ZEROFact) {
            return generates(inst) + listOf(ZEROFact)
        }
        return listOf(fact)
    }

    override fun obtainStartFacts(startStatement: JcInst): Collection<DomainFact> {
        return listOf(ZEROFact)
    }
}


private class TaintBackwardFunctions(
    graph: JcApplicationGraph,
    maxPathLength: Int,
) : AbstractTaintBackwardFunctions(graph, maxPathLength) {
    override val inIds: List<SpaceId> = listOf(TaintAnalyzer, ZEROFact.id)
}