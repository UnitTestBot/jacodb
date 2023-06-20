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

import org.jacodb.analysis.VulnerabilityInstance
import org.jacodb.analysis.engine.Analyzer
import org.jacodb.analysis.engine.DomainFact
import org.jacodb.analysis.engine.FlowFunctionsSpace
import org.jacodb.analysis.engine.IfdsResult
import org.jacodb.analysis.engine.IfdsVertex
import org.jacodb.analysis.engine.SpaceId
import org.jacodb.analysis.engine.ZEROFact
import org.jacodb.analysis.paths.AccessPath
import org.jacodb.analysis.paths.ElementAccessor
import org.jacodb.analysis.paths.FieldAccessor
import org.jacodb.analysis.paths.isDereferencedAt
import org.jacodb.analysis.paths.minus
import org.jacodb.analysis.paths.startsWith
import org.jacodb.analysis.paths.toPath
import org.jacodb.analysis.paths.toPathOrNull
import org.jacodb.api.JcArrayType
import org.jacodb.api.JcMethod
import org.jacodb.api.analysis.JcApplicationGraph
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
import org.jacodb.api.cfg.locals
import org.jacodb.api.cfg.values
import org.jacodb.api.ext.fields
import org.jacodb.api.ext.isNullable

class NpeAnalyzer(
    graph: JcApplicationGraph,
    maxPathLength: Int = 5
) : Analyzer {
    override val flowFunctions: FlowFunctionsSpace = NpeForwardFunctions(graph, maxPathLength)

    companion object : SpaceId {
        override val value: String = "npe-analysis"
    }

    override fun calculateSources(ifdsResult: IfdsResult): List<VulnerabilityInstance> {
        val vulnerabilities = mutableListOf<VulnerabilityInstance>()
        ifdsResult.resultFacts.forEach { (inst, facts) ->
            facts.filterIsInstance<NpeTaintNode>().forEach { fact ->
                if (fact.activation == null && fact.variable.isDereferencedAt(inst)) {
                    vulnerabilities.add(
                        VulnerabilityInstance(
                            value,
                            ifdsResult.resolveTaintRealisationsGraph(IfdsVertex(inst, fact))
                        )
                    )
                }
            }
        }
        return vulnerabilities
    }
}


private class NpeForwardFunctions(
    graph: JcApplicationGraph,
    private val maxPathLength: Int
) : AbstractTaintForwardFunctions(graph) {

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
            is NpeTaintNode -> fact.variable
            ZEROFact -> null
            else -> return emptyList()
        }

        if (factPath.isDereferencedAt(atInst)) {
            return emptyList()
        }

        if (from is JcNullConstant || (from is JcCallExpr && from.method.method.treatAsNullable)) {
            return if (fact == ZEROFact) {
                listOf(ZEROFact, NpeTaintNode(toPath)) // taint is generated here
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
                return listOf(ZEROFact, NpeTaintNode(arrayElemPath))
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

        fact as NpeTaintNode

        // TODO: slightly differs from original paper, think what's correct
        val fromPath = from.toPathOrNull()?.limit(maxPathLength) ?: return default

        return normalFactFlow(fact, fromPath, toPath, dropFact, maxPathLength)
    }

    override fun transmitDataFlowAtNormalInst(inst: JcInst, nextInst: JcInst, fact: DomainFact): List<DomainFact> {
        val factPath = when (fact) {
            is NpeTaintNode -> fact.variable
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
        val currentBranch = graph.methodOf(inst).flowGraph().ref(nextInst)
        if (fact == ZEROFact) {
            if (inst.pathComparedWithNull != null) {
                if ((inst.condition is JcEqExpr && currentBranch == inst.trueBranch) ||
                    (inst.condition is JcNeqExpr && currentBranch == inst.falseBranch)) {
                    // This is a hack: instructions like `return null` in branch of next will be considered only if
                    //  the fact holds (otherwise we could not get there)
                    return listOf(NpeTaintNode(inst.pathComparedWithNull!!))
                }
            }
            return listOf(ZEROFact)
        }

        fact as NpeTaintNode

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
        result += method.flowGraph().locals
            .filterIsInstance<JcArgument>()
            .filter { method.parameters[it.index].isNullable != false }
            .map { NpeTaintNode(AccessPath.fromLocal(it)) }

        // Possibly null statics
        // TODO: handle statics in a more general manner
        result += method.enclosingClass.fields
            .filter { it.isNullable != false && it.isStatic }
            .map { NpeTaintNode(AccessPath.fromStaticField(it)) }

        val thisInstance = method.thisInstance

        // Possibly null fields
        result += method.enclosingClass.fields
            .filter { it.isNullable != false && !it.isStatic }
            .map {
                NpeTaintNode(
                    AccessPath.fromOther(AccessPath.fromLocal(thisInstance), listOf(FieldAccessor(it)))
                )
            }

        return result
    }
}

class NpeFlowdroidBackwardAnalyzer(
    graph: JcApplicationGraph,
    maxPathLength: Int = 5
) : Analyzer {
    override val flowFunctions: FlowFunctionsSpace = NpeBackwardFunctions(graph, maxPathLength)

    override fun calculateSources(ifdsResult: IfdsResult): List<VulnerabilityInstance> {
        error("Should never be called")
    }
}

private class NpeBackwardFunctions(
    graph: JcApplicationGraph,
    maxPathLength: Int,
) : AbstractTaintBackwardFunctions(graph, maxPathLength) {
    override val inIds: List<SpaceId> = listOf(NpeAnalyzer, ZEROFact.id)
}

class NpePrecalcBackwardAnalyzer(
    graph: JcApplicationGraph,
    maxPathLength: Int = 5
) : Analyzer {
    override val flowFunctions: FlowFunctionsSpace = NpePrecalcBackwardFunctions(graph, maxPathLength)

    override fun calculateSources(ifdsResult: IfdsResult): List<VulnerabilityInstance> {
        return emptyList()
    }
}

class NpePrecalcBackwardFunctions(
    graph: JcApplicationGraph,
    maxPathLength: Int
) : AbstractTaintBackwardFunctions(graph, maxPathLength) {
    override val inIds: List<SpaceId> = listOf(NpeAnalyzer, ZEROFact.id)

    override fun transmitBackDataFlow(from: JcValue, to: JcExpr, atInst: JcInst, fact: DomainFact, dropFact: Boolean): List<DomainFact> {
        val thisInstance = atInst.location.method.thisInstance.toPath()
        if (fact == ZEROFact) {
            val derefs = atInst.values
                .mapNotNull { it.toPathOrNull() }
                .filter { it.isDereferencedAt(atInst) }
                .filterNot { it == thisInstance }
                .map { NpeTaintNode(it) }
            return listOf(ZEROFact) + derefs
        }

        if (fact !is TaintNode || fact.id !in inIds) {
            return emptyList()
        }

        val factPath = (fact as? TaintNode)?.variable
        val default = if (dropFact) emptyList() else listOf(fact)
        val toPath = to.toPathOrNull() ?: return default
        val fromPath = from.toPathOrNull() ?: return default

        val diff = factPath.minus(fromPath)
        if (diff != null) {
            return listOf(fact.moveToOtherPath(AccessPath.fromOther(toPath, diff).limit(maxPathLength))).filterNot {
                it.variable == thisInstance
            }
        }
        return default
    }

    override fun obtainStartFacts(startStatement: JcInst): List<DomainFact> {
        val values = startStatement.values
        return listOf(ZEROFact) + values
            .mapNotNull { it.toPathOrNull() }
            .filterNot { it == startStatement.location.method.thisInstance.toPath() }
            .map { NpeTaintNode(it) }
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