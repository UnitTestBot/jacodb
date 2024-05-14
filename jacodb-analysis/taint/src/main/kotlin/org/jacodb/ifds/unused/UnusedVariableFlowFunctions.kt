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

package org.jacodb.ifds.unused

import org.jacodb.ifds.common.FlowFunctions
import org.jacodb.ifds.domain.toPath
import org.jacodb.ifds.domain.toPathOrNull
import org.jacodb.ifds.util.getArgumentsOf
import org.jacodb.api.JcClasspath
import org.jacodb.api.analysis.JcApplicationGraph
import org.jacodb.api.cfg.JcAssignInst
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.cfg.JcSpecialCallExpr
import org.jacodb.api.cfg.JcStaticCallExpr
import org.jacodb.api.ext.cfg.callExpr

class UnusedVariableFlowFunctions(
    private val graph: JcApplicationGraph,
) : FlowFunctions<JcInst, UnusedVariableDomainFact> {
    private val cp: JcClasspath
        get() = graph.classpath

    override fun sequent(
        current: JcInst,
        next: JcInst,
        fact: UnusedVariableDomainFact
    ): Collection<UnusedVariableDomainFact> {
        if (current !is JcAssignInst) {
            return setOf(fact)
        }

        if (fact == UnusedVariableZeroFact) {
            val toPath = current.lhv.toPath()
            if (!toPath.isOnHeap) {
                return setOf(UnusedVariableZeroFact, UnusedVariable(toPath, current))
            } else {
                return setOf(UnusedVariableZeroFact)
            }
        }
        check(fact is UnusedVariable)

        val toPath = current.lhv.toPath()
        val default = if (toPath == fact.variable) emptySet() else setOf(fact)
        val fromPath = current.rhv.toPathOrNull()
            ?: return default

        if (fromPath.isOnHeap || toPath.isOnHeap) {
            return default
        }

        if (fromPath == fact.variable) {
            return default + fact.copy(variable = toPath)
        }

        return default
    }

    override fun callToReturn(
        callStatement: JcInst,
        returnSite: JcInst,
        fact: UnusedVariableDomainFact
    ): Collection<UnusedVariableDomainFact> = sequent(callStatement, returnSite, fact)

    override fun callToStart(
        callStatement: JcInst,
        calleeStart: JcInst,
        fact: UnusedVariableDomainFact
    ): Collection<UnusedVariableDomainFact> {
        val callExpr = callStatement.callExpr
            ?: error("Call statement should have non-null callExpr")

        if (fact == UnusedVariableZeroFact) {
            if (callExpr !is JcStaticCallExpr && callExpr !is JcSpecialCallExpr) {
                return setOf(UnusedVariableZeroFact)
            }
            return buildSet {
                add(UnusedVariableZeroFact)
                val callee = calleeStart.location.method
                val formalParams = cp.getArgumentsOf(callee)
                for (formal in formalParams) {
                    add(UnusedVariable(formal.toPath(), callStatement))
                }
            }
        }
        check(fact is UnusedVariable)

        return emptySet()
    }

    override fun exitToReturnSite(
        callStatement: JcInst,
        returnSite: JcInst,
        exitStatement: JcInst,
        fact: UnusedVariableDomainFact
    ): Collection<UnusedVariableDomainFact> {
        return if (fact == UnusedVariableZeroFact) {
            setOf(UnusedVariableZeroFact)
        } else {
            emptySet()
        }
    }
}
