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

package org.jacodb.analysis.unused

import org.jacodb.analysis.ifds.FlowFunction
import org.jacodb.analysis.ifds.FlowFunctions
import org.jacodb.analysis.ifds.toPath
import org.jacodb.analysis.ifds.toPathOrNull
import org.jacodb.analysis.util.getArgumentsOf
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcMethod
import org.jacodb.api.analysis.JcApplicationGraph
import org.jacodb.api.cfg.JcAssignInst
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.cfg.JcSpecialCallExpr
import org.jacodb.api.cfg.JcStaticCallExpr
import org.jacodb.api.ext.cfg.callExpr

class UnusedVariableFlowFunctions(
    private val graph: JcApplicationGraph,
) : FlowFunctions<UnusedVariableDomainFact> {
    private val cp: JcClasspath
        get() = graph.classpath

    override fun obtainPossibleStartFacts(
        method: JcMethod,
    ): Collection<UnusedVariableDomainFact> {
        return setOf(UnusedVariableZeroFact)
    }

    override fun obtainSequentFlowFunction(
        current: JcInst,
        next: JcInst,
    ) = FlowFunction<UnusedVariableDomainFact> { fact ->
        if (current !is JcAssignInst) {
            return@FlowFunction setOf(fact)
        }

        if (fact == UnusedVariableZeroFact) {
            val toPath = current.lhv.toPath()
            if (!toPath.isOnHeap) {
                return@FlowFunction setOf(UnusedVariableZeroFact, UnusedVariable(toPath, current))
            } else {
                return@FlowFunction setOf(UnusedVariableZeroFact)
            }
        }
        check(fact is UnusedVariable)

        val toPath = current.lhv.toPath()
        val default = if (toPath == fact.variable) emptySet() else setOf(fact)
        val fromPath = current.rhv.toPathOrNull()
            ?: return@FlowFunction default

        if (fromPath.isOnHeap || toPath.isOnHeap) {
            return@FlowFunction default
        }

        if (fromPath == fact.variable) {
            return@FlowFunction default + fact.copy(variable = toPath)
        }

        default
    }

    override fun obtainCallToReturnSiteFlowFunction(
        callStatement: JcInst,
        returnSite: JcInst,
    ) = obtainSequentFlowFunction(callStatement, returnSite)

    override fun obtainCallToStartFlowFunction(
        callStatement: JcInst,
        calleeStart: JcInst,
    ) = FlowFunction<UnusedVariableDomainFact> { fact ->
        val callExpr = callStatement.callExpr
            ?: error("Call statement should have non-null callExpr")

        if (fact == UnusedVariableZeroFact) {
            if (callExpr !is JcStaticCallExpr && callExpr !is JcSpecialCallExpr) {
                return@FlowFunction setOf(UnusedVariableZeroFact)
            }
            return@FlowFunction buildSet {
                add(UnusedVariableZeroFact)
                val callee = calleeStart.location.method
                val formalParams = cp.getArgumentsOf(callee)
                for (formal in formalParams) {
                    add(UnusedVariable(formal.toPath(), callStatement))
                }
            }
        }
        check(fact is UnusedVariable)

        emptySet()
    }

    override fun obtainExitToReturnSiteFlowFunction(
        callStatement: JcInst,
        returnSite: JcInst,
        exitStatement: JcInst,
    ) = FlowFunction<UnusedVariableDomainFact> { fact ->
        if (fact == UnusedVariableZeroFact) {
            setOf(UnusedVariableZeroFact)
        } else {
            emptySet()
        }
    }
}
