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
import org.jacodb.api.cfg.DefaultJcExprVisitor
import org.jacodb.api.cfg.JcArgument
import org.jacodb.api.cfg.JcAssignInst
import org.jacodb.api.cfg.JcCallExpr
import org.jacodb.api.cfg.JcCastExpr
import org.jacodb.api.cfg.JcDynamicCallExpr
import org.jacodb.api.cfg.JcEqExpr
import org.jacodb.api.cfg.JcExpr
import org.jacodb.api.cfg.JcIfInst
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.cfg.JcInstanceCallExpr
import org.jacodb.api.cfg.JcLocal
import org.jacodb.api.cfg.JcLocalVar
import org.jacodb.api.cfg.JcNeqExpr
import org.jacodb.api.cfg.JcNullConstant
import org.jacodb.api.cfg.JcReturnInst
import org.jacodb.api.cfg.JcSpecialCallExpr
import org.jacodb.api.cfg.JcStaticCallExpr
import org.jacodb.api.cfg.JcValue
import org.jacodb.api.cfg.JcVirtualCallExpr
import org.jacodb.api.ext.cfg.callExpr
import org.jacodb.api.ext.cfg.fieldRef
import org.jacodb.api.ext.isNullable
import org.jacodb.impl.analysis.locals

//class NPEForwardFunctions: FlowFunctionsSpace<JcMethod, JcInst, TaintNode> {
//    override fun obtainStartFacts(startStatement: JcInst): Collection<TaintNode> {
//        TODO("Not yet implemented")
//    }
//
//    override fun obtainSequentFlowFunction(current: JcInst, next: JcInst): FlowFunctionInstance<TaintNode> {
//        TODO("Not yet implemented")
//    }
//
//    override fun obtainCallToStartFlowFunction(
//        callStatement: JcInst,
//        callee: JcMethod
//    ): FlowFunctionInstance<TaintNode> {
//        TODO("Not yet implemented")
//    }
//
//    override fun obtainCallToReturnFlowFunction(
//        callStatement: JcInst,
//        returnSite: JcInst
//    ): FlowFunctionInstance<TaintNode> {
//        TODO("Not yet implemented")
//    }
//
//    override fun obtainExitToReturnSiteFlowFunction(
//        callStatement: JcInst,
//        returnSite: JcInst,
//        exitStatement: JcInst
//    ): FlowFunctionInstance<TaintNode> {
//        TODO("Not yet implemented")
//    }
//}

class NPEBackwardFunctions: FlowFunctionsSpace<JcMethod, JcInst, TaintNode> {
    override fun obtainStartFacts(startStatement: JcInst): Collection<TaintNode> {
        return emptyList()
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
class NPEForwardFunctions(
    private val classpath: JcClasspath,
    private val graph: ApplicationGraph<JcMethod, JcInst>,
    private val platform: JcAnalysisPlatform
): FlowFunctionsSpace<JcMethod, JcInst, TaintNode> {

    private val factLeadingToNullVisitor = object : DefaultJcExprVisitor<TaintNode?> {
        override val defaultExprHandler: (JcExpr) -> TaintNode?
            get() = { null }

        private fun visitJcLocal(value: JcLocal) = TaintNode(AccessPath.fromLocal(value), null)

        private fun visitJcCallExpr(expr: JcCallExpr): TaintNode? {
            if (expr.method.method.isNullable == true) {
                return TaintNode.ZERO
            }
            return null
        }

        override fun visitJcArgument(value: JcArgument) = visitJcLocal(value)

        override fun visitJcLocalVar(value: JcLocalVar) = visitJcLocal(value)

        override fun visitJcNullConstant(value: JcNullConstant) = TaintNode.ZERO

        override fun visitJcCastExpr(expr: JcCastExpr) = expr.operand.accept(this)

        override fun visitJcDynamicCallExpr(expr: JcDynamicCallExpr) = visitJcCallExpr(expr)

        override fun visitJcSpecialCallExpr(expr: JcSpecialCallExpr) = visitJcCallExpr(expr)

        override fun visitJcStaticCallExpr(expr: JcStaticCallExpr) = visitJcCallExpr(expr)

        override fun visitJcVirtualCallExpr(expr: JcVirtualCallExpr) = visitJcCallExpr(expr)

        // TODO: override others
    }

    // Returns a fact, such that if it holds, `this` expr is null (or null if there is no such fact)
    private val JcExpr.factLeadingToNull: TaintNode?
        get() = accept(factLeadingToNullVisitor)

    // TODO: think about name shadowing
    // Returns all local variables and arguments referenced by this method
    private val JcMethod.domain: Set<TaintNode>
        get() {
            return platform.flowGraph(this).locals
                .map { TaintNode(AccessPath.fromLocal(it), null) }
                .toSet()
                .plus(TaintNode.ZERO)
        }

    private val JcInst.domain: Set<TaintNode>
        get() = location.method.domain

    // Returns a value that is being dereferenced in this call
    private val JcInst.dereferencedValue: JcLocal?
        get() {
            (callExpr as? JcInstanceCallExpr)?.let {
                return it.instance as? JcLocal
            }

            return fieldRef?.instance as? JcLocal
        }

    override fun obtainStartFacts(startStatement: JcInst): Collection<TaintNode> {
        return startStatement.domain.filter { it.variable.value == null || it.variable.value.type.nullable != false }
    }

    override fun obtainSequentFlowFunction(current: JcInst, next: JcInst): FlowFunctionInstance<TaintNode> {
        val nonId = mutableMapOf<TaintNode, List<TaintNode>>()

        current.dereferencedValue?.let {
            nonId[TaintNode(AccessPath.fromLocal(it), null)] = emptyList()
        }

        if (current is JcAssignInst) {
            val lhv = current.lhv
            if (lhv is JcLocal) {
                nonId[TaintNode(AccessPath.fromLocal(lhv), null)] = listOf()
                current.rhv.factLeadingToNull?.let {
                    if (it.variable.value == null || it.variable.value != current.dereferencedValue) {
                        nonId[it] = setOf(it, TaintNode(AccessPath.fromLocal(lhv), null)).toList()
                    }
                }
            }
        }

        // This handles cases like if (x != null) expr1 else expr2, where edges to expr1 and to expr2 should be different
        // (because x == null will be held at expr2 but won't be held at expr1)
        if (current is JcIfInst) {
            val expr = current.condition
            var comparedValue: JcValue? = null

            if (expr.rhv is JcNullConstant && expr.lhv is JcLocal)
                comparedValue = expr.lhv
            else if (expr.lhv is JcNullConstant && expr.rhv is JcLocal)
                comparedValue = expr.rhv

            if (comparedValue !is JcLocal)
                return IdLikeFlowFunction(current.domain, nonId)

            val currentBranch = graph.methodOf(current).flowGraph().ref(next)
            when (expr) {
                is JcEqExpr -> {
                    comparedValue.let {
                        if (currentBranch == current.trueBranch) {
                            nonId[TaintNode.ZERO] = listOf(TaintNode.ZERO, TaintNode(AccessPath.fromLocal(comparedValue), null))
                        } else if (currentBranch == current.falseBranch) {
                            nonId[TaintNode(AccessPath.fromLocal(comparedValue), null)] = emptyList()
                        }
                    }
                }
                is JcNeqExpr -> {
                    comparedValue.let {
                        if (currentBranch == current.falseBranch) {
                            nonId[TaintNode.ZERO] = listOf(TaintNode.ZERO, TaintNode(AccessPath.fromLocal(comparedValue), null))
                        } else if (currentBranch == current.trueBranch) {
                            nonId[TaintNode(AccessPath.fromLocal(comparedValue), null)] = emptyList()
                        }
                    }
                }
                else -> Unit
            }
    }
        return IdLikeFlowFunction(current.domain, nonId)
    }

    override fun obtainCallToStartFlowFunction(
        callStatement: JcInst,
        callee: JcMethod
    ): FlowFunctionInstance<TaintNode> {
        val nonId = mutableMapOf<TaintNode, MutableList<TaintNode>>()
        val callExpr = callStatement.callExpr ?: error("Call statement should have non-null callExpr")
        val args = callExpr.args
        val params = callee.parameters.map {
            JcArgument.of(it.index, it.name, classpath.findTypeOrNull(it.type.typeName)!!)
        }

        // We don't propagate locals to the callee
        platform.flowGraph(callStatement.location.method).locals.forEach {
            nonId[TaintNode(AccessPath.fromLocal(it), null)] = mutableListOf()
        }

        // All nullable callee's locals are initialized as null
        nonId[TaintNode.ZERO] = callee.domain
            .filterIsInstance<JcLocalVar>()
            .filter { it.type.nullable != false }
            .map { TaintNode(AccessPath.fromLocal(it), null) }
            .plus(TaintNode.ZERO)
            .toMutableList()

        // Propagate values passed to callee as parameters
        params.zip(args).forEach { (param, arg) ->
            arg.factLeadingToNull?.let {
                nonId.getValue(it).add(TaintNode(AccessPath.fromLocal(param), null))
            }
        }

        // todo: pass everything related to `this` if this is JcInstanceCallExpr
        return IdLikeFlowFunction(callStatement.domain, nonId)
    }

    override fun obtainCallToReturnFlowFunction(
        callStatement: JcInst,
        returnSite: JcInst
    ): FlowFunctionInstance<TaintNode> {
        val nonId = mutableMapOf<TaintNode, List<TaintNode>>()
        if (callStatement is JcAssignInst && callStatement.lhv is JcLocal) {
            // Nullability of lhs of assignment will be handled by exit-to-return flow function
            nonId[TaintNode(AccessPath.fromLocal(callStatement.lhv as JcLocal), null)] = emptyList()
        }
        callStatement.dereferencedValue?.let {
            nonId[TaintNode(AccessPath.fromLocal(it), null)] = emptyList()
        }
        return IdLikeFlowFunction(callStatement.domain, nonId)
    }

    override fun obtainExitToReturnSiteFlowFunction(
        callStatement: JcInst,
        returnSite: JcInst,
        exitStatement: JcInst
    ): FlowFunctionInstance<TaintNode> {
        val nonId = mutableMapOf<TaintNode, List<TaintNode>>()

        // We shouldn't propagate locals back to caller
        exitStatement.domain.forEach {
            nonId[it] = emptyList()
        }

        // TODO: pass everything related to `this` back to caller

        if (callStatement is JcAssignInst && exitStatement is JcReturnInst && callStatement.lhv is JcLocal) {
            // Propagate results back to caller in case of assignment
            exitStatement.returnValue?.factLeadingToNull?.let {
                nonId[it] = listOf(TaintNode(AccessPath.fromLocal(callStatement.lhv as JcLocal), null))
            }
        }
        return IdLikeFlowFunction(exitStatement.domain, nonId)
    }
}

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