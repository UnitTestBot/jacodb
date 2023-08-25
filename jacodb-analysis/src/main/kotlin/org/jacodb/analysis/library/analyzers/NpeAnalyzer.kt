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

import org.jacodb.analysis.engine.AbstractAnalyzer
import org.jacodb.analysis.engine.AnalyzerFactory
import org.jacodb.analysis.engine.CrossUnitCallFact
import org.jacodb.analysis.engine.DomainFact
import org.jacodb.analysis.engine.FlowFunctionsSpace
import org.jacodb.analysis.engine.IfdsEdge
import org.jacodb.analysis.engine.IfdsUnitManager
import org.jacodb.analysis.engine.VulnerabilityLocation
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
import org.jacodb.api.JcClasspath
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

fun NpeAnalyzerFactory(maxPathLength: Int) = AnalyzerFactory { graph ->
    NpeAnalyzer(graph, maxPathLength)
}

class NpeAnalyzer(graph: JcApplicationGraph, maxPathLength: Int) : AbstractAnalyzer(graph) {
    override val flowFunctions: FlowFunctionsSpace = NpeForwardFunctions(graph.classpath, maxPathLength)

    override val saveSummaryEdgesAndCrossUnitCalls: Boolean
        get() = true

    companion object {
        const val vulnerabilityType: String = "npe-analysis"
    }

    override fun handleNewEdge(edge: IfdsEdge, manager: IfdsUnitManager<*>) {
        val (inst, fact0) = edge.v

        if (fact0 is NpeTaintNode && fact0.activation == null && fact0.variable.isDereferencedAt(inst)) {
            manager.uploadSummaryFact(VulnerabilityLocation(vulnerabilityType, edge.v))
            verticesWithTraceGraphNeeded.add(edge.v)
        }

        super.handleNewEdge(edge, manager)
    }

    override fun handleNewCrossUnitCall(fact: CrossUnitCallFact, manager: IfdsUnitManager<*>) {
        manager.addEdgeForOtherRunner(IfdsEdge(fact.calleeVertex, fact.calleeVertex))
        super.handleNewCrossUnitCall(fact, manager)
    }
}

private class NpeForwardFunctions(
    cp: JcClasspath,
    private val maxPathLength: Int
) : AbstractTaintForwardFunctions(cp) {

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
        val default = if (dropFact && fact != ZEROFact) emptyList() else listOf(fact)
        val toPath = to.toPathOrNull()?.limit(maxPathLength) ?: return default

        if (fact == ZEROFact) {
            return if (from is JcNullConstant || (from is JcCallExpr && from.method.method.treatAsNullable)) {
                listOf(ZEROFact, NpeTaintNode(toPath)) // taint is generated here
            } else if (from is JcNewArrayExpr && (from.type as JcArrayType).elementType.nullable != false) {
                val arrayElemPath = AccessPath.fromOther(toPath, List((from.type as JcArrayType).dimensions) { ElementAccessor })
                listOf(ZEROFact, NpeTaintNode(arrayElemPath.limit(maxPathLength)))
            } else {
                listOf(ZEROFact)
            }
        }

        if (fact !is NpeTaintNode) {
            return emptyList()
        }

        val factPath = fact.variable
        if (factPath.isDereferencedAt(atInst)) {
            return emptyList()
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
        val nextInstIsTrueBranch = nextInst.location.index == inst.trueBranch.index
        if (fact == ZEROFact) {
            if (inst.pathComparedWithNull != null) {
                if ((inst.condition is JcEqExpr && nextInstIsTrueBranch) ||
                    (inst.condition is JcNeqExpr && !nextInstIsTrueBranch)
                ) {
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
        if (inst.pathComparedWithNull != fact.variable) {
            return listOf(fact)
        }

        return if ((expr is JcEqExpr && nextInstIsTrueBranch) || (expr is JcNeqExpr && !nextInstIsTrueBranch)) {
            // comparedPath is null in this branch
            listOf(ZEROFact)
        } else {
            emptyList()
        }
    }

    override fun obtainPossibleStartFacts(startStatement: JcInst): Collection<DomainFact> {
        val result = mutableListOf<DomainFact>(ZEROFact)
//        return result

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

        // Possibly null public non-final fields
        result += method.enclosingClass.fields
            .filter { it.isNullable != false && !it.isStatic && it.isPublic && !it.isFinal }
            .map {
                NpeTaintNode(
                    AccessPath.fromOther(AccessPath.fromLocal(thisInstance), listOf(FieldAccessor(it)))
                )
            }

        return result
    }
}

fun NpePrecalcBackwardAnalyzerFactory(maxPathLength: Int) = AnalyzerFactory { graph ->
    NpePrecalcBackwardAnalyzer(graph, maxPathLength)
}

private class NpePrecalcBackwardAnalyzer(val graph: JcApplicationGraph, maxPathLength: Int) : AbstractAnalyzer(graph) {
    override val flowFunctions: FlowFunctionsSpace = NpePrecalcBackwardFunctions(graph, maxPathLength)

    override val saveSummaryEdgesAndCrossUnitCalls: Boolean
        get() = false

    override fun handleNewEdge(edge: IfdsEdge, manager: IfdsUnitManager<*>) {
        if (edge.v.statement in graph.exitPoints(edge.method)) {
            manager.addEdgeForOtherRunner(IfdsEdge(edge.v, edge.v))
        }
    }
}

class NpePrecalcBackwardFunctions(graph: JcApplicationGraph, maxPathLength: Int)
    : AbstractTaintBackwardFunctions(graph, maxPathLength) {
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

        if (fact !is TaintNode) {
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

    override fun transmitDataFlowAtNormalInst(inst: JcInst, nextInst: JcInst, fact: DomainFact): List<DomainFact> =
        listOf(fact)

    override fun obtainPossibleStartFacts(startStatement: JcInst): List<DomainFact> {
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