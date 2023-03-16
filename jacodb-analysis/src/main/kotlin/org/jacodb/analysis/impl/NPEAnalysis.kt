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

import org.jacodb.api.JcClassType
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcField
import org.jacodb.api.JcMethod
import org.jacodb.api.analysis.ApplicationGraph
import org.jacodb.api.analysis.JcAnalysisPlatform
import org.jacodb.api.cfg.JcArgument
import org.jacodb.api.cfg.JcAssignInst
import org.jacodb.api.cfg.JcCallExpr
import org.jacodb.api.cfg.JcCastExpr
import org.jacodb.api.cfg.JcConstant
import org.jacodb.api.cfg.JcEqExpr
import org.jacodb.api.cfg.JcExpr
import org.jacodb.api.cfg.JcFieldRef
import org.jacodb.api.cfg.JcIfInst
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.cfg.JcInstanceCallExpr
import org.jacodb.api.cfg.JcLocal
import org.jacodb.api.cfg.JcLocalVar
import org.jacodb.api.cfg.JcNeqExpr
import org.jacodb.api.cfg.JcNewArrayExpr
import org.jacodb.api.cfg.JcNewExpr
import org.jacodb.api.cfg.JcNullConstant
import org.jacodb.api.cfg.JcReturnInst
import org.jacodb.api.cfg.JcThis
import org.jacodb.api.cfg.JcValue
import org.jacodb.api.ext.cfg.callExpr
import org.jacodb.api.ext.fields
import org.jacodb.api.ext.isNullable
import org.jacodb.impl.analysis.locals

class NPEForwardFunctions(
    private val classpath: JcClasspath,
    private val graph: ApplicationGraph<JcMethod, JcInst>,
    private val platform: JcAnalysisPlatform
): FlowFunctionsSpace<JcMethod, JcInst, TaintNode> {

    private val maxPathLength = 5

    private val JcExpr.toPathOrNull: AccessPath?
        get() {
            if (this is JcCastExpr) {
                return operand.toPathOrNull
            }
            if (this is JcLocal) {
                return AccessPath.fromLocal(this)
            }
            if (this is JcFieldRef) {
                // TODO: think about static fields
                return instance?.toPathOrNull?.let {
                    AccessPath.fromOther(it, listOf(field.field), maxPathLength)
                }
            }
            // TODO: handle arrays
            return null
        }

    private val JcValue.toPath: AccessPath
        get() = toPathOrNull ?: error("Unable to build access path for value $this")

    private fun AccessPath.expandAtDepth(k: Int): List<AccessPath> {
        if (k == 0)
            return listOf(this)

        val jcClass = fieldAccesses.lastOrNull()?.enclosingClass ?: (value.type as? JcClassType)?.jcClass
            ?: return listOf(this)
        // TODO: handle ArrayType

        return listOf(this) + jcClass.fields.flatMap {
            AccessPath.fromOther(this, listOf(it), maxPathLength).expandAtDepth(k - 1)
        }
    }

    private fun AccessPath?.minus(other: AccessPath): List<JcField>? {
        if (this == null) {
            return null
        }
        if (value != other.value) {
            return null
        }
        if (fieldAccesses.take(other.fieldAccesses.size) != other.fieldAccesses) {
            return null
        }
        return fieldAccesses.drop(other.fieldAccesses.size)
    }

    private fun AccessPath?.startsWith(other: AccessPath): Boolean {
        return minus(other) != null
    }

    private fun TaintNode.checkActivation(inst: JcInst): TaintNode {
        return if (inst == activation)
            copy(activation=null)
        else
            this
    }

    private fun AccessPath?.isDereferencedAt(inst: JcInst): Boolean {
        if (this == null) {
            return false
        }

        (inst.callExpr as? JcInstanceCallExpr)?.instance?.toPath?.let {
            if (it.startsWith(this)) {
                return true
            }
        }

        return inst.operands.mapNotNull { it.toPathOrNull }.any {
            it.minus(this)?.isNotEmpty() == true
        }
    }

    private fun AccessPath?.isDereferencedAt(expr: JcExpr): Boolean {
        if (this == null) {
            return false
        }

        (expr as? JcInstanceCallExpr)?.instance?.toPath?.let {
            if (it.startsWith(this)) {
                return true
            }
        }

        return expr.operands.mapNotNull { it.toPathOrNull }.any {
            it.minus(this)?.isNotEmpty() == true
        }
    }

    private fun transmitDataFlow(from: JcExpr, to: JcValue, fact: TaintNode): List<TaintNode> {
        val factPath = fact.variable
        val default = if (factPath.isDereferencedAt(from) || factPath.isDereferencedAt(to)) emptyList() else listOf(fact)
        val toPath = to.toPathOrNull ?: return default

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
        val fromPath = from.toPathOrNull ?: return default
        if (factPath.startsWith(fromPath)) {
            val diff = factPath.minus(fromPath)!!
            return setOf(fact, TaintNode.fromPath(AccessPath.fromOther(toPath, diff, maxPathLength))).toList()
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
            .map { AccessPath.fromLocal(it) }
            .flatMap { it.expandAtDepth(0) } // maxPathLength here gives lots of false positives
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
                expr.lhv.toPathOrNull
            } else if (expr.lhv is JcNullConstant) {
                expr.rhv.toPathOrNull
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
                if (fact.variable.startsWith(comparedPath)) {
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
        // NB: in our jimple formalParameters are immutable => we can safely
        // pass paths from caller to callee and back by just renaming them
        val ans = mutableListOf<TaintNode>()
        val callExpr = callStatement.callExpr ?: error("Call statement should have non-null callExpr")
        val actualParams = callExpr.args
        val formalParams = callee.parameters.map {
            JcArgument.of(it.index, it.name, classpath.findTypeOrNull(it.type.typeName)!!)
        }

        formalParams.zip(actualParams).forEach { (formal, actual) ->
            ans += transmitDataFlowAndThrowFact(actual, formal, fact)
        }

        if (callExpr is JcInstanceCallExpr) {
            ans += transmitDataFlowAndThrowFact(callExpr.instance, JcThis(callExpr.instance.type), fact)
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

        actualParams.mapNotNull { it.toPathOrNull }.forEach {
            if (fact.variable.startsWith(it)) {
                return@FlowFunctionInstance emptyList() // Will be handled by summary edge
            }
        }

        if (callExpr is JcInstanceCallExpr) {
            if (fact.variable.startsWith(callExpr.instance.toPath)) {
                return@FlowFunctionInstance emptyList() // Will be handled by summary edge
            }
        }

        if (callStatement is JcAssignInst && fact.variable.startsWith(callStatement.lhv.toPath)) {
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

class NPEBackwardFunctions: FlowFunctionsSpace<JcMethod, JcInst, TaintNode> {
    override fun obtainStartFacts(startStatement: JcInst): Collection<TaintNode> {
        TODO("Not yet implemented")
    }

    override fun obtainSequentFlowFunction(current: JcInst, next: JcInst): FlowFunctionInstance<TaintNode> {
        return IdLikeFlowFunction(emptySet(), emptyMap())
        TODO("Not yet implemented")
    }

    override fun obtainCallToStartFlowFunction(
        callStatement: JcInst,
        callee: JcMethod
    ): FlowFunctionInstance<TaintNode> {
        return IdLikeFlowFunction(emptySet(), emptyMap())
        TODO("Not yet implemented")
    }

    override fun obtainCallToReturnFlowFunction(
        callStatement: JcInst,
        returnSite: JcInst
    ): FlowFunctionInstance<TaintNode> {
        return IdLikeFlowFunction(emptySet(), emptyMap())
        TODO("Not yet implemented")
    }

    override fun obtainExitToReturnSiteFlowFunction(
        callStatement: JcInst,
        returnSite: JcInst,
        exitStatement: JcInst
    ): FlowFunctionInstance<TaintNode> {
        return IdLikeFlowFunction(emptySet(), emptyMap())
        TODO("Not yet implemented")
    }
}

/**
 * This is an implementation of [FlowFunctionsSpace] for NullPointerException problem based on JaCoDB CFG.
 * Here "fact D holds" denotes that "value D can be null"
 */
//class NPEForwardFunctions(
//    private val classpath: JcClasspath,
//    private val graph: ApplicationGraph<JcMethod, JcInst>,
//    private val platform: JcAnalysisPlatform
//): FlowFunctionsSpace<JcMethod, JcInst, TaintNode> {
//
//    private val factLeadingToNullVisitor = object : DefaultJcExprVisitor<TaintNode?> {
//        override val defaultExprHandler: (JcExpr) -> TaintNode?
//            get() = { null }
//
//        private fun visitJcLocal(value: JcLocal) = TaintNode.fromPath(AccessPath.fromLocal(value))
//
//        private fun visitJcCallExpr(expr: JcCallExpr): TaintNode? {
//            if (expr.method.method.isNullable == true) {
//                return TaintNode.ZERO
//            }
//            return null
//        }
//
//        override fun visitJcArgument(value: JcArgument) = visitJcLocal(value)
//
//        override fun visitJcLocalVar(value: JcLocalVar) = visitJcLocal(value)
//
//        override fun visitJcNullConstant(value: JcNullConstant) = TaintNode.ZERO
//
//        override fun visitJcCastExpr(expr: JcCastExpr) = expr.operand.accept(this)
//
//        override fun visitJcDynamicCallExpr(expr: JcDynamicCallExpr) = visitJcCallExpr(expr)
//
//        override fun visitJcSpecialCallExpr(expr: JcSpecialCallExpr) = visitJcCallExpr(expr)
//
//        override fun visitJcStaticCallExpr(expr: JcStaticCallExpr) = visitJcCallExpr(expr)
//
//        override fun visitJcVirtualCallExpr(expr: JcVirtualCallExpr) = visitJcCallExpr(expr)
//
//        // TODO: override others
//    }
//
//    // Returns a fact, such that if it holds, `this` expr is null (or null if there is no such fact)
//    private val JcExpr.factLeadingToNull: TaintNode?
//        get() = accept(factLeadingToNullVisitor)
//
//    // TODO: think about name shadowing
//    // Returns all local variables and arguments referenced by this method
//    private val JcMethod.domain: Set<TaintNode>
//        get() {
//            return platform.flowGraph(this).locals
//                .map { TaintNode.fromPath(AccessPath.fromLocal(it)) }
//                .toSet()
//                .plus(TaintNode.ZERO)
//        }
//
//    private val JcInst.domain: Set<TaintNode>
//        get() = location.method.domain
//
//    // Returns a value that is being dereferenced in this call
//    private val JcInst.dereferencedValue: JcLocal?
//        get() {
//            (callExpr as? JcInstanceCallExpr)?.let {
//                return it.instance as? JcLocal
//            }
//
//            return fieldRef?.instance as? JcLocal
//        }
//
//    override fun obtainStartFacts(startStatement: JcInst): Collection<TaintNode> {
//        return startStatement.domain.filter { it.variable == null || it.variable.value.type.nullable != false }
//    }
//
//    override fun obtainSequentFlowFunction(current: JcInst, next: JcInst): FlowFunctionInstance<TaintNode> {
//        val nonId = mutableMapOf<TaintNode, List<TaintNode>>()
//
//        current.dereferencedValue?.let {
//            nonId[TaintNode.fromPath(AccessPath.fromLocal(it))] = emptyList()
//        }
//
//        if (current is JcAssignInst) {
//            val lhv = current.lhv
//            if (lhv is JcLocal) {
//                nonId[TaintNode.fromPath(AccessPath.fromLocal(lhv))] = listOf()
//                current.rhv.factLeadingToNull?.let {
//                    if (it.variable == null || it.variable.value != current.dereferencedValue) {
//                        nonId[it] = setOf(it, TaintNode.fromPath(AccessPath.fromLocal(lhv))).toList()
//                    }
//                }
//            }
//        }
//
//        // This handles cases like if (x != null) expr1 else expr2, where edges to expr1 and to expr2 should be different
//        // (because x == null will be held at expr2 but won't be held at expr1)
//        if (current is JcIfInst) {
//            val expr = current.condition
//            var comparedValue: JcValue? = null
//
//            if (expr.rhv is JcNullConstant && expr.lhv is JcLocal)
//                comparedValue = expr.lhv
//            else if (expr.lhv is JcNullConstant && expr.rhv is JcLocal)
//                comparedValue = expr.rhv
//
//            if (comparedValue !is JcLocal)
//                return IdLikeFlowFunction(current.domain, nonId)
//
//            val currentBranch = graph.methodOf(current).flowGraph().ref(next)
//            when (expr) {
//                is JcEqExpr -> {
//                    comparedValue.let {
//                        if (currentBranch == current.trueBranch) {
//                            nonId[TaintNode.ZERO] = listOf(TaintNode.ZERO, TaintNode.fromPath(AccessPath.fromLocal(comparedValue)))
//                        } else if (currentBranch == current.falseBranch) {
//                            nonId[TaintNode.fromPath(AccessPath.fromLocal(comparedValue))] = emptyList()
//                        }
//                    }
//                }
//                is JcNeqExpr -> {
//                    comparedValue.let {
//                        if (currentBranch == current.falseBranch) {
//                            nonId[TaintNode.ZERO] = listOf(TaintNode.ZERO, TaintNode.fromPath(AccessPath.fromLocal(comparedValue)))
//                        } else if (currentBranch == current.trueBranch) {
//                            nonId[TaintNode.fromPath(AccessPath.fromLocal(comparedValue))] = emptyList()
//                        }
//                    }
//                }
//                else -> Unit
//            }
//    }
//        return IdLikeFlowFunction(current.domain, nonId)
//    }
//
//    override fun obtainCallToStartFlowFunction(
//        callStatement: JcInst,
//        callee: JcMethod
//    ): FlowFunctionInstance<TaintNode> {
//        val nonId = mutableMapOf<TaintNode, MutableList<TaintNode>>()
//        val callExpr = callStatement.callExpr ?: error("Call statement should have non-null callExpr")
//        val args = callExpr.args
//        val params = callee.parameters.map {
//            JcArgument.of(it.index, it.name, classpath.findTypeOrNull(it.type.typeName)!!)
//        }
//
//        // We don't propagate locals to the callee
//        platform.flowGraph(callStatement.location.method).locals.forEach {
//            nonId[TaintNode.fromPath(AccessPath.fromLocal(it))] = mutableListOf()
//        }
//
//        // All nullable callee's locals are initialized as null
//        nonId[TaintNode.ZERO] = callee.domain
//            .filterIsInstance<JcLocalVar>()
//            .filter { it.type.nullable != false }
//            .map { TaintNode.fromPath(AccessPath.fromLocal(it)) }
//            .plus(TaintNode.ZERO)
//            .toMutableList()
//
//        // Propagate values passed to callee as parameters
//        params.zip(args).forEach { (param, arg) ->
//            arg.factLeadingToNull?.let {
//                nonId.getValue(it).add(TaintNode.fromPath(AccessPath.fromLocal(param)))
//            }
//        }
//
//        // todo: pass everything related to `this` if this is JcInstanceCallExpr
//        return IdLikeFlowFunction(callStatement.domain, nonId)
//    }
//
//    override fun obtainCallToReturnFlowFunction(
//        callStatement: JcInst,
//        returnSite: JcInst
//    ): FlowFunctionInstance<TaintNode> {
//        val nonId = mutableMapOf<TaintNode, List<TaintNode>>()
//        if (callStatement is JcAssignInst && callStatement.lhv is JcLocal) {
//            // Nullability of lhs of assignment will be handled by exit-to-return flow function
//            nonId[TaintNode.fromPath(AccessPath.fromLocal(callStatement.lhv as JcLocal))] = emptyList()
//        }
//        callStatement.dereferencedValue?.let {
//            nonId[TaintNode.fromPath(AccessPath.fromLocal(it))] = emptyList()
//        }
//        return IdLikeFlowFunction(callStatement.domain, nonId)
//    }
//
//    override fun obtainExitToReturnSiteFlowFunction(
//        callStatement: JcInst,
//        returnSite: JcInst,
//        exitStatement: JcInst
//    ): FlowFunctionInstance<TaintNode> {
//        val nonId = mutableMapOf<TaintNode, List<TaintNode>>()
//
//        // We shouldn't propagate locals back to caller
//        exitStatement.domain.forEach {
//            nonId[it] = emptyList()
//        }
//
//        // TODO: pass everything related to `this` back to caller
//
//        if (callStatement is JcAssignInst && exitStatement is JcReturnInst && callStatement.lhv is JcLocal) {
//            // Propagate results back to caller in case of assignment
//            exitStatement.returnValue?.factLeadingToNull?.let {
//                nonId[it] = listOf(TaintNode.fromPath(AccessPath.fromLocal(callStatement.lhv as JcLocal)))
//            }
//        }
//        return IdLikeFlowFunction(exitStatement.domain, nonId)
//    }
//}

fun runNPEWithPointsTo(
    graph: ApplicationGraph<JcMethod, JcInst>,
    platform: JcAnalysisPlatform,
    startMethod: JcMethod,
    devirtualizer: Devirtualizer<JcMethod, JcInst>?
): IFDSResult<JcMethod, JcInst, TaintNode> {
    val forwardInstance = IFDSInstance(graph, NPEForwardFunctions(platform.classpath, graph, platform), devirtualizer)
    val backwardInstance = IFDSInstance(graph.reversed, NPEBackwardFunctions(), devirtualizer)
    val instance = TaintAnalysisWithPointsTo(forwardInstance, backwardInstance)
    instance.addStart(startMethod)
    instance.run()
    return instance.collectResults()
}