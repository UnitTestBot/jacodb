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

import org.jacodb.analysis.engine.DomainFact
import org.jacodb.analysis.engine.FlowFunctionInstance
import org.jacodb.analysis.engine.FlowFunctionsSpace
import org.jacodb.analysis.engine.ZEROFact
import org.jacodb.analysis.paths.startsWith
import org.jacodb.analysis.paths.toPathOrNull
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcMethod
import org.jacodb.api.cfg.JcArgument
import org.jacodb.api.cfg.JcAssignInst
import org.jacodb.api.cfg.JcExpr
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.cfg.JcInstanceCallExpr
import org.jacodb.api.cfg.JcReturnInst
import org.jacodb.api.cfg.JcValue
import org.jacodb.api.ext.cfg.callExpr

abstract class AbstractTaintForwardFunctions(
    protected val cp: JcClasspath
) : FlowFunctionsSpace {

    abstract fun transmitDataFlow(from: JcExpr, to: JcValue, atInst: JcInst, fact: DomainFact, dropFact: Boolean): List<DomainFact>

    abstract fun transmitDataFlowAtNormalInst(inst: JcInst, nextInst: JcInst, fact: DomainFact): List<DomainFact>

    override fun obtainSequentFlowFunction(current: JcInst, next: JcInst): FlowFunctionInstance =
        object : FlowFunctionInstance {
            override fun compute(fact: DomainFact): Collection<DomainFact> {
                if (fact is TaintNode && fact.activation == current) {
                    return listOf(fact.activatedCopy)
                }

                if (current is JcAssignInst) {
                    return transmitDataFlow(current.rhv, current.lhv, current, fact, dropFact = false)
                }

                return transmitDataFlowAtNormalInst(current, next, fact)
            }
        }

    override fun obtainCallToStartFlowFunction(
        callStatement: JcInst,
        callee: JcMethod
    ): FlowFunctionInstance = object : FlowFunctionInstance {
        override fun compute(fact: DomainFact): Collection<DomainFact> {
            if (fact is TaintNode && fact.activation == callStatement) {
                return emptyList()
            }

            val ans = mutableListOf<DomainFact>()
            val callExpr = callStatement.callExpr ?: error("Call statement should have non-null callExpr")
            val actualParams = callExpr.args
            val formalParams = callee.parameters.map {
                JcArgument.of(it.index, it.name, cp.findTypeOrNull(it.type.typeName)!!)
            }

            formalParams.zip(actualParams).forEach { (formal, actual) ->
                ans += transmitDataFlow(actual, formal, callStatement, fact, dropFact = true)
            }

            if (callExpr is JcInstanceCallExpr) {
                val thisInstance = callee.thisInstance
                ans += transmitDataFlow(callExpr.instance, thisInstance, callStatement, fact, dropFact = true)
            }

            if (fact == ZEROFact || (fact is TaintNode && fact.variable.isStatic)) {
                ans.add(fact)
            }

            return ans
        }
    }

    override fun obtainCallToReturnFlowFunction(
        callStatement: JcInst,
        returnSite: JcInst
    ): FlowFunctionInstance = object : FlowFunctionInstance {
        override fun compute(fact: DomainFact): Collection<DomainFact> {
            if (fact == ZEROFact) {
                return listOf(fact)
            }

            if (fact !is TaintNode) {
                return emptyList()
            }

            if (fact.activation == callStatement) {
                return listOf(fact.activatedCopy)
            }

            val callExpr = callStatement.callExpr ?: error("Call statement should have non-null callExpr")
            val actualParams = callExpr.args

            if (fact.variable.isStatic) {
                return emptyList()
            }

            actualParams.mapNotNull { it.toPathOrNull() }.forEach {
                if (fact.variable.startsWith(it)) {
                    return emptyList() // Will be handled by summary edge
                }
            }

            if (callExpr is JcInstanceCallExpr) {
                if (fact.variable.startsWith(callExpr.instance.toPathOrNull())) {
                    return emptyList() // Will be handled by summary edge
                }
            }

            if (callStatement is JcAssignInst && fact.variable.startsWith(callStatement.lhv.toPathOrNull())) {
                return emptyList()
            }

            return transmitDataFlowAtNormalInst(callStatement, returnSite, fact)
        }
    }

    override fun obtainExitToReturnSiteFlowFunction(
        callStatement: JcInst,
        returnSite: JcInst,
        exitStatement: JcInst
    ): FlowFunctionInstance = object : FlowFunctionInstance {
        override fun compute(fact: DomainFact): Collection<DomainFact> {
            val ans = mutableListOf<DomainFact>()
            val callExpr = callStatement.callExpr ?: error("Call statement should have non-null callExpr")
            val actualParams = callExpr.args
            val callee = exitStatement.location.method
            // TODO: maybe we can always use fact instead of updatedFact here
            val updatedFact = if (fact is TaintNode && fact.activation?.location?.method == callee) {
                fact.updateActivation(callStatement)
            } else {
                fact
            }
            val formalParams = callee.parameters.map {
                JcArgument.of(it.index, it.name, cp.findTypeOrNull(it.type.typeName)!!)
            }

            if (fact is TaintNode && fact.variable.isOnHeap) {
                // If there is some method A.f(formal: T) that is called like A.f(actual) then
                //  1. For all g^k, k >= 1, we should propagate back from formal.g^k to actual.g^k (as they are on heap)
                //  2. We shouldn't propagate from formal to actual (as formal is local)
                //  Second case is why we need check for isOnHeap
                // TODO: add test for handling of 2nd case
                formalParams.zip(actualParams).forEach { (formal, actual) ->
                    ans += transmitDataFlow(formal, actual, exitStatement, updatedFact, dropFact = true)
                }
            }

            if (callExpr is JcInstanceCallExpr) {
                ans += transmitDataFlow(callee.thisInstance, callExpr.instance, exitStatement, updatedFact, dropFact = true)
            }

            if (callStatement is JcAssignInst && exitStatement is JcReturnInst) {
                exitStatement.returnValue?.let { // returnValue can be null here in some weird cases, for e.g. lambda
                    ans += transmitDataFlow(it, callStatement.lhv, exitStatement, updatedFact, dropFact = true)
                }
            }

            if (fact is TaintNode && fact.variable.isStatic && fact !in ans) {
                ans.add(fact)
            }

            return ans
        }
    }
}