package org.utbot.jcdb.impl

import org.jetbrains.exposed.sql.transactions.transaction
import org.utbot.jcdb.api.ByteCodeLocation
import org.utbot.jcdb.api.Feature
import org.utbot.jcdb.api.Index
import org.utbot.jcdb.api.IndexRequest
import org.utbot.jcdb.impl.index.index
import org.utbot.jcdb.impl.storage.PersistentEnvironment
import org.utbot.jcdb.impl.tree.ClassNode
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap

class FeaturesRegistry(
    private val persistence: PersistentEnvironment? = null,
    val features: List<Feature<*, *>>
) : Closeable {

    private val indexes = ConcurrentHashMap<String, Index<*, *>>()

    fun <T, INDEX : Index<T, *>> append(feature: Feature<T, INDEX>, index: INDEX) {
        indexes[feature.key] = index
    }

    suspend fun index(location: ByteCodeLocation, classes: Collection<ClassNode>) {
        features.forEach { feature ->
            feature.index(location, classes)
        }
    }

    private suspend fun <T, INDEX : Index<T, *>> Feature<T, INDEX>.index(
        location: ByteCodeLocation,
        classes: Collection<ClassNode>
    ) {
        val builder = newBuilder(location)
        classes.forEach { node ->
            index(node, builder)
        }
        val index = builder.build()
        indexes[key] = index

        val store = persistence?.locationStore
        if (store != null) {
            persistentOperation {
                transaction {
                    persist(index)
                }
            }
        }
    }

    fun <T, REQ : IndexRequest> findIndex(key: String): Index<T, REQ>? {
        return indexes[key] as? Index<T, REQ>?
    }

    fun onLocationRemove(location: ByteCodeLocation) {

    }

    override fun close() {
        indexes.clear()
    }
}
