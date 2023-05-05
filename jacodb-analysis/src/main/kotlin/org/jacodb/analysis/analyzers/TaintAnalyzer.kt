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

import org.jacodb.analysis.DumpableAnalysisResult
import org.jacodb.analysis.VulnerabilityInstance
import org.jacodb.analysis.engine.Analyzer
import org.jacodb.analysis.engine.DomainFact
import org.jacodb.analysis.engine.FlowFunctionsSpace
import org.jacodb.analysis.engine.IFDSResult
import org.jacodb.analysis.engine.IFDSVertex
import org.jacodb.analysis.engine.SpaceId
import org.jacodb.analysis.engine.ZEROFact
import org.jacodb.analysis.paths.FieldAccessor
import org.jacodb.analysis.paths.toPathOrNull
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcMethod
import org.jacodb.api.analysis.ApplicationGraph
import org.jacodb.api.analysis.JcAnalysisPlatform
import org.jacodb.api.cfg.JcArgument
import org.jacodb.api.cfg.JcExpr
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.cfg.JcLocal
import org.jacodb.api.cfg.JcValue
import org.jacodb.api.ext.cfg.allValues
import org.jacodb.api.ext.cfg.callExpr


// TODO: this is experimental raw implementation with hardcoded constants, rewrite it with proper interfaces
class TaintAnalyzer(
    classpath: JcClasspath,
    graph: ApplicationGraph<JcMethod, JcInst>,
    private val platform: JcAnalysisPlatform,
    generates: (JcInst) -> List<DomainFact>,
    maxPathLength: Int = 5
) : Analyzer {
    override val flowFunctions: FlowFunctionsSpace = TaintForwardFunctions(classpath, graph, platform, maxPathLength, generates)
    override val backward: Analyzer = object : Analyzer {
        override val backward: Analyzer
            get() = this@TaintAnalyzer
        override val flowFunctions: FlowFunctionsSpace
            get() = this@TaintAnalyzer.flowFunctions.backward

        override fun calculateSources(ifdsResult: IFDSResult): DumpableAnalysisResult {
            error("Do not call sources for backward analyzer instance")
        }
    }

    companion object : SpaceId {
        override val value: String = "taint analysis"
    }

    override fun calculateSources(ifdsResult: IFDSResult): DumpableAnalysisResult {
        val vulnerabilities = mutableListOf<VulnerabilityInstance>()
        ifdsResult.resultFacts.forEach { (inst, facts) ->
            facts.filterIsInstance<TaintAnalysisNode>().forEach { fact ->
                if (fact.activation == null && inst.callExpr?.method?.name == "test" && inst.callExpr?.method?.method?.enclosingClass?.simpleName == "Benchmark") {
                    fact.variable.let {

                        val name = when(val x = it.value) {
                            is JcArgument -> x.name
                            is JcLocal -> platform.flowGraph(inst.location.method).instructions
                                .first { x in it.operands.flatMap { it.allValues } }
                                .lineNumber
                                .toString()
                            null -> (it.accesses[0] as FieldAccessor).field.enclosingClass.simpleName
                        }

                        val fullPath = buildString {
                            append(name)
                            if (it.accesses.isNotEmpty()) {
                                append(".")
                            }
                            append(it.accesses.joinToString("."))
                        }

                        vulnerabilities.add(
                            ifdsResult.resolveTaintRealisationsGraph(IFDSVertex(inst, fact)).toVulnerability(value).copy(
                                sink = fullPath
                            )
                        )
                    }
                }
            }
        }
        return DumpableAnalysisResult(vulnerabilities)
    }
}

private class TaintForwardFunctions(
    classpath: JcClasspath,
    graph: ApplicationGraph<JcMethod, JcInst>,
    platform: JcAnalysisPlatform,
    maxPathLength: Int,
    private val generates: (JcInst) -> List<DomainFact>,
) : AbstractTaintForwardFunctions(classpath, graph, platform, maxPathLength) {

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

    override val backward: FlowFunctionsSpace by lazy { TaintBackwardFunctions(classpath, graph, platform, this, maxPathLength) }
}


private class TaintBackwardFunctions(
    classpath: JcClasspath,
    graph: ApplicationGraph<JcMethod, JcInst>,
    platform: JcAnalysisPlatform,
    backward: FlowFunctionsSpace,
    maxPathLength: Int,
) : AbstractTaintBackwardFunctions(classpath, graph, platform, backward, maxPathLength) {
    override val inIds: List<SpaceId> = listOf(TaintAnalyzer, ZEROFact.id)
}