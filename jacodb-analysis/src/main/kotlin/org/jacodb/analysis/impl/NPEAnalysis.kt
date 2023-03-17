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

package org.jacodb.analysis.impl

import org.jacodb.api.JcClasspath
import org.jacodb.api.JcMethod
import org.jacodb.api.analysis.ApplicationGraph
import org.jacodb.api.analysis.JcAnalysisPlatform
import org.jacodb.api.cfg.JcArgument
import org.jacodb.api.cfg.JcAssignInst
import org.jacodb.api.cfg.JcCallExpr
import org.jacodb.api.cfg.JcConstant
import org.jacodb.api.cfg.JcEqExpr
import org.jacodb.api.cfg.JcExpr
import org.jacodb.api.cfg.JcIfInst
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.cfg.JcInstanceCallExpr
import org.jacodb.api.cfg.JcLocalVar
import org.jacodb.api.cfg.JcNeqExpr
import org.jacodb.api.cfg.JcNewArrayExpr
import org.jacodb.api.cfg.JcNewExpr
import org.jacodb.api.cfg.JcNullConstant
import org.jacodb.api.cfg.JcReturnInst
import org.jacodb.api.cfg.JcThis
import org.jacodb.api.cfg.JcValue
import org.jacodb.api.ext.cfg.callExpr
import org.jacodb.api.ext.isNullable
import org.jacodb.impl.analysis.locals

class NPEForwardFunctions(
    private val classpath: JcClasspath,
    private val graph: ApplicationGraph<JcMethod, JcInst>,
    private val platform: JcAnalysisPlatform,
    private val maxPathLength: Int = 5
): FlowFunctionsSpace<JcMethod, JcInst, TaintNode> {

    private fun TaintNode.checkActivation(inst: JcInst): TaintNode {
        return if (inst == activation)
            copy(activation = null)
        else
            this
    }

    private fun AccessPath?.isDereferencedAt(inst: JcInst): Boolean {
        if (this == null) {
            return false
        }

        (inst.callExpr as? JcInstanceCallExpr)?.instance?.toPath(maxPathLength)?.let {
            if (it.startsWith(this)) {
                return true
            }
        }

        return inst.operands.mapNotNull { it.toPathOrNull(maxPathLength) }.any {
            it.minus(this)?.isNotEmpty() == true
        }
    }

    private fun AccessPath?.isDereferencedAt(expr: JcExpr): Boolean {
        if (this == null) {
            return false
        }

        (expr as? JcInstanceCallExpr)?.instance?.toPath(maxPathLength)?.let {
            if (it.startsWith(this)) {
                return true
            }
        }

        return expr.operands.mapNotNull { it.toPathOrNull(maxPathLength) }.any {
            it.minus(this)?.isNotEmpty() == true
        }
    }

    private fun transmitDataFlow(from: JcExpr, to: JcValue, fact: TaintNode): List<TaintNode> {
        val factPath = fact.variable
        val default = if (factPath.isDereferencedAt(from) || factPath.isDereferencedAt(to)) emptyList() else listOf(fact)
        val toPath = to.toPathOrNull(maxPathLength) ?: return default

        if (from is JcNullConstant || (from is JcCallExpr && from.method.method.isNullable == true)) {
            return if (fact == TaintNode.ZERO) {
                listOf(TaintNode.ZERO, TaintNode.fromPath(toPath)) // taint is generated here
            } else {
                if (factPath.startsWith(toPath)) {
                    emptyList()
                } else {
                    default
                }
            }
        }
        if (from is JcNewExpr || from is JcNewArrayExpr || from is JcConstant || (from is JcCallExpr && from.method.method.isNullable != true)) {
            return if (factPath.startsWith(toPath)) {
                emptyList() // new kills the fact here
            } else {
                default
            }
        }

        // TODO: slightly differs from original paper, think what's correct
        val fromPath = from.toPathOrNull(maxPathLength) ?: return default
        if (factPath.startsWith(fromPath)) {
            val diff = factPath.minus(fromPath)!!
            return setOf(fact, TaintNode.fromPath(AccessPath.fromOther(toPath, diff, maxPathLength), fact.activation)).toList()
        }
        if (factPath.startsWith(toPath)) {
            return emptyList()
        }
        return default
    }

    private fun transmitDataFlowAndThrowFact(from: JcExpr, to: JcValue, fact: TaintNode): List<TaintNode> {
        return transmitDataFlow(from, to, fact).minus(fact)
    }

    override fun obtainStartFacts(startStatement: JcInst): Collection<TaintNode> {
        return listOf(TaintNode.ZERO) + platform.flowGraph(startStatement.location.method).locals
            .filter { it.type.nullable != false }
            .map { AccessPath.fromLocal(it) }
            .flatMap { it.expandAtDepth(if (it.value is JcThis) 1 else 0) } // maxPathLength here gives lots of false positives
            .map { TaintNode.fromPath(it) }
    }

    override fun obtainSequentFlowFunction(current: JcInst, next: JcInst): FlowFunctionInstance<TaintNode> = FlowFunctionInstance { fact ->
        val updatedFact = fact.checkActivation(current)
        val default = if (fact.variable.isDereferencedAt(current)) emptyList() else listOf(updatedFact)
        if (current is JcAssignInst) {
            return@FlowFunctionInstance transmitDataFlow(current.rhv, current.lhv, updatedFact)
        }

        // This handles cases like if (x != null) expr1 else expr2, where edges to expr1 and to expr2 should be different
        // (because x == null will be held at expr2 but won't be held at expr1)
        if (current is JcIfInst) {
            val expr = current.condition
            val comparedPath = if (expr.rhv is JcNullConstant) {
                expr.lhv.toPathOrNull(maxPathLength)
            } else if (expr.lhv is JcNullConstant) {
                expr.rhv.toPathOrNull(maxPathLength)
            } else {
                null
            }

            if (comparedPath == null)
                return@FlowFunctionInstance default

            val currentBranch = graph.methodOf(current).flowGraph().ref(next)
            if ((expr is JcEqExpr && currentBranch == current.trueBranch) || (expr is JcNeqExpr && currentBranch == current.falseBranch)) {
                // comparedPath is null in this branch
                if (fact == TaintNode.ZERO)
                    return@FlowFunctionInstance listOf(TaintNode.fromPath(comparedPath))
                if (fact.variable.startsWith(comparedPath) && fact.activation == null) {
                    if (fact.variable == comparedPath) {
                        // This is a hack: instructions like `return null` in branch of next will be considered only if
                        //  the fact holds (otherwise we could not get there)
                        return@FlowFunctionInstance listOf(TaintNode.ZERO)
                    }
                    return@FlowFunctionInstance emptyList()
                }
                return@FlowFunctionInstance default
            }
            if ((expr is JcEqExpr && currentBranch == current.falseBranch) || (expr is JcNeqExpr && currentBranch == current.trueBranch)) {
                // comparedPath is not null in this branch
                if (fact.variable == comparedPath)
                    return@FlowFunctionInstance emptyList()
                return@FlowFunctionInstance default
            }
        }

        default
    }

    override fun obtainCallToStartFlowFunction(
        callStatement: JcInst,
        callee: JcMethod
    ): FlowFunctionInstance<TaintNode> = FlowFunctionInstance { fact ->
        if (fact.activation == callStatement) {
            // TODO: think if this is correct
            return@FlowFunctionInstance emptyList()
        }
        // NB: in our jimple formalParameters are immutable => we can safely
        // pass paths from caller to callee and back by just renaming them
        val updatedFact = fact.checkActivation(callStatement)

        val ans = mutableListOf<TaintNode>()
        val callExpr = callStatement.callExpr ?: error("Call statement should have non-null callExpr")
        val actualParams = callExpr.args
        val formalParams = callee.parameters.map {
            JcArgument.of(it.index, it.name, classpath.findTypeOrNull(it.type.typeName)!!)
        }

        formalParams.zip(actualParams).forEach { (formal, actual) ->
            ans += transmitDataFlowAndThrowFact(actual, formal, updatedFact)
        }

        if (callExpr is JcInstanceCallExpr) {
            ans += transmitDataFlowAndThrowFact(callExpr.instance, JcThis(callExpr.instance.type), updatedFact)
        }

        if (fact == TaintNode.ZERO) {
            ans += platform.flowGraph(callee)
                .locals
                .filterIsInstance<JcLocalVar>()
                .filter { it.type.nullable != false }
                .map { TaintNode.fromPath(AccessPath.fromLocal(it)) }
                .plus(TaintNode.ZERO)
        }

        ans
    }

    override fun obtainCallToReturnFlowFunction(
        callStatement: JcInst,
        returnSite: JcInst
    ): FlowFunctionInstance<TaintNode> = FlowFunctionInstance { fact ->
        if (fact == TaintNode.ZERO)
            return@FlowFunctionInstance listOf(fact)

        val updatedFact = fact.checkActivation(callStatement)
        val callExpr = callStatement.callExpr ?: error("Call statement should have non-null callExpr")
        val actualParams = callExpr.args
        val default = if (fact.variable.isDereferencedAt(callStatement)) emptyList() else listOf(updatedFact)

        actualParams.mapNotNull { it.toPathOrNull(maxPathLength) }.forEach {
            if (fact.variable.startsWith(it)) {
                return@FlowFunctionInstance emptyList() // Will be handled by summary edge
            }
        }

        if (callExpr is JcInstanceCallExpr) {
            if (fact.variable.startsWith(callExpr.instance.toPath(maxPathLength))) {
                return@FlowFunctionInstance emptyList() // Will be handled by summary edge
            }
        }

        if (callStatement is JcAssignInst && fact.variable.startsWith(callStatement.lhv.toPath(maxPathLength))) {
            return@FlowFunctionInstance emptyList()
        }

        default
    }

    override fun obtainExitToReturnSiteFlowFunction(
        callStatement: JcInst,
        returnSite: JcInst,
        exitStatement: JcInst
    ): FlowFunctionInstance<TaintNode> = FlowFunctionInstance { fact ->
        val ans = mutableListOf<TaintNode>()
        val callExpr = callStatement.callExpr ?: error("Call statement should have non-null callExpr")
        val actualParams = callExpr.args
        val callee = exitStatement.location.method
        val formalParams = callee.parameters.map {
            JcArgument.of(it.index, it.name, classpath.findTypeOrNull(it.type.typeName)!!)
        }

        formalParams.zip(actualParams).forEach { (formal, actual) ->
            ans += transmitDataFlowAndThrowFact(formal, actual, fact)
        }

        if (callExpr is JcInstanceCallExpr) {
            ans += transmitDataFlowAndThrowFact(JcThis(callExpr.instance.type), callExpr.instance, fact)
        }

        if (callStatement is JcAssignInst && exitStatement is JcReturnInst) {
            exitStatement.returnValue?.let { // returnValue can be null here in some weird cases, for e.g. lambda
                ans += transmitDataFlowAndThrowFact(it, callStatement.lhv, fact)
            }
        }

        ans
    }
}

class NPEBackwardFunctions(
    private val classpath: JcClasspath,
    private val graph: ApplicationGraph<JcMethod, JcInst>,
    private val platform: JcAnalysisPlatform,
    private val maxPathLength: Int = 5
): FlowFunctionsSpace<JcMethod, JcInst, TaintNode> {
    private fun transmitBackDataFlow(from: JcValue, to: JcExpr, fact: TaintNode): List<TaintNode> {
        val factPath = fact.variable
        val default = listOf(fact)
        val toPath = to.toPathOrNull(maxPathLength) ?: return default
        val fromPath = from.toPathOrNull(maxPathLength) ?: return default

        if (factPath.startsWith(fromPath)) {
            val diff = factPath.minus(fromPath)!!
            return listOf(TaintNode.fromPath(AccessPath.fromOther(toPath, diff, maxPathLength), fact.activation))
        }
        return default
    }

    private fun transmitBackDataFlowAndThrowFact(from: JcValue, to: JcExpr, fact: TaintNode): List<TaintNode> {
        return transmitBackDataFlow(from, to, fact).minus(fact)
    }

    override fun obtainStartFacts(startStatement: JcInst): Collection<TaintNode> {
        return emptyList()
    }

    override fun obtainSequentFlowFunction(current: JcInst, next: JcInst): FlowFunctionInstance<TaintNode> = FlowFunctionInstance { fact ->
        // fact.activation != current needed here to jump over assignment where the fact appeared
        return@FlowFunctionInstance if (current is JcAssignInst && fact.activation != current) {
            transmitBackDataFlow(current.lhv, current.rhv, fact)
        } else {
            listOf(fact)
        }
    }

    override fun obtainCallToStartFlowFunction(
        callStatement: JcInst,
        callee: JcMethod
    ): FlowFunctionInstance<TaintNode> = FlowFunctionInstance { fact ->
        val ans = mutableListOf<TaintNode>()
        val callExpr = callStatement.callExpr ?: error("Call statement should have non-null callExpr")

        if (fact == TaintNode.ZERO)
            ans.add(TaintNode.ZERO)

        if (callStatement is JcAssignInst) {
            graph.entryPoint(callee).filterIsInstance<JcReturnInst>().forEach { returnInst ->
                returnInst.returnValue?.let {
                    ans += transmitBackDataFlowAndThrowFact(callStatement.lhv, it, fact)
                }
            }
        }

        if (callExpr is JcInstanceCallExpr) {
            ans += transmitBackDataFlowAndThrowFact(callExpr.instance, JcThis(callExpr.instance.type), fact)
        }

        // TODO: handle statics

        ans
    }

    override fun obtainCallToReturnFlowFunction(
        callStatement: JcInst,
        returnSite: JcInst
    ): FlowFunctionInstance<TaintNode> = FlowFunctionInstance { fact ->
        val factPath = fact.variable ?: return@FlowFunctionInstance listOf(fact)

        if (callStatement is JcAssignInst) {
            val lhvPath = callStatement.lhv.toPath(maxPathLength)
            if (factPath.startsWith(lhvPath) && fact.activation != callStatement) {
                return@FlowFunctionInstance emptyList()
            }
        }

        listOf(fact)
    }

    override fun obtainExitToReturnSiteFlowFunction(
        callStatement: JcInst,
        returnSite: JcInst,
        exitStatement: JcInst
    ): FlowFunctionInstance<TaintNode> = FlowFunctionInstance { fact ->
        val ans = mutableListOf<TaintNode>()
        val callExpr = callStatement.callExpr ?: error("Call statement should have non-null callExpr")
        val actualParams = callExpr.args
        val callee = graph.methodOf(exitStatement)
        val formalParams = callee.parameters.map {
            JcArgument.of(it.index, it.name, classpath.findTypeOrNull(it.type.typeName)!!)
        }

        formalParams.zip(actualParams).forEach { (formal, actual) ->
            ans += transmitBackDataFlowAndThrowFact(formal, actual, fact)
        }

        if (callExpr is JcInstanceCallExpr) {
            ans += transmitBackDataFlowAndThrowFact(JcThis(callExpr.instance.type), callExpr.instance, fact)
        }

        ans
    }
}

fun runNPEWithPointsTo(
    graph: ApplicationGraph<JcMethod, JcInst>,
    platform: JcAnalysisPlatform,
    startMethod: JcMethod,
    devirtualizer: Devirtualizer<JcMethod, JcInst>?
): IFDSResult<JcMethod, JcInst, TaintNode> {
    val forwardInstance = IFDSInstance(graph, NPEForwardFunctions(platform.classpath, graph, platform), devirtualizer)
    val backwardInstance = IFDSInstance(graph.reversed, NPEBackwardFunctions(platform.classpath, graph, platform), devirtualizer)
    val instance = TaintAnalysisWithPointsTo(forwardInstance, backwardInstance)
    instance.addStart(startMethod)
    instance.run()
    return instance.collectResults()
}