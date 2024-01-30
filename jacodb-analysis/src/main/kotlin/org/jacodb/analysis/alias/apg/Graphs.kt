package org.jacodb.analysis.alias.apg

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentHashMapOf
import kotlinx.collections.immutable.persistentHashSetOf
import kotlinx.collections.immutable.persistentMapOf

interface DirectredGraph<Vertex> {
    val order: Int
    val size: Int

    val vertices: Collection<Vertex>
    val edges: Collection<Pair<Vertex, Vertex>>

    fun outcoming(vertex: Vertex): Collection<Vertex>
    fun incoming(vertex: Vertex): Collection<Vertex>

    fun asReversed(): DirectredGraph<Vertex>
}

interface PersistentDirectedGraph<Vertex> : DirectredGraph<Vertex> {
    fun addVertex(vertex: Vertex): PersistentDirectedGraph<Vertex>
    fun addEdge(from: Vertex, to: Vertex, addVertices: Boolean = true): PersistentDirectedGraph<Vertex>
    fun addEdges(collection: Collection<Pair<Vertex, Vertex>>): PersistentDirectedGraph<Vertex>

    fun removeEdge(from: Vertex, to: Vertex): PersistentDirectedGraph<Vertex>
    fun removeVertex(vertex: Vertex): PersistentDirectedGraph<Vertex>
}

private typealias AdjacentList<Vertex> = PersistentMap<Vertex, PersistentSet<Vertex>>

class PersistentDirectedGraphImpl<Vertex>(
    private val outcoming: AdjacentList<Vertex>,
    private val incoming: AdjacentList<Vertex>
) : PersistentDirectedGraph<Vertex> {

    override val order: Int
        get() = vertices.size
    override val size: Int
        get() = outcoming.entries.sumOf { it.value.size }

    override val vertices: Collection<Vertex>
        get() = outcoming.keys + incoming.keys
    override val edges: Collection<Pair<Vertex, Vertex>>
        get() = outcoming.flatMap { (vertex, adjacentVertices) -> adjacentVertices.map { vertex to it } }

    override fun asReversed(): DirectredGraph<Vertex> =
        PersistentDirectedGraphImpl(incoming, outcoming)

    override fun outcoming(vertex: Vertex): Collection<Vertex> =
        outcoming[vertex].orEmpty()

    override fun incoming(vertex: Vertex): Collection<Vertex> =
        incoming[vertex].orEmpty()

    override fun addVertex(vertex: Vertex): PersistentDirectedGraph<Vertex> {
        val newOutcoming = outcoming.put(vertex, persistentHashSetOf())
        val newIncoming = incoming.put(vertex, persistentHashSetOf())
        if (newOutcoming == outcoming && newIncoming == incoming) {
            return this
        }
        return PersistentDirectedGraphImpl(newOutcoming, newIncoming)
    }

    override fun addEdge(from: Vertex, to: Vertex, addVertices: Boolean): PersistentDirectedGraph<Vertex> {
        val newOutcoming = outcoming.addEdge(from, to)
        val newIncoming = incoming.addEdge(to, from)
        if (newOutcoming == outcoming && newIncoming == incoming) {
            return this
        }
        return PersistentDirectedGraphImpl(newOutcoming, newIncoming)
    }

    override fun addEdges(collection: Collection<Pair<Vertex, Vertex>>): PersistentDirectedGraph<Vertex> {
        val newOutcoming = outcoming.addEdges(collection)
        val newIncoming = incoming.addEdges(collection.map { it.second to it.first })
        if (newOutcoming == outcoming && newIncoming == incoming) {
            return this
        }
        return PersistentDirectedGraphImpl(newOutcoming, newIncoming)
    }

    override fun removeEdge(from: Vertex, to: Vertex): PersistentDirectedGraph<Vertex> {
        val newOutcoming = outcoming.removeEdge(from, to)
        val newIncoming = incoming.removeEdge(to, from)
        if (newOutcoming == outcoming && newIncoming == incoming) {
            return this
        }
        return PersistentDirectedGraphImpl(newOutcoming, newIncoming)
    }

    override fun removeVertex(vertex: Vertex): PersistentDirectedGraph<Vertex> {
        val newOutcoming = outcoming.removeVertex(vertex)
        val newIncoming = incoming.removeVertex(vertex)
        if (newOutcoming == outcoming && newIncoming == incoming) {
            return this
        }
        return PersistentDirectedGraphImpl(newOutcoming, newIncoming)
    }


    private fun AdjacentList<Vertex>.addEdge(from: Vertex, to: Vertex): AdjacentList<Vertex> {
        val newAdjacencyList = this[from]?.add(to) ?: persistentHashSetOf(to)
        return put(from, newAdjacencyList)
    }

    private fun AdjacentList<Vertex>.addEdges(collection: Collection<Pair<Vertex, Vertex>>): AdjacentList<Vertex> {
        val builder = builder()
        val vertexToOutcomings = collection.groupBy({ it.first }) { it.second }
        for ((vertex, outcomings) in vertexToOutcomings) {
            val innerBuilder = this[vertex]?.builder() ?: persistentHashSetOf<Vertex>().builder()
            innerBuilder.addAll(outcomings)
            builder[vertex] = innerBuilder.build()
        }

        return builder.build()
    }

    private fun AdjacentList<Vertex>.removeEdge(from: Vertex, to: Vertex): AdjacentList<Vertex> {
        val newAdjacencyList = this[from]?.remove(to) ?: return this
        return put(from, newAdjacencyList)
    }

    private fun AdjacentList<Vertex>.removeVertex(vertex: Vertex): AdjacentList<Vertex> {
        if (vertex !in this) {
            return this
        }
        return this.remove(vertex)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PersistentDirectedGraphImpl<*>

        if (outcoming != other.outcoming) return false
        if (incoming != other.incoming) return false

        return true
    }

    override fun hashCode(): Int {
        var result = outcoming.hashCode()
        result = 31 * result + incoming.hashCode()
        return result
    }

}

fun <Vertex> emptyDirectedPersistentGraph() =
    PersistentDirectedGraphImpl<Vertex>(persistentHashMapOf(), persistentHashMapOf())


fun <Vertex> DirectredGraph<Vertex>.checkLiesOnLoop(vertex: Vertex): Boolean {
    val exited = hashMapOf<Vertex, Boolean>()
    fun dfs(u: Vertex): Boolean {
        exited[u] = false
        for (v in outcoming(u)) {
            val exV = exited[v]
            if (exV == true) {
                continue
            }
            if (exV == false || dfs(v)) {
                return true
            }
        }
        return false
    }
    return dfs(vertex)
}