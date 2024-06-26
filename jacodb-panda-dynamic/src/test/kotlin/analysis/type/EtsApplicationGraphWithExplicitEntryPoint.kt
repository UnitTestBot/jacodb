package analysis.type

import org.jacodb.api.common.CommonProject
import org.jacodb.api.common.analysis.ApplicationGraph
import org.jacodb.panda.dynamic.ets.base.EtsInstLocation
import org.jacodb.panda.dynamic.ets.base.EtsNopStmt
import org.jacodb.panda.dynamic.ets.base.EtsStmt
import org.jacodb.panda.dynamic.ets.graph.EtsApplicationGraph
import org.jacodb.panda.dynamic.ets.model.EtsMethod
import org.jacodb.panda.dynamic.ets.utils.callExpr

class EtsApplicationGraphWithExplicitEntryPoint(
    private val graph: EtsApplicationGraph,
): ApplicationGraph<EtsMethod, EtsStmt> {
    override val project: CommonProject
        get() = graph.project

    override fun methodOf(node: EtsStmt): EtsMethod = node.location.method

    override fun exitPoints(method: EtsMethod): Sequence<EtsStmt> = graph.exitPoints(method)

    private fun methodEntryPoint(method: EtsMethod) =
        EtsNopStmt(EtsInstLocation(method, index = -1))

    override fun entryPoints(method: EtsMethod): Sequence<EtsStmt> = sequenceOf(methodEntryPoint(method))

    override fun callers(method: EtsMethod): Sequence<EtsStmt> = graph.callers(method)

    override fun callees(node: EtsStmt): Sequence<EtsMethod> {
        val callees = graph.callees(node).toList()

        val callExpr = node.callExpr
        if (callees.isEmpty() && callExpr != null) {
            System.err.println("No methods found for: $node")
        }

        return callees.asSequence()
    }

    override fun successors(node: EtsStmt): Sequence<EtsStmt> {
        val method = methodOf(node)
        val methodEntry = methodEntryPoint(method)

        if (node == methodEntry) {
            return graph.entryPoints(method)
        }

        return graph.successors(node)
    }

    override fun predecessors(node: EtsStmt): Sequence<EtsStmt> {
        val method = methodOf(node)
        val methodEntry = methodEntryPoint(method)

        if (node == methodEntry) {
            return emptySequence()
        }

        if (node in graph.entryPoints(method)) {
            return sequenceOf(methodEntry)
        }

        return graph.predecessors(node)
    }
}
