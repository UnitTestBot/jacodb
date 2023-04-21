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
import org.jacodb.analysis.paths.toPath
import org.jacodb.analysis.paths.toPathOrNull
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcMethod
import org.jacodb.api.analysis.ApplicationGraph
import org.jacodb.api.analysis.JcAnalysisPlatform
import org.jacodb.api.cfg.*
import org.jacodb.api.ext.cfg.callExpr

abstract class AbstractTaintBackwardFunctions(
    protected val classpath: JcClasspath,
    protected val graph: ApplicationGraph<JcMethod, JcInst>,
    protected val platform: JcAnalysisPlatform,
    override val backward: FlowFunctionsSpace,
    protected val maxPathLength: Int,
) : FlowFunctionsSpace {
    
    override fun obtainStartFacts(startStatement: JcInst): Collection<TaintNode> {
        return emptyList()
    }

    abstract fun transmitBackDataFlow(from: JcValue, to: JcExpr, atInst: JcInst, fact: DomainFact, dropFact: Boolean): List<DomainFact>

    override fun obtainSequentFlowFunction(current: JcInst, next: JcInst): FlowFunctionInstance =
        object : FlowFunctionInstance {
            override val inIds = this@AbstractTaintBackwardFunctions.inIds

            override fun compute(fact: DomainFact): Collection<DomainFact> {
                // fact.activation != current needed here to jump over assignment where the fact appeared
                return if (current is JcAssignInst && (fact !is TaintNode || fact.activation != current)) {
                    transmitBackDataFlow(current.lhv, current.rhv, current, fact, dropFact = false)
                } else {
                    listOf(fact)
                }
            }
        }

    override fun obtainCallToStartFlowFunction(
        callStatement: JcInst,
        callee: JcMethod
    ): FlowFunctionInstance = object : FlowFunctionInstance {
        override val inIds = this@AbstractTaintBackwardFunctions.inIds

        override fun compute(fact: DomainFact): Collection<DomainFact> {
            val ans = mutableListOf<DomainFact>()
            val callExpr = callStatement.callExpr ?: error("Call statement should have non-null callExpr")

            // TODO: think about activation point handling for statics here
            if (fact == ZEROFact || (fact is TaintNode && fact.variable.isStatic))
                ans.add(fact)

            if (callStatement is JcAssignInst) {
                graph.entryPoint(callee).filterIsInstance<JcReturnInst>().forEach { returnInst ->
                    returnInst.returnValue?.let {
                        ans += transmitBackDataFlow(callStatement.lhv, it, callStatement, fact, dropFact = true)
                    }
                }
            }

            if (callExpr is JcInstanceCallExpr) {
                val thisInstance = callee.thisInstance
                ans += transmitBackDataFlow(callExpr.instance, thisInstance, callStatement, fact, dropFact = true)
            }

            val formalParams = callee.parameters.map {
                JcArgument.of(it.index, it.name, classpath.findTypeOrNull(it.type.typeName)!!)
            }

            callExpr.args.zip(formalParams).forEach { (actual, formal) ->
                // FilterNot is needed for reasons described in comment for symmetric case in
                //  AbstractTaintForwardFunctions.obtainExitToReturnSiteFlowFunction
                ans += transmitBackDataFlow(actual, formal, callStatement, fact, dropFact = true)
                    .filterNot { it is NPETaintNode && !it.variable.isOnHeap }
            }

            return ans
        }
    }

    override fun obtainCallToReturnFlowFunction(
        callStatement: JcInst,
        returnSite: JcInst
    ): FlowFunctionInstance = object: FlowFunctionInstance {
        override val inIds = this@AbstractTaintBackwardFunctions.inIds

        override fun compute(fact: DomainFact): Collection<DomainFact> {
            if (fact !is TaintNode) {
                return if (fact == ZEROFact) {
                    listOf(fact)
                } else {
                    emptyList()
                }
            }

            val factPath = fact.variable
            val callExpr = callStatement.callExpr ?: error("CallStatement is expected to contain callExpr")

            // TODO: check that this is legal
            if (fact.activation == callStatement) {
                return listOf(fact)
            }

            if (fact.variable.isStatic) {
                return emptyList()
            }

            callExpr.args.forEach {
                if (fact.variable.startsWith(it.toPathOrNull())) {
                    return emptyList()
                }
            }

            if (callExpr is JcInstanceCallExpr) {
                if (factPath.startsWith(callExpr.instance.toPathOrNull())) {
                    return emptyList()
                }
            }

            if (callStatement is JcAssignInst) {
                val lhvPath = callStatement.lhv.toPath()
                if (factPath.startsWith(lhvPath)) {
                    return emptyList()
                }
            }

            return listOf(fact)
        }
    }

    override fun obtainExitToReturnSiteFlowFunction(
        callStatement: JcInst,
        returnSite: JcInst,
        exitStatement: JcInst
    ): FlowFunctionInstance = object: FlowFunctionInstance {
        override val inIds = this@AbstractTaintBackwardFunctions.inIds

        override fun compute(fact: DomainFact): Collection<DomainFact> {
            val ans = mutableListOf<DomainFact>()
            val callExpr = callStatement.callExpr ?: error("Call statement should have non-null callExpr")
            val actualParams = callExpr.args
            val callee = graph.methodOf(exitStatement)
            val formalParams = callee.parameters.map {
                JcArgument.of(it.index, it.name, classpath.findTypeOrNull(it.type.typeName)!!)
            }

            formalParams.zip(actualParams).forEach { (formal, actual) ->
                ans += transmitBackDataFlow(formal, actual, exitStatement, fact, dropFact = true)
            }

            if (callExpr is JcInstanceCallExpr) {
                ans += transmitBackDataFlow(callee.thisInstance, callExpr.instance, exitStatement, fact, dropFact = true)
            }

            if (fact is TaintNode && fact.variable.isStatic) {
                ans.add(fact)
            }

            return ans
        }
    }
}