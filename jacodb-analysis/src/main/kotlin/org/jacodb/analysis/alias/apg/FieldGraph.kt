package org.jacodb.analysis.alias.apg

import org.jacodb.api.JcField

data class FieldGraph(
    val head: JcField,
    val tail: JcField,
    val graph: PersistentDirectedGraph<JcField>
) {
    override fun toString(): String =
        if (head == tail && graph.order == 1) ".${head.name}" else ".${head.name}.(...).${tail.name}"
}

fun fieldGraphFrom(field: JcField) =
    FieldGraph(field, field, emptyDirectedPersistentGraph<JcField>().addVertex(field))

fun FieldGraph.matches(field: JcField): Boolean =
    head == field

fun FieldGraph.removeHead(): Set<FieldGraph> =
    if (graph.checkLiesOnLoop(head)) {
        graph.outcoming(head).mapTo(hashSetOf()) { headSucc ->
            FieldGraph(headSucc, tail, graph)
        }
    } else {
        graph.outcoming(head).mapTo(hashSetOf()) { headSucc ->
            FieldGraph(headSucc, tail, graph.removeEdge(head, headSucc))
        }
    }


fun FieldGraph.removeTail(): Set<FieldGraph> =
    if (graph.checkLiesOnLoop(tail)) {
        graph.incoming(head).mapTo(hashSetOf()) { tailPred ->
            FieldGraph(head, tailPred, graph)
        }
    } else {
        graph.incoming(head).mapTo(hashSetOf()) { tailPred ->
            FieldGraph(head, tailPred, graph.removeEdge(tailPred, tail))
        }
    }

fun FieldGraph.prependHead(field: JcField): FieldGraph {
    val newGraph = graph.addEdge(field, head)
    return FieldGraph(
        field,
        tail,
        newGraph
    )
}

fun FieldGraph.appendTail(field: JcField): FieldGraph {
    val newGraph = graph.addEdge(tail, field)
    return FieldGraph(
        head,
        field,
        newGraph
    )
}

fun FieldGraph.concatenate(other: FieldGraph): FieldGraph {
    val newGraph = graph.addEdges(other.graph.edges)
    return FieldGraph(
        head,
        other.tail,
        newGraph
    )
}