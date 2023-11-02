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

import org.jacodb.analysis.engine.DomainFact
import org.jacodb.analysis.engine.FlowFunctionInstance
import org.jacodb.analysis.engine.FlowFunctionsSpace
import org.jacodb.analysis.engine.ZEROFact
import org.jacodb.analysis.paths.startsWith
import org.jacodb.analysis.paths.toPathOrNull
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcMethod
import org.jacodb.api.cfg.JcAssignInst
import org.jacodb.api.cfg.JcExpr
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.cfg.JcInstanceCallExpr
import org.jacodb.api.cfg.JcReturnInst
import org.jacodb.api.cfg.JcValue
import org.jacodb.api.ext.cfg.callExpr

abstract class AbstractTaintForwardFunctions(
    protected val cp: JcClasspath,
) : FlowFunctionsSpace {

    abstract fun transmitDataFlow(
        from: JcExpr,
        to: JcValue,
        atInst: JcInst,
        fact: DomainFact,
        dropFact: Boolean,
    ): List<DomainFact>

    abstract fun transmitDataFlowAtNormalInst(
        inst: JcInst,
        nextInst: JcInst,
        fact: DomainFact,
    ): List<DomainFact>

    final override fun obtainSequentFlowFunction(
        current: JcInst,
        next: JcInst,
    ) = FlowFunctionInstance { fact ->
        if (fact is TaintNode && fact.activation == current) {
            listOf(fact.activatedCopy)
        } else if (current is JcAssignInst) {
            // Note: 'next' is ignored
            transmitDataFlow(current.rhv, current.lhv, current, fact, false)
        } else {
            transmitDataFlowAtNormalInst(current, next, fact)
        }
    }

    final override fun obtainCallToStartFlowFunction(
        callStatement: JcInst,
        callee: JcMethod,
    ) = FlowFunctionInstance { fact ->
        if (fact is TaintNode && fact.activation == callStatement) {
            return@FlowFunctionInstance emptyList()
        }

        val callExpr = callStatement.callExpr ?: error("Call statement should have non-null callExpr")
        val actualParams = callExpr.args
        val formalParams = cp.getFormalParamsOf(callee)
        buildList {
            formalParams.zip(actualParams).forEach { (formal, actual) ->
                addAll(transmitDataFlow(actual, formal, callStatement, fact, dropFact = true))
            }

            if (callExpr is JcInstanceCallExpr) {
                addAll(transmitDataFlow(callExpr.instance, callee.thisInstance, callStatement, fact, dropFact = true))
            }

            if (fact == ZEROFact || (fact is TaintNode && fact.variable.isStatic)) {
                add(fact)
            }
        }
    }

    final override fun obtainCallToReturnFlowFunction(
        callStatement: JcInst,
        returnSite: JcInst,
    ) = FlowFunctionInstance { fact ->
        if (fact == ZEROFact) {
            return@FlowFunctionInstance listOf(fact)
        }

        if (fact !is TaintNode || fact.variable.isStatic) {
            return@FlowFunctionInstance emptyList()
        }

        if (fact.activation == callStatement) {
            return@FlowFunctionInstance listOf(fact.activatedCopy)
        }

        val callExpr = callStatement.callExpr ?: error("Call statement should have non-null callExpr")

        for (actual in callExpr.args) {
            // Possibly tainted actual parameter:
            if (fact.variable.startsWith(actual.toPathOrNull())) {
                return@FlowFunctionInstance emptyList() // Will be handled by summary edge
            }
        }

        if (callExpr is JcInstanceCallExpr) {
            // Possibly tainted instance:
            if (fact.variable.startsWith(callExpr.instance.toPathOrNull())) {
                return@FlowFunctionInstance emptyList() // Will be handled by summary edge
            }
        }

        if (callStatement is JcAssignInst) {
            // Possibly tainted lhv:
            if (fact.variable.startsWith(callStatement.lhv.toPathOrNull())) {
                return@FlowFunctionInstance emptyList() // Overridden by rhv
            }
        }

        transmitDataFlowAtNormalInst(callStatement, returnSite, fact)
    }

    final override fun obtainExitToReturnSiteFlowFunction(
        callStatement: JcInst,
        returnSite: JcInst,
        exitStatement: JcInst,
    ): FlowFunctionInstance = FlowFunctionInstance { fact ->
        val callExpr = callStatement.callExpr ?: error("Call statement should have non-null callExpr")
        val actualParams = callExpr.args
        val callee = exitStatement.location.method
        // TODO: maybe we can always use fact instead of updatedFact here
        val updatedFact = if (fact is TaintNode && fact.activation?.location?.method == callee) {
            fact.updateActivation(callStatement)
        } else {
            fact
        }
        val formalParams = cp.getFormalParamsOf(callee)

        buildList {
            if (fact is TaintNode && fact.variable.isOnHeap) {
                // If there is some method A.f(formal: T) that is called like A.f(actual) then
                //  1. For all g^k, k >= 1, we should propagate back from formal.g^k to actual.g^k (as they are on heap)
                //  2. We shouldn't propagate from formal to actual (as formal is local)
                //  Second case is why we need check for isOnHeap
                // TODO: add test for handling of 2nd case
                formalParams.zip(actualParams).forEach { (formal, actual) ->
                    addAll(transmitDataFlow(formal, actual, exitStatement, updatedFact, dropFact = true))
                }
            }

            if (callExpr is JcInstanceCallExpr) {
                addAll(
                    transmitDataFlow(
                        callee.thisInstance,
                        callExpr.instance,
                        exitStatement,
                        updatedFact,
                        dropFact = true
                    )
                )
            }

            if (callStatement is JcAssignInst && exitStatement is JcReturnInst) {
                exitStatement.returnValue?.let { // returnValue can be null here in some weird cases, for e.g. lambda
                    addAll(transmitDataFlow(it, callStatement.lhv, exitStatement, updatedFact, dropFact = true))
                }
            }

            if (fact is TaintNode && fact.variable.isStatic) {
                add(fact)
            }
        }
    }
}
