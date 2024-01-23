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
import org.jacodb.api.core.CoreMethod
import org.jacodb.api.core.cfg.CoreExpr
import org.jacodb.api.core.cfg.CoreInst
import org.jacodb.api.core.cfg.CoreInstLocation
import org.jacodb.api.core.cfg.CoreValue
import org.jacodb.api.jvm.JcProject
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.cfg.JcAssignInst
import org.jacodb.api.jvm.cfg.JcExpr
import org.jacodb.api.jvm.cfg.JcInst
import org.jacodb.api.jvm.cfg.JcInstanceCallExpr
import org.jacodb.api.jvm.cfg.JcReturnInst
import org.jacodb.api.jvm.cfg.JcValue
import org.jacodb.api.jvm.ext.cfg.callExpr

abstract class AbstractTaintForwardFunctions<Method, Location, Statement, Value, Expr, Type>(
    protected val cp: JcProject
) : FlowFunctionsSpace<Statement, Method>
        where Value : CoreValue<Value, Type>,
              Expr : CoreExpr<Type, Value>,
              Method : CoreMethod<Statement>,
              Location : CoreInstLocation<Method>,
              Statement : CoreInst<Location, Method, Expr> {

    abstract fun transmitDataFlow(
        from: Expr,
        to: Value,
        atInst: Statement,
        fact: DomainFact,
        dropFact: Boolean
    ): List<DomainFact>

    abstract fun transmitDataFlowAtNormalInst(inst: Statement, nextInst: Statement, fact: DomainFact): List<DomainFact>

    override fun obtainSequentFlowFunction(current: Statement, next: Statement) = FlowFunctionInstance { fact ->
        if (fact is TaintNode && fact.activation == current) {
            listOf(fact.activatedCopy)
        } else if (current is JcAssignInst) {
            @Suppress("UNCHECKED_CAST")
            transmitDataFlow(current.rhv as Expr, current.lhv as Value, current, fact, dropFact = false)
        } else {
            transmitDataFlowAtNormalInst(current, next, fact)
        }
    }

    override fun obtainCallToStartFlowFunction(
        callStatement: Statement,
        callee: Method
    ) = FlowFunctionInstance { fact ->
        if (fact is TaintNode && fact.activation == callStatement) {
            return@FlowFunctionInstance emptyList()
        }


        buildList {
            if (callStatement is JcInst) {
                val callExpr = callStatement.callExpr ?: error("Call statement should have non-null callExpr")
                val actualParams = callExpr.args
                val formalParams = cp.getFormalParamsOf(callee as JcMethod)

                formalParams.zip(actualParams).forEach { (formal, actual) ->
                    @Suppress("UNCHECKED_CAST")
                    addAll(transmitDataFlow(actual as Expr, formal as Value, callStatement, fact, dropFact = true))
                }

                if (callExpr is JcInstanceCallExpr) {
                    @Suppress("UNCHECKED_CAST")
                    addAll(
                        transmitDataFlow(
                            callExpr.instance as Expr,
                            callee.thisInstance as Value,
                            callStatement,
                            fact,
                            dropFact = true
                        )
                    )
                }

                if (fact == ZEROFact || (fact is TaintNode && fact.variable.isStatic)) {
                    add(fact)
                }
            }
        }
    }

    override fun obtainCallToReturnFlowFunction(
        callStatement: Statement,
        returnSite: Statement
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

        if (callStatement is JcInst) {
            val callExpr = callStatement.callExpr ?: error("Call statement should have non-null callExpr")
            val actualParams = callExpr.args

            actualParams.mapNotNull { it.toPathOrNull() }.forEach {
                if (fact.variable.startsWith(it)) {
                    return@FlowFunctionInstance emptyList() // Will be handled by summary edge
                }
            }

            if (callExpr is JcInstanceCallExpr) {
                if (fact.variable.startsWith(callExpr.instance.toPathOrNull())) {
                    return@FlowFunctionInstance emptyList() // Will be handled by summary edge
                }
            }

            if (callStatement is JcAssignInst && fact.variable.startsWith(callStatement.lhv.toPathOrNull())) {
                return@FlowFunctionInstance emptyList()
            }
        }

        transmitDataFlowAtNormalInst(callStatement, returnSite, fact)
    }

    override fun obtainExitToReturnSiteFlowFunction(
        callStatement: Statement,
        returnSite: Statement,
        exitStatement: Statement
    ): FlowFunctionInstance = FlowFunctionInstance { fact ->
        buildList {
            if (callStatement is JcInst) {
                val callExpr = callStatement.callExpr ?: error("Call statement should have non-null callExpr")
                val actualParams = callExpr.args
                val callee = exitStatement.location.method
                // TODO: maybe we can always use fact instead of updatedFact here
                val updatedFact = if (fact is TaintNode && fact.activation?.location?.method == callee) {
                    fact.updateActivation(callStatement)
                } else {
                    fact
                }
                val formalParams = cp.getFormalParamsOf(callee as JcMethod)

                if (fact is TaintNode && fact.variable.isOnHeap) {
                    // If there is some method A.f(formal: T) that is called like A.f(actual) then
                    //  1. For all g^k, k >= 1, we should propagate back from formal.g^k to actual.g^k (as they are on heap)
                    //  2. We shouldn't propagate from formal to actual (as formal is local)
                    //  Second case is why we need check for isOnHeap
                    // TODO: add test for handling of 2nd case
                    formalParams.zip(actualParams).forEach { (formal, actual) ->
                        @Suppress("UNCHECKED_CAST")
                        addAll(
                            transmitDataFlow(
                                formal as Expr,
                                actual as Value,
                                exitStatement,
                                updatedFact,
                                dropFact = true
                            )
                        )
                    }
                }

                if (callExpr is JcInstanceCallExpr) {
                    @Suppress("UNCHECKED_CAST")
                    addAll(
                        transmitDataFlow(
                            callee.thisInstance as Expr,
                            callExpr.instance as Value,
                            exitStatement,
                            updatedFact,
                            dropFact = true
                        )
                    )
                }

                if (callStatement is JcAssignInst && exitStatement is JcReturnInst) {
                    exitStatement.returnValue?.let { // returnValue can be null here in some weird cases, for e.g. lambda
                        @Suppress("UNCHECKED_CAST")
                        addAll(
                            transmitDataFlow(
                                it as Expr,
                                callStatement.lhv as Value,
                                exitStatement,
                                updatedFact,
                                dropFact = true
                            )
                        )
                    }
                }

                if (fact is TaintNode && fact.variable.isStatic) {
                    add(fact)
                }
            }
        }
    }
}