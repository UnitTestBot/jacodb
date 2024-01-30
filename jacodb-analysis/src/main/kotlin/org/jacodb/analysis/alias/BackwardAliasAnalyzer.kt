package org.jacodb.analysis.alias

import org.jacodb.analysis.alias.apg.AccessGraph
import org.jacodb.analysis.alias.flow.AliasFlowFunctions
import org.jacodb.analysis.ifds2.Edge
import org.jacodb.analysis.ifds2.Vertex
import org.jacodb.api.JcMethod
import org.jacodb.api.analysis.JcApplicationGraph
import org.jacodb.api.cfg.JcInst

class BackwardAliasAnalyzer(
    private val graph: JcApplicationGraph,
    override val flowFunctions: AliasFlowFunctions
) : AliasAnalyer {

    override fun isSkipped(method: JcMethod): Boolean {
        return super.isSkipped(method)
    }

    private fun isExitPoint(statement: JcInst): Boolean {
        return statement in graph.exitPoints(statement.location.method)
    }

    override fun handleNewEdge(edge: Edge<AccessGraph>): List<AliasEvent> = buildList {
//        if (isExitPoint(edge.to.statement)) {
//            add(SummaryEdge(edge))
//        }
    }

    override fun handleCrossUnitCall(
        caller: Vertex<AccessGraph>,
        callee: Vertex<AccessGraph>
    ): List<AliasEvent> = buildList {
        println("handleCrossUnitCall: $caller $callee")
    }
}