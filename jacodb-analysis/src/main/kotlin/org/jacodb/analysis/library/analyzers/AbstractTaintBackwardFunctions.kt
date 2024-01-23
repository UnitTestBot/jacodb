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
import org.jacodb.analysis.paths.toPath
import org.jacodb.analysis.paths.toPathOrNull
import org.jacodb.api.core.CoreMethod
import org.jacodb.api.core.analysis.ApplicationGraph
import org.jacodb.api.core.cfg.CoreExpr
import org.jacodb.api.core.cfg.CoreInst
import org.jacodb.api.core.cfg.CoreInstLocation
import org.jacodb.api.core.cfg.CoreValue
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.analysis.JcApplicationGraph
import org.jacodb.api.jvm.cfg.JcAssignInst
import org.jacodb.api.jvm.cfg.JcExpr
import org.jacodb.api.jvm.cfg.JcInst
import org.jacodb.api.jvm.cfg.JcInstLocation
import org.jacodb.api.jvm.cfg.JcInstanceCallExpr
import org.jacodb.api.jvm.cfg.JcReturnInst
import org.jacodb.api.jvm.cfg.JcValue
import org.jacodb.api.jvm.ext.cfg.callExpr
import javax.swing.plaf.nimbus.State

abstract class AbstractTaintBackwardFunctions<Method, Location, Statement, Value, Expr, Type>(
    protected val graph: ApplicationGraph<Method, Statement>,
    protected val maxPathLength: Int,
) : FlowFunctionsSpace<Statement, Method>
        where Value : CoreValue<Value, Type>,
              Expr : CoreExpr<Type, Value>,
              Method : CoreMethod<Statement>,
              Location : CoreInstLocation<Method>,
              Statement : CoreInst<Location, Method, Expr> {

    override fun obtainPossibleStartFacts(startStatement: Statement): Collection<DomainFact> {
        return listOf(ZEROFact)
    }

    abstract fun transmitBackDataFlow(
        from: Value,
        to: Expr,
        atInst: Statement,
        fact: DomainFact,
        dropFact: Boolean
    ): List<DomainFact>

    abstract fun transmitDataFlowAtNormalInst(inst: Statement, nextInst: Statement, fact: DomainFact): List<DomainFact>

    override fun obtainSequentFlowFunction(current: Statement, next: Statement) = FlowFunctionInstance { fact ->
        // TODO Caelmbleidd

        // fact.activation != current needed here to jump over assignment where the fact appeared
        if (current is JcAssignInst && (fact !is TaintNode || fact.activation != current)) {
            @Suppress("UNCHECKED_CAST")
            this as AbstractTaintBackwardFunctions<JcMethod, JcInstLocation, JcInst, JcValue, JcExpr, JcType>
            transmitBackDataFlow(current.lhv, current.rhv, current, fact, dropFact = false)
        } else {
            transmitDataFlowAtNormalInst(current, next, fact)
        }
    }

    override fun obtainCallToStartFlowFunction(
        callStatement: Statement,
        callee: Method
    ): FlowFunctionInstance = FlowFunctionInstance { fact ->

        buildList {
            if (callStatement is JcInst) { // TODO Caelmbleidd
                @Suppress("UNCHECKED_CAST")
                this@AbstractTaintBackwardFunctions as AbstractTaintBackwardFunctions<JcMethod, JcInstLocation, JcInst, JcValue, JcExpr, JcType>
                callee as JcMethod

                val callExpr = callStatement.callExpr ?: error("Call statement should have non-null callExpr")

                // TODO: think about activation point handling for statics here
                if (fact == ZEROFact || (fact is TaintNode && fact.variable.isStatic)) {
                    add(fact)
                }

                if (callStatement is JcAssignInst) {
                    graph.entryPoint(callee).filterIsInstance<JcReturnInst>().forEach { returnInst ->
                        returnInst.returnValue?.let {
                            addAll(
                                transmitBackDataFlow(
                                    callStatement.lhv,
                                    it,
                                    callStatement,
                                    fact,
                                    dropFact = true
                                )
                            )
                        }
                    }
                }

                if (callExpr is JcInstanceCallExpr) {
                    val thisInstance = (callee as JcMethod).thisInstance
                    addAll(transmitBackDataFlow(callExpr.instance, thisInstance, callStatement, fact, dropFact = true))
                }

                val formalParams = (graph as JcApplicationGraph).classpath.getFormalParamsOf(callee as JcMethod)

                callExpr.args.zip(formalParams).forEach { (actual, formal) ->
                    // FilterNot is needed for reasons described in comment for symmetric case in
                    //  AbstractTaintForwardFunctions.obtainExitToReturnSiteFlowFunction
                    addAll(transmitBackDataFlow(actual, formal, callStatement, fact, dropFact = true)
                        .filterNot { it is TaintNode && !it.variable.isOnHeap })
                }
            }
        }
    }

    override fun obtainCallToReturnFlowFunction(
        callStatement: Statement,
        returnSite: Statement
    ): FlowFunctionInstance = FlowFunctionInstance { fact ->
        if (fact !is TaintNode) {
            return@FlowFunctionInstance if (fact == ZEROFact) {
                listOf(fact)
            } else {
                emptyList()
            }
        }

        if (callStatement is JcInst) {
            val factPath = fact.variable
            val callExpr = callStatement.callExpr ?: error("CallStatement is expected to contain callExpr")

            // TODO: check that this is legal
            if (fact.activation == callStatement) {
                return@FlowFunctionInstance listOf(fact)
            }

            if (fact.variable.isStatic) {
                return@FlowFunctionInstance emptyList()
            }

            callExpr.args.forEach {
                if (fact.variable.startsWith(it.toPathOrNull())) {
                    return@FlowFunctionInstance emptyList()
                }
            }

            if (callExpr is JcInstanceCallExpr) {
                if (factPath.startsWith(callExpr.instance.toPathOrNull())) {
                    return@FlowFunctionInstance emptyList()
                }
            }

            if (callStatement is JcAssignInst) {
                val lhvPath = callStatement.lhv.toPath()
                if (factPath.startsWith(lhvPath)) {
                    return@FlowFunctionInstance emptyList()
                }
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
                @Suppress("UNCHECKED_CAST")
                this@AbstractTaintBackwardFunctions as AbstractTaintBackwardFunctions<JcMethod, JcInstLocation, JcInst, JcValue, JcExpr, JcType>
                exitStatement as JcInst
                graph as JcApplicationGraph

                val callExpr = callStatement.callExpr ?: error("Call statement should have non-null callExpr")
                val actualParams = callExpr.args
                val callee = graph.methodOf(exitStatement)
                val formalParams = graph.classpath.getFormalParamsOf(callee)

                formalParams.zip(actualParams).forEach { (formal, actual) ->
                    addAll(transmitBackDataFlow(formal, actual, exitStatement, fact, dropFact = true))
                }


                if (callExpr is JcInstanceCallExpr) {
                    addAll(
                        transmitBackDataFlow(
                            callee.thisInstance,
                            callExpr.instance,
                            exitStatement,
                            fact,
                            dropFact = true
                        )
                    )
                }

                if (fact is TaintNode && fact.variable.isStatic) {
                    add(fact)
                }
            }
        }
    }
}