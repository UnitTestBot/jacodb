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
import org.jacodb.analysis.engine.Analyzer
import org.jacodb.analysis.engine.DomainFact
import org.jacodb.analysis.engine.FlowFunctionsSpace
import org.jacodb.analysis.engine.IFDSResult
import org.jacodb.analysis.engine.IFDSVertex
import org.jacodb.analysis.engine.SpaceId
import org.jacodb.analysis.engine.ZEROFact
import org.jacodb.analysis.paths.AccessPath
import org.jacodb.analysis.paths.ElementAccessor
import org.jacodb.analysis.paths.FieldAccessor
import org.jacodb.analysis.paths.isDereferencedAt
import org.jacodb.analysis.paths.startsWith
import org.jacodb.analysis.paths.toPathOrNull
import org.jacodb.api.JcArrayType
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcMethod
import org.jacodb.api.analysis.ApplicationGraph
import org.jacodb.api.analysis.JcAnalysisPlatform
import org.jacodb.api.cfg.JcArgument
import org.jacodb.api.cfg.JcCallExpr
import org.jacodb.api.cfg.JcConstant
import org.jacodb.api.cfg.JcEqExpr
import org.jacodb.api.cfg.JcExpr
import org.jacodb.api.cfg.JcIfInst
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.cfg.JcNeqExpr
import org.jacodb.api.cfg.JcNewArrayExpr
import org.jacodb.api.cfg.JcNewExpr
import org.jacodb.api.cfg.JcNullConstant
import org.jacodb.api.cfg.JcValue
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
            error("Do not call sources for backward analyzer instance")
        }
    }

    companion object : SpaceId {
        override val value: String = "npe-analysis"
    }

    override fun calculateSources(ifdsResult: IFDSResult): DumpableAnalysisResult {
        val vulnerabilities = mutableListOf<VulnerabilityInstance>()
        ifdsResult.resultFacts.forEach { (inst, facts) ->
            facts.filterIsInstance<NPETaintNode>().forEach { fact ->
                if (fact.activation == null && fact.variable.isDereferencedAt(inst)) {
                    vulnerabilities.add(
                        ifdsResult.resolveTaintRealisationsGraph(IFDSVertex(inst, fact)).toVulnerability(value)
                    )
                }
            }
        }
        return DumpableAnalysisResult(vulnerabilities)
    }
}


private class NPEForwardFunctions(
    classpath: JcClasspath,
    graph: ApplicationGraph<JcMethod, JcInst>,
    platform: JcAnalysisPlatform,
    maxPathLength: Int
) : AbstractTaintForwardFunctions(classpath, graph, platform, maxPathLength) {

    override val inIds: List<SpaceId> get() = listOf(NpeAnalyzer, ZEROFact.id)

    private val JcIfInst.pathComparedWithNull: AccessPath?
        get() {
            val expr = condition
            return if (expr.rhv is JcNullConstant) {
                expr.lhv.toPathOrNull()?.limit(maxPathLength)
            } else if (expr.lhv is JcNullConstant) {
                expr.rhv.toPathOrNull()?.limit(maxPathLength)
            } else {
                null
            }
        }

    override fun transmitDataFlow(from: JcExpr, to: JcValue, atInst: JcInst, fact: DomainFact, dropFact: Boolean): List<DomainFact> {
        val default = if (dropFact) emptyList() else listOf(fact)
        val toPath = to.toPathOrNull()?.limit(maxPathLength) ?: return default
        val factPath = when (fact) {
            is NPETaintNode -> fact.variable
            ZEROFact -> null
            else -> return emptyList()
        }

        if (factPath.isDereferencedAt(atInst)) {
            return emptyList()
        }

        if (from is JcNullConstant || (from is JcCallExpr && from.method.method.treatAsNullable)) {
            return if (fact == ZEROFact) {
                listOf(ZEROFact, NPETaintNode(toPath)) // taint is generated here
            } else {
                if (factPath.startsWith(toPath)) {
                    emptyList()
                } else {
                    default
                }
            }
        }

        if (from is JcNewArrayExpr && fact == ZEROFact) {
            val arrayType = from.type as JcArrayType
            if (arrayType.elementType.nullable != false) {
                val arrayElemPath = AccessPath.fromOther(toPath, List(arrayType.dimensions) { ElementAccessor })
                return listOf(ZEROFact, NPETaintNode(arrayElemPath))
            }
        }

        if (from is JcNewExpr || from is JcNewArrayExpr || from is JcConstant || (from is JcCallExpr && !from.method.method.treatAsNullable)) {
            return if (factPath.startsWith(toPath)) {
                emptyList() // new kills the fact here
            } else {
                default
            }
        }

        if (fact == ZEROFact) {
            return listOf(ZEROFact)
        }

        fact as NPETaintNode

        // TODO: slightly differs from original paper, think what's correct
        val fromPath = from.toPathOrNull()?.limit(maxPathLength) ?: return default

        return normalFactFlow(fact, fromPath, toPath, dropFact, maxPathLength)
    }

    override fun transmitDataFlowAtNormalInst(inst: JcInst, nextInst: JcInst, fact: DomainFact): List<DomainFact> {
        val factPath = when (fact) {
            is NPETaintNode -> fact.variable
            ZEROFact -> null
            else -> return emptyList()
        }

        if (factPath.isDereferencedAt(inst)) {
            return emptyList()
        }

        if (inst !is JcIfInst) {
            return listOf(fact)
        }

        // Following are some ad-hoc magic for if statements to change facts after instructions like if (x != null)
        val currentBranch = platform.flowGraph(graph.methodOf(inst)).ref(nextInst)
        if (fact == ZEROFact) {
            if (inst.pathComparedWithNull != null) {
                if ((inst.condition is JcEqExpr && currentBranch == inst.trueBranch) ||
                    (inst.condition is JcNeqExpr && currentBranch == inst.falseBranch)) {
                    // This is a hack: instructions like `return null` in branch of next will be considered only if
                    //  the fact holds (otherwise we could not get there)
                    return listOf(NPETaintNode(inst.pathComparedWithNull!!))
                }
            }
            return listOf(ZEROFact)
        }

        fact as NPETaintNode

        // This handles cases like if (x != null) expr1 else expr2, where edges to expr1 and to expr2 should be different
        // (because x == null will be held at expr2 but won't be held at expr1)
        val expr = inst.condition
        val comparedPath = inst.pathComparedWithNull ?: return listOf(fact)

        if ((expr is JcEqExpr && currentBranch == inst.trueBranch) || (expr is JcNeqExpr && currentBranch == inst.falseBranch)) {
            // comparedPath is null in this branch
            if (fact.variable.startsWith(comparedPath) && fact.activation == null) {
                if (fact.variable == comparedPath) {
                    return listOf(ZEROFact)
                }
                return emptyList()
            }
            return listOf(fact)
        } else {
            // comparedPath is not null in this branch
            if (fact.variable == comparedPath)
                return emptyList()
            return listOf(fact)
        }
    }

    override fun obtainStartFacts(startStatement: JcInst): Collection<DomainFact> {
        val result = mutableListOf<DomainFact>(ZEROFact)

        val method = startStatement.location.method

        // Note that here and below we intentionally don't expand fields because this may cause
        //  an increase of false positives and significant performance drop

        // Possibly null arguments
        result += platform.flowGraph(method).locals
            .filterIsInstance<JcArgument>()
            .filter { it.type.nullable != false }
            .map { NPETaintNode(AccessPath.fromLocal(it)) }

        // Possibly null statics
        // TODO: handle statics in a more general manner
        result += method.enclosingClass.fields
            .filter { it.isNullable != false && it.isStatic }
            .map { NPETaintNode(AccessPath.fromStaticField(it)) }

        val thisInstance = method.thisInstance

        // Possibly null fields
        result += method.enclosingClass.fields
            .filter { it.isNullable != false && !it.isStatic }
            .map {
                NPETaintNode(
                    AccessPath.fromOther(AccessPath.fromLocal(thisInstance), listOf(FieldAccessor(it)))
                )
            }

        return result
    }

    override val backward: FlowFunctionsSpace by lazy { NPEBackwardFunctions(classpath, graph, platform, this, maxPathLength) }
}

private class NPEBackwardFunctions(
    classpath: JcClasspath,
    graph: ApplicationGraph<JcMethod, JcInst>,
    platform: JcAnalysisPlatform,
    backward: FlowFunctionsSpace,
    maxPathLength: Int,
) : AbstractTaintBackwardFunctions(classpath, graph, platform, backward, maxPathLength) {
    override val inIds: List<SpaceId> = listOf(NpeAnalyzer, ZEROFact.id)
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