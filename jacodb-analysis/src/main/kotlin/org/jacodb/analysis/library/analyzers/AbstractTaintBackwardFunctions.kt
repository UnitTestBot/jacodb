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
import org.jacodb.api.JcMethod
import org.jacodb.api.analysis.JcApplicationGraph
import org.jacodb.api.cfg.JcAssignInst
import org.jacodb.api.cfg.JcExpr
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.cfg.JcInstanceCallExpr
import org.jacodb.api.cfg.JcReturnInst
import org.jacodb.api.cfg.JcValue
import org.jacodb.api.ext.cfg.callExpr

abstract class AbstractTaintBackwardFunctions(
    protected val graph: JcApplicationGraph,
    protected val maxPathLength: Int,
) : FlowFunctionsSpace {

    override fun obtainPossibleStartFacts(startStatement: JcInst): Collection<DomainFact> {
        return listOf(ZEROFact)
    }

    abstract fun transmitBackDataFlow(from: JcValue, to: JcExpr, atInst: JcInst, fact: DomainFact, dropFact: Boolean): List<DomainFact>

    abstract fun transmitDataFlowAtNormalInst(inst: JcInst, nextInst: JcInst, fact: DomainFact): List<DomainFact>

    override fun obtainSequentFlowFunction(current: JcInst, next: JcInst) = FlowFunctionInstance { fact ->
        // fact.activation != current needed here to jump over assignment where the fact appeared
        if (current is JcAssignInst && (fact !is TaintNode || fact.activation != current)) {
            transmitBackDataFlow(current.lhv, current.rhv, current, fact, dropFact = false)
        } else {
            transmitDataFlowAtNormalInst(current, next, fact)
        }
    }

    override fun obtainCallToStartFlowFunction(
        callStatement: JcInst,
        callee: JcMethod
    ): FlowFunctionInstance = FlowFunctionInstance { fact ->
        val callExpr = callStatement.callExpr ?: error("Call statement should have non-null callExpr")

        buildList {
            // TODO: think about activation point handling for statics here
            if (fact == ZEROFact || (fact is TaintNode && fact.variable.isStatic)) {
                add(fact)
            }

            if (callStatement is JcAssignInst) {
                graph.entryPoint(callee).filterIsInstance<JcReturnInst>().forEach { returnInst ->
                    returnInst.returnValue?.let {
                        addAll(transmitBackDataFlow(callStatement.lhv, it, callStatement, fact, dropFact = true))
                    }
                }
            }

            if (callExpr is JcInstanceCallExpr) {
                val thisInstance = callee.thisInstance
                addAll(transmitBackDataFlow(callExpr.instance, thisInstance, callStatement, fact, dropFact = true))
            }

            val formalParams = graph.classpath.getFormalParamsOf(callee)

            callExpr.args.zip(formalParams).forEach { (actual, formal) ->
                // FilterNot is needed for reasons described in comment for symmetric case in
                //  AbstractTaintForwardFunctions.obtainExitToReturnSiteFlowFunction
                addAll(transmitBackDataFlow(actual, formal, callStatement, fact, dropFact = true)
                    .filterNot { it is TaintNode && !it.variable.isOnHeap })
            }
        }
    }

    override fun obtainCallToReturnFlowFunction(
        callStatement: JcInst,
        returnSite: JcInst
    ): FlowFunctionInstance = FlowFunctionInstance { fact ->
        if (fact !is TaintNode) {
            return@FlowFunctionInstance if (fact == ZEROFact) {
                listOf(fact)
            } else {
                emptyList()
            }
        }

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

        transmitDataFlowAtNormalInst(callStatement, returnSite, fact)
    }

    override fun obtainExitToReturnSiteFlowFunction(
        callStatement: JcInst,
        returnSite: JcInst,
        exitStatement: JcInst
    ): FlowFunctionInstance = FlowFunctionInstance { fact ->
        val callExpr = callStatement.callExpr ?: error("Call statement should have non-null callExpr")
        val actualParams = callExpr.args
        val callee = graph.methodOf(exitStatement)
        val formalParams = graph.classpath.getFormalParamsOf(callee)

        buildList {
            formalParams.zip(actualParams).forEach { (formal, actual) ->
                addAll(transmitBackDataFlow(formal, actual, exitStatement, fact, dropFact = true))
            }

            if (callExpr is JcInstanceCallExpr) {
                addAll(transmitBackDataFlow(callee.thisInstance, callExpr.instance, exitStatement, fact, dropFact = true))
            }

            if (fact is TaintNode && fact.variable.isStatic) {
                add(fact)
            }
        }
    }
}