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

import org.jacodb.analysis.DumpableAnalysisResult
import org.jacodb.analysis.VulnerabilityInstance
import org.jacodb.analysis.engine.*
import org.jacodb.analysis.paths.*
import org.jacodb.api.JcArrayType
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcMethod
import org.jacodb.api.analysis.ApplicationGraph
import org.jacodb.api.analysis.JcAnalysisPlatform
import org.jacodb.api.cfg.*
import org.jacodb.api.ext.cfg.callExpr
import org.jacodb.api.ext.fields
import org.jacodb.api.ext.isNullable
import org.jacodb.api.ext.isStatic
import org.jacodb.impl.analysis.locals

class NpeAnalyzer(
    classpath: JcClasspath,
    graph: ApplicationGraph<JcMethod, JcInst>,
    platform: JcAnalysisPlatform,
    maxPathLength: Int = 5
) : Analyzer {
    override val flowFunctions: FlowFunctionsSpace = NPEForwardFunctions(classpath, graph, platform, maxPathLength)
    override val backward: Analyzer = object : Analyzer {
        override val backward: Analyzer
            get() = this@NpeAnalyzer
        override val flowFunctions: FlowFunctionsSpace
            get() = this@NpeAnalyzer.flowFunctions.backward

        override fun calculateSources(ifdsResult: IFDSResult): DumpableAnalysisResult {
            TODO("Do not call sources for backward")
        }
    }

    companion object : SpaceId {
        override val value: String = "npe-analysis"
    }

    override fun calculateSources(ifdsResult: IFDSResult): DumpableAnalysisResult {
        val npes = findNPEInstructions(ifdsResult)
        val vulnerabilities = npes.map {
            VulnerabilityInstance(
                NpeAnalyzer.value,
                it.source.toString(),
                it.path.toString(),
                it.possibleStackTrace.map { it.toString() })
        }
        return DumpableAnalysisResult(vulnerabilities)
    }

    private data class NPELocation(val source: JcInst, val path: AccessPath, val possibleStackTrace: List<JcInst>)

    /**
     * The method finds all places where NPE may occur
     */
    private fun findNPEInstructions(ifdsResults: IFDSResult): List<NPELocation> {
        val possibleNPEInstructions = mutableListOf<NPELocation>()
        for (method in ifdsResults.graph.visitedMethods()) {
            ifdsResults.resultFacts.forEach { (inst, facts) ->
                (facts as Set<TaintNode>).forEach { fact ->
                    if (fact.activation == null && fact.variable.isDereferencedAt(inst)) {
                        val possibleStackTrace = ifdsResults.resolvePossibleStackTrace(
                            IFDSVertex(inst, fact), method
                        )
                        possibleNPEInstructions.add(NPELocation(inst, fact.variable!!, possibleStackTrace))
                    }
                }
            }
        }
        return possibleNPEInstructions
    }
}


private class NPEForwardFunctions(
    private val classpath: JcClasspath,
    private val graph: ApplicationGraph<JcMethod, JcInst>,
    private val platform: JcAnalysisPlatform,
    private val maxPathLength: Int = 5
) : FlowFunctionsSpace {

    private fun transmitDataFlow(from: JcExpr, to: JcValue, fact: TaintNode, dropFact: Boolean): List<TaintNode> {
        val factPath = fact.variable

        val default = if (dropFact || factPath.isDereferencedAt(from) || factPath.isDereferencedAt(to)) {
            emptyList()
        } else {
            listOf(fact)
        }

        val toPath = to.toPathOrNull()?.limit(maxPathLength) ?: return default

        // TODO: change hardcoded "getProperty" to something more adequate
        if (from is JcNullConstant || (from is JcCallExpr && from.method.method.treatAsNullable)) {
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

        if (from is JcNewArrayExpr && fact == TaintNode.ZERO) {
            val arrayType = from.type as JcArrayType
            if (arrayType.elementType.nullable != false) {
                val arrayElemPath = AccessPath.fromOther(toPath, List(arrayType.dimensions) { ElementAccessor })
                return listOf(TaintNode.ZERO, TaintNode.fromPath(arrayElemPath, fact.activation))
            }
        }

        if (from is JcNewExpr || from is JcNewArrayExpr || from is JcConstant || (from is JcCallExpr && !from.method.method.treatAsNullable)) {
            return if (factPath.startsWith(toPath)) {
                emptyList() // new kills the fact here
            } else {
                default
            }
        }

        // TODO: slightly differs from original paper, think what's correct
        val fromPath = from.toPathOrNull()?.limit(maxPathLength) ?: return default
        if (factPath.startsWith(fromPath)) {
            val diff = factPath.minus(fromPath)!!
            return default
                .plus(TaintNode.fromPath(AccessPath.fromOther(toPath, diff).limit(maxPathLength), fact.activation))
                .distinct()
        }

        if (factPath.startsWith(toPath)) {
            return emptyList()
        }
        return default
    }

    override val id: SpaceId get() = NpeAnalyzer

    override fun obtainStartFacts(startStatement: JcInst): Collection<TaintNode> {
        val result = mutableListOf(TaintNode.ZERO)

        val method = startStatement.location.method

        // Note that here and below we intentionally don't expand fields because this may cause
        //  an increase of false positives and significant performance drop

        // Possibly null arguments
        result += platform.flowGraph(method).locals
            .filterIsInstance<JcArgument>()
            .filter { it.type.nullable != false }
            .map { TaintNode.fromPath(AccessPath.fromLocal(it)) }

        // Possibly null statics
        // TODO: handle statics in a more general manner
        result += method.enclosingClass.fields
            .filter { it.isNullable != false && it.isStatic }
            .map { TaintNode.fromPath(AccessPath.fromStaticField(it)) }

        val thisInstance = platform.flowGraph(method).locals.filterIsInstance<JcThis>().singleOrNull()
            ?: return result

        // Possibly null fields
        result += method.enclosingClass.fields
            .filter { it.isNullable != false && !it.isStatic }
            .map {
                TaintNode.fromPath(
                    AccessPath.fromOther(AccessPath.fromLocal(thisInstance), listOf(FieldAccessor(it)))
                )
            }

        return result
    }

    override fun obtainSequentFlowFunction(current: JcInst, next: JcInst): FlowFunctionInstance =
        object : FlowFunctionInstance {
            override val spaceId = NpeAnalyzer
            override fun compute(fact: DomainFact): Collection<TaintNode> {
                fact as TaintNode
                if (fact.activation == current) {
                    return listOf(fact.activatedCopy)
                }
                val default = if (fact.variable.isDereferencedAt(current)) emptyList() else listOf(fact)
                if (current is JcAssignInst) {
                    return transmitDataFlow(current.rhv, current.lhv, fact, dropFact = false)
                }

                // This handles cases like if (x != null) expr1 else expr2, where edges to expr1 and to expr2 should be different
                // (because x == null will be held at expr2 but won't be held at expr1)
                if (current is JcIfInst) {
                    val expr = current.condition
                    val comparedPath = if (expr.rhv is JcNullConstant) {
                        expr.lhv.toPathOrNull()?.limit(maxPathLength)
                    } else if (expr.lhv is JcNullConstant) {
                        expr.rhv.toPathOrNull()?.limit(maxPathLength)
                    } else {
                        null
                    }

                    if (comparedPath == null)
                        return default

                    val currentBranch = graph.methodOf(current).flowGraph().ref(next)
                    if ((expr is JcEqExpr && currentBranch == current.trueBranch) || (expr is JcNeqExpr && currentBranch == current.falseBranch)) {
                        // comparedPath is null in this branch
                        if (fact == TaintNode.ZERO)
                            return listOf(TaintNode.fromPath(comparedPath))
                        if (fact.variable.startsWith(comparedPath) && fact.activation == null) {
                            if (fact.variable == comparedPath) {
                                // This is a hack: instructions like `return null` in branch of next will be considered only if
                                //  the fact holds (otherwise we could not get there)
                                return listOf(TaintNode.ZERO)
                            }
                            return emptyList()
                        }
                        return default
                    }
                    if ((expr is JcEqExpr && currentBranch == current.falseBranch) || (expr is JcNeqExpr && currentBranch == current.trueBranch)) {
                        // comparedPath is not null in this branch
                        if (fact.variable == comparedPath)
                            return emptyList()
                        return default
                    }
                }

                return default
            }
        }

    override fun obtainCallToStartFlowFunction(
        callStatement: JcInst,
        callee: JcMethod
    ): FlowFunctionInstance = object : FlowFunctionInstance {

        override val spaceId = NpeAnalyzer
        override fun compute(fact: DomainFact): Collection<TaintNode> {
            fact as TaintNode
            if (fact.activation == callStatement) {
                return emptyList()
            }
            // NB: in our jimple formalParameters are immutable => we can safely
            // pass paths from caller to callee and back by just renaming them

            val ans = mutableListOf<TaintNode>()
            val callExpr = callStatement.callExpr ?: error("Call statement should have non-null callExpr")
            val actualParams = callExpr.args
            val formalParams = callee.parameters.map {
                JcArgument.of(it.index, it.name, classpath.findTypeOrNull(it.type.typeName)!!)
            }

            formalParams.zip(actualParams).forEach { (formal, actual) ->
                ans += transmitDataFlow(actual, formal, fact, dropFact = true)
            }

            if (callExpr is JcInstanceCallExpr) {
                val thisInstance = JcThis(callExpr.instance.type)
                ans += transmitDataFlow(callExpr.instance, thisInstance, fact, dropFact = true).filterNot {
                    it.variable == thisInstance.toPath()
                }
            }

            if (fact == TaintNode.ZERO || fact.variable?.isStatic == true) {
                ans.add(fact)
            }

            return ans
        }
    }

    override fun obtainCallToReturnFlowFunction(
        callStatement: JcInst,
        returnSite: JcInst
    ): FlowFunctionInstance = object : FlowFunctionInstance {

        override val spaceId = NpeAnalyzer
        override fun compute(fact: DomainFact): Collection<TaintNode> {
            fact as TaintNode
            if (fact == TaintNode.ZERO)
                return listOf(fact)

            if (fact.activation == callStatement) {
                return listOf(fact.activatedCopy)
            }

            val callExpr = callStatement.callExpr ?: error("Call statement should have non-null callExpr")
            val actualParams = callExpr.args
            val default = if (fact.variable.isDereferencedAt(callStatement)) emptyList() else listOf(fact)

            if (fact.variable?.isStatic == true) {
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

            return default
        }
    }

    override fun obtainExitToReturnSiteFlowFunction(
        callStatement: JcInst,
        returnSite: JcInst,
        exitStatement: JcInst
    ): FlowFunctionInstance = object : FlowFunctionInstance {

        override val spaceId = NpeAnalyzer
        override fun compute(fact: DomainFact): Collection<TaintNode> {
            fact as TaintNode
            val ans = mutableListOf<TaintNode>()
            val callExpr = callStatement.callExpr ?: error("Call statement should have non-null callExpr")
            val actualParams = callExpr.args
            val callee = exitStatement.location.method
            // TODO: maybe we can always use fact instead of updatedFact here
            val updatedFact =
                if (fact.activation?.location?.method == callee) fact.copy(activation = callStatement) else fact
            val formalParams = callee.parameters.map {
                JcArgument.of(it.index, it.name, classpath.findTypeOrNull(it.type.typeName)!!)
            }

            if (fact.variable?.isOnHeap == true) {
                // If there is some method A.f(formal: T) that is called like A.f(actual) then
                //  1. For all g^k, k >= 1, we should propagate back from formal.g^k to actual.g^k (as they are on heap)
                //  2. We shouldn't propagate from formal to actual (as formal is local)
                //  Second case is why we need check for isOnHeap
                // TODO: add test for handling of 2nd case
                formalParams.zip(actualParams).forEach { (formal, actual) ->
                    ans += transmitDataFlow(formal, actual, updatedFact, dropFact = true)
                }
            }

            if (callExpr is JcInstanceCallExpr) {
                ans += transmitDataFlow(JcThis(callExpr.instance.type), callExpr.instance, updatedFact, dropFact = true)
            }

            if (callStatement is JcAssignInst && exitStatement is JcReturnInst) {
                exitStatement.returnValue?.let { // returnValue can be null here in some weird cases, for e.g. lambda
                    ans += transmitDataFlow(it, callStatement.lhv, updatedFact, dropFact = true)
                }
            }

            if (fact.variable?.isStatic == true && fact !in ans) {
                ans.add(fact)
            }

            return ans
        }
    }

    override val backward: FlowFunctionsSpace by lazy { NPEBackwardFunctions(classpath, graph, platform, this, maxPathLength) }
}

private class NPEBackwardFunctions(
    private val classpath: JcClasspath,
    private val graph: ApplicationGraph<JcMethod, JcInst>,
    private val platform: JcAnalysisPlatform,
    override val backward: FlowFunctionsSpace,
    private val maxPathLength: Int = 5,
) : FlowFunctionsSpace {
    override fun obtainStartFacts(startStatement: JcInst): Collection<TaintNode> {
        return emptyList()
    }
    override val id: SpaceId = NpeAnalyzer
    private fun transmitBackDataFlow(from: JcValue, to: JcExpr, fact: TaintNode, dropFact: Boolean): List<TaintNode> {
        val factPath = fact.variable
        val default = if (dropFact) emptyList() else listOf(fact)
        val toPath = to.toPathOrNull() ?: return default
        val fromPath = from.toPathOrNull() ?: return default

        if (factPath.startsWith(fromPath)) {
            val diff = factPath.minus(fromPath)!!
            return listOf(TaintNode.fromPath(AccessPath.fromOther(toPath, diff).limit(maxPathLength), fact.activation))
        }
        return default
    }

    override fun obtainSequentFlowFunction(current: JcInst, next: JcInst): FlowFunctionInstance =
        object : FlowFunctionInstance {

            override val spaceId = NpeAnalyzer
            override fun compute(fact: DomainFact): Collection<TaintNode> {
                fact as TaintNode
                // fact.activation != current needed here to jump over assignment where the fact appeared
                return if (current is JcAssignInst && fact.activation != current) {
                    transmitBackDataFlow(current.lhv, current.rhv, fact, dropFact = false)
                } else {
                    listOf(fact)
                }
            }
        }

    override fun obtainCallToStartFlowFunction(
        callStatement: JcInst,
        callee: JcMethod
    ): FlowFunctionInstance = object: FlowFunctionInstance {

        override val spaceId = NpeAnalyzer
        override fun compute(fact: DomainFact): Collection<TaintNode> {
            fact as TaintNode
            val ans = mutableListOf<TaintNode>()
            val callExpr = callStatement.callExpr ?: error("Call statement should have non-null callExpr")

            // TODO: think about activation point handling for statics here
            if (fact == TaintNode.ZERO || fact.variable?.isStatic == true)
                ans.add(fact)

            if (callStatement is JcAssignInst) {
                graph.entryPoint(callee).filterIsInstance<JcReturnInst>().forEach { returnInst ->
                    returnInst.returnValue?.let {
                        ans += transmitBackDataFlow(callStatement.lhv, it, fact, dropFact = true)
                    }
                }
            }

            if (callExpr is JcInstanceCallExpr) {
                val thisInstance = JcThis(callExpr.instance.type)
                ans += transmitBackDataFlow(callExpr.instance, thisInstance, fact, dropFact = true).filterNot {
                    it.variable == thisInstance.toPath()
                }
            }

            val formalParams = callee.parameters.map {
                JcArgument.of(it.index, it.name, classpath.findTypeOrNull(it.type.typeName)!!)
            }

            callExpr.args.zip(formalParams).forEach { (actual, formal) ->
                // FilterNot is needed for reasons described in comment for symmetric case in NPEForwardFunctions.obtainExitToReturnSiteFlowFunction
                ans += transmitBackDataFlow(actual, formal, fact, dropFact = true)
                    .filterNot { it.variable?.isOnHeap != true }
            }

            return ans
        }
    }

    override fun obtainCallToReturnFlowFunction(
        callStatement: JcInst,
        returnSite: JcInst
    ): FlowFunctionInstance = object: FlowFunctionInstance {

        override val spaceId = NpeAnalyzer
        override fun compute(fact: DomainFact): Collection<TaintNode> {
            fact as TaintNode
            val factPath = fact.variable ?: return listOf(fact)
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

            return listOf (fact)
        }
    }

    override fun obtainExitToReturnSiteFlowFunction(
        callStatement: JcInst,
        returnSite: JcInst,
        exitStatement: JcInst
    ): FlowFunctionInstance = object: FlowFunctionInstance {

        override val spaceId = NpeAnalyzer
        override fun compute(fact: DomainFact): Collection<TaintNode> {
            fact as TaintNode
            val ans = mutableListOf<TaintNode>()
            val callExpr = callStatement.callExpr ?: error("Call statement should have non-null callExpr")
            val actualParams = callExpr.args
            val callee = graph.methodOf(exitStatement)
            val formalParams = callee.parameters.map {
                JcArgument.of(it.index, it.name, classpath.findTypeOrNull(it.type.typeName)!!)
            }

            formalParams.zip(actualParams).forEach { (formal, actual) ->
                ans += transmitBackDataFlow(formal, actual, fact, dropFact = true)
            }

            if (callExpr is JcInstanceCallExpr) {
                ans += transmitBackDataFlow(JcThis(callExpr.instance.type), callExpr.instance, fact, dropFact = true)
            }

            if (fact.variable?.isStatic == true) {
                ans.add(fact)
            }

            return ans
        }
    }
}

private val JcMethod.treatAsNullable: Boolean
    get() {
        if (isNullable == true) {
            return true
        }
        return "${enclosingClass.name}.$name" in knownNullableMethods
    }

private val knownNullableMethods = listOf(
    "java.lang.System.getProperty",
    "java.util.Properties.getProperty"
)