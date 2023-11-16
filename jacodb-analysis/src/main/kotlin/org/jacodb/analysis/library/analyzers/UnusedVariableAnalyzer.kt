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
import org.jacodb.analysis.engine.AnalyzerFactory
import org.jacodb.analysis.engine.CrossUnitCallFact
import org.jacodb.analysis.engine.DomainFact
import org.jacodb.analysis.engine.FlowFunctionInstance
import org.jacodb.analysis.engine.FlowFunctionsSpace
import org.jacodb.analysis.engine.IfdsResult
import org.jacodb.analysis.engine.IfdsVertex
import org.jacodb.analysis.engine.NewSummaryFact
import org.jacodb.analysis.engine.VulnerabilityLocation
import org.jacodb.analysis.engine.ZEROFact
import org.jacodb.analysis.paths.AccessPath
import org.jacodb.analysis.paths.toPath
import org.jacodb.analysis.paths.toPathOrNull
import org.jacodb.analysis.sarif.SarifMessage
import org.jacodb.analysis.sarif.VulnerabilityDescription
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcMethod
import org.jacodb.api.analysis.JcApplicationGraph
import org.jacodb.api.cfg.JcArrayAccess
import org.jacodb.api.cfg.JcAssignInst
import org.jacodb.api.cfg.JcBranchingInst
import org.jacodb.api.cfg.JcExpr
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.cfg.JcLocal
import org.jacodb.api.cfg.JcSpecialCallExpr
import org.jacodb.api.cfg.JcStaticCallExpr
import org.jacodb.api.cfg.JcTerminatingInst
import org.jacodb.api.cfg.values
import org.jacodb.api.ext.cfg.callExpr

class UnusedVariableAnalyzer(graph: JcApplicationGraph) : AbstractAnalyzer(graph) {
    override val flowFunctions: FlowFunctionsSpace = UnusedVariableForwardFunctions(graph.classpath)

    override val isMainAnalyzer: Boolean
        get() = true

    companion object {
        const val ruleId: String = "unused-variable"
        private val vulnerabilityMessage = SarifMessage("Assigned value is unused")

        val vulnerabilityDescription = VulnerabilityDescription(vulnerabilityMessage, ruleId)
    }

    private fun AccessPath.isUsedAt(expr: JcExpr): Boolean {
        return this in expr.values.map { it.toPathOrNull() }
    }

    private fun AccessPath.isUsedAt(inst: JcInst): Boolean {
        val callExpr = inst.callExpr

        if (callExpr != null) {
            // Don't count constructor calls as usages
            if (callExpr.method.method.isConstructor && isUsedAt((callExpr as JcSpecialCallExpr).instance)) {
                return false
            }

            return isUsedAt(callExpr)
        }
        if (inst is JcAssignInst) {
            if (inst.lhv is JcArrayAccess && isUsedAt(inst.lhv as JcArrayAccess)) {
                return true
            }
            return isUsedAt(inst.rhv) && (inst.lhv !is JcLocal || inst.rhv !is JcLocal)
        }
        if (inst is JcTerminatingInst || inst is JcBranchingInst) {
            return inst.operands.any { isUsedAt(it) }
        }
        return false
    }

    override fun handleNewCrossUnitCall(fact: CrossUnitCallFact): List<AnalysisDependentEvent> {
        return emptyList()
    }

    override fun handleIfdsResult(ifdsResult: IfdsResult): List<AnalysisDependentEvent> = buildList {
        val used: MutableMap<JcInst, Boolean> = mutableMapOf()
        ifdsResult.resultFacts.forEach { (inst, facts) ->
            facts.filterIsInstance<UnusedVariableNode>().forEach { fact ->
                if (fact.initStatement !in used) {
                    used[fact.initStatement] = false
                }

                if (fact.variable.isUsedAt(inst)) {
                    used[fact.initStatement] = true
                }
            }
        }
        used.filterValues { !it }.keys.map {
            add(
                NewSummaryFact(VulnerabilityLocation(vulnerabilityDescription, IfdsVertex(it, ZEROFact)))
            )
        }
    }
}

val UnusedVariableAnalyzerFactory = AnalyzerFactory { graph ->
    UnusedVariableAnalyzer(graph)
}

private class UnusedVariableForwardFunctions(
    val classpath: JcClasspath,
) : FlowFunctionsSpace {

    override fun obtainPossibleStartFacts(startStatement: JcInst): Collection<DomainFact> {
        return listOf(ZEROFact)
    }

    override fun obtainSequentFlowFunction(current: JcInst, next: JcInst) = FlowFunctionInstance { fact ->
        if (current !is JcAssignInst) {
            return@FlowFunctionInstance listOf(fact)
        }

        if (fact == ZEROFact) {
            val toPath = current.lhv.toPathOrNull() ?: return@FlowFunctionInstance listOf(ZEROFact)
            return@FlowFunctionInstance if (!toPath.isOnHeap) {
                listOf(ZEROFact, UnusedVariableNode(toPath, current))
            } else {
                listOf(ZEROFact)
            }
        }

        if (fact !is UnusedVariableNode) {
            return@FlowFunctionInstance emptyList()
        }

        val default = if (fact.variable == current.lhv.toPathOrNull()) emptyList() else listOf(fact)
        val fromPath = current.rhv.toPathOrNull() ?: return@FlowFunctionInstance default
        val toPath = current.lhv.toPathOrNull() ?: return@FlowFunctionInstance default

        if (fromPath.isOnHeap || toPath.isOnHeap) {
            return@FlowFunctionInstance default
        }

        if (fromPath == fact.variable) {
            return@FlowFunctionInstance default + UnusedVariableNode(toPath, fact.initStatement)
        }

        default
    }

    override fun obtainCallToStartFlowFunction(callStatement: JcInst, callee: JcMethod) = FlowFunctionInstance { fact ->
        val callExpr = callStatement.callExpr ?: error("Call expr is expected to be not-null")
        val formalParams = classpath.getFormalParamsOf(callee)

        if (fact == ZEROFact) {
            // We don't show unused parameters for virtual calls
            if (callExpr !is JcStaticCallExpr && callExpr !is JcSpecialCallExpr) {
                return@FlowFunctionInstance listOf(ZEROFact)
            }
            return@FlowFunctionInstance formalParams.map { UnusedVariableNode(it.toPath(), callStatement) } + ZEROFact
        }

        if (fact !is UnusedVariableNode) {
            return@FlowFunctionInstance emptyList()
        }

        emptyList()
    }

    override fun obtainCallToReturnFlowFunction(callStatement: JcInst, returnSite: JcInst) =
        obtainSequentFlowFunction(callStatement, returnSite)

    override fun obtainExitToReturnSiteFlowFunction(
        callStatement: JcInst,
        returnSite: JcInst,
        exitStatement: JcInst,
    ) = FlowFunctionInstance { fact ->
        if (fact == ZEROFact) {
            listOf(ZEROFact)
        } else {
            emptyList()
        }
    }
}
