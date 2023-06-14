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

import org.jacodb.analysis.AnalysisResult
import org.jacodb.analysis.VulnerabilityInstance
import org.jacodb.analysis.engine.Analyzer
import org.jacodb.analysis.engine.DomainFact
import org.jacodb.analysis.engine.FlowFunctionsSpace
import org.jacodb.analysis.engine.IFDSResult
import org.jacodb.analysis.engine.IFDSVertex
import org.jacodb.analysis.engine.SpaceId
import org.jacodb.analysis.engine.ZEROFact
import org.jacodb.analysis.paths.toPathOrNull
import org.jacodb.api.analysis.JcApplicationGraph
import org.jacodb.api.cfg.JcExpr
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.cfg.JcValue


abstract class TaintAnalyzer(
    graph: JcApplicationGraph,
    generates: (JcInst) -> List<DomainFact>,
    val isSink: (JcInst, DomainFact) -> Boolean,
    maxPathLength: Int = 5
) : Analyzer {
    override val flowFunctions: FlowFunctionsSpace = TaintForwardFunctions(graph, maxPathLength, generates)
    override val backward: Analyzer = object : Analyzer {
        override val name: String
            get() = this@TaintAnalyzer.name

        override val backward: Analyzer
            get() = this@TaintAnalyzer
        override val flowFunctions: FlowFunctionsSpace
            get() = TaintBackwardFunctions(graph, maxPathLength)

        override fun calculateSources(ifdsResult: IFDSResult): AnalysisResult {
            error("Do not call sources for backward analyzer instance")
        }
    }

    companion object : SpaceId {
        override val value: String = "taint analysis"
    }

    override fun calculateSources(ifdsResult: IFDSResult): AnalysisResult {
        val vulnerabilities = mutableListOf<VulnerabilityInstance>()
        ifdsResult.resultFacts.forEach { (inst, facts) ->
            facts.filterIsInstance<TaintAnalysisNode>().forEach { fact ->
                if (isSink(inst, fact)) {
                    fact.variable.let {
                        vulnerabilities.add(
                            VulnerabilityInstance(
                                value,
                                ifdsResult.resolveTaintRealisationsGraph(IFDSVertex(inst, fact))
                            )
                        )
                    }
                }
            }
        }
        return AnalysisResult(vulnerabilities)
    }
}

private class TaintForwardFunctions(
    graph: JcApplicationGraph,
    private val maxPathLength: Int,
    private val generates: (JcInst) -> List<DomainFact>,
) : AbstractTaintForwardFunctions(graph) {

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