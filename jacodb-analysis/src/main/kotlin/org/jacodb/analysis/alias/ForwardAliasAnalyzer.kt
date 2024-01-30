package org.jacodb.analysis.alias

import org.jacodb.analysis.alias.apg.AccessGraph
import org.jacodb.analysis.alias.apg.isEmpty
import org.jacodb.analysis.alias.apg.matches
import org.jacodb.analysis.alias.flow.AliasFlowFunctions
import org.jacodb.analysis.graph.JcNoopInst
import org.jacodb.analysis.ifds2.Analyzer
import org.jacodb.analysis.ifds2.Edge
import org.jacodb.analysis.ifds2.Vertex
import org.jacodb.api.JcMethod
import org.jacodb.api.analysis.JcApplicationGraph
import org.jacodb.api.cfg.JcAssignInst
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.cfg.JcNewExpr
import org.jacodb.api.ext.cfg.callExpr

interface AliasAnalyer : Analyzer<AccessGraph, AliasEvent> {
    override val flowFunctions: AliasFlowFunctions
}

class ForwardAliasAnalyzer(
    private val graph: JcApplicationGraph,
    override val flowFunctions: AliasFlowFunctions
) : AliasAnalyer {

    override fun isSkipped(method: JcMethod): Boolean {
        return super.isSkipped(method)
    }

    private fun isExitPoint(statement: JcInst): Boolean {
        return statement in graph.exitPoints(statement.location.method)
    }

    private fun isAllocationSummaryEdge(edge: Edge<AccessGraph>): Boolean {
        val (stmt, fact) = edge.from
        return stmt is JcAssignInst && stmt.rhv is JcNewExpr && fact.isEmpty() && fact.matches(stmt.lhv)
    }

    private fun isTransitiveSummaryEdge(edge: Edge<AccessGraph>): Boolean {
        val stmt = edge.from.statement
        return stmt.callExpr != null
    }

    private fun isParameterSummaryEdge(edge: Edge<AccessGraph>): Boolean {
        val stmt = edge.from.statement
        return stmt is JcNoopInst
    }

    override fun handleNewEdge(edge: Edge<AccessGraph>): List<AliasEvent> = buildList {
        when {
            isExitPoint(edge.to.statement) -> add(SummaryEdge(edge, SummaryEdge.Type.FUNCTION_SUMMARY))
            isAllocationSummaryEdge(edge) -> add(SummaryEdge(edge, SummaryEdge.Type.ALLOCATION))
            isTransitiveSummaryEdge(edge) -> add(SummaryEdge(edge, SummaryEdge.Type.TRANSITIVE))
            isParameterSummaryEdge(edge) -> add(SummaryEdge(edge, SummaryEdge.Type.PARAMETER))
        }
    }

    override fun handleCrossUnitCall(
        caller: Vertex<AccessGraph>,
        callee: Vertex<AccessGraph>
    ): List<AliasEvent> = buildList {
        println("handleCrossUnitCall: $caller $callee")
    }
}