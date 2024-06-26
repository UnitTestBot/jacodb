package analysis.type

import org.jacodb.analysis.ifds.Analyzer
import org.jacodb.analysis.ifds.Edge
import org.jacodb.analysis.ifds.Vertex
import org.jacodb.api.common.analysis.ApplicationGraph
import org.jacodb.panda.dynamic.ets.base.EtsStmt
import org.jacodb.panda.dynamic.ets.model.EtsMethod

class ForwardAnalyzer(
    val graph: ApplicationGraph<EtsMethod, EtsStmt>,
    methodInitialTypes: Map<EtsMethod, EtsMethodTypeFacts>
) : Analyzer<ForwardTypeDomainFact, AnalyzerEvent, EtsMethod, EtsStmt> {
    override val flowFunctions = ForwardFlowFunction(graph, methodInitialTypes)

    override fun handleCrossUnitCall(
        caller: Vertex<ForwardTypeDomainFact, EtsStmt>,
        callee: Vertex<ForwardTypeDomainFact, EtsStmt>
    ): List<AnalyzerEvent> {
        error("No cross unit calls")
    }

    override fun handleNewEdge(edge: Edge<ForwardTypeDomainFact, EtsStmt>): List<AnalyzerEvent> {
        val (startVertex, currentVertex) = edge
        val (current, currentFact) = currentVertex

        val method = graph.methodOf(current)
        val currentIsExit = current in graph.exitPoints(method)

        if (!currentIsExit) return emptyList()

        return listOf(
            ForwardSummaryAnalyzerEvent(
                method, startVertex.fact, currentFact
            )
        )
    }
}