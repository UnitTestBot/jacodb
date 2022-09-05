package org.utbot.jcdb.impl

import org.utbot.jcdb.api.ByteCodeLocation
import org.utbot.jcdb.api.Feature
import org.utbot.jcdb.api.GlobalIdsStore
import org.utbot.jcdb.api.Index
import org.utbot.jcdb.impl.index.index
import org.utbot.jcdb.impl.storage.PersistentEnvironment
import org.utbot.jcdb.impl.tree.ClassNode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap

class FeaturesRegistry(
    private val persistence: PersistentEnvironment? = null,
    private val globalIdsStore: GlobalIdsStore,
    val features: List<Feature<*, *>>
) : Closeable {

    private val indexes: ConcurrentHashMap<String, Index<*>> = ConcurrentHashMap()

    fun <T, INDEX : Index<T>> append(feature: Feature<T, INDEX>, index: INDEX) {
        indexes[feature.key] = index
    }

    suspend fun index(location: ByteCodeLocation, classes: Collection<ClassNode>) {
        features.forEach { feature ->
            feature.index(location, classes)
        }
        classes.forEach { node ->
            node.onAfterIndexing()
        }
    }

    private suspend fun <T, INDEX : Index<T>> Feature<T, INDEX>.index(
        location: ByteCodeLocation,
        classes: Collection<ClassNode>
    ) {
        val builder = newBuilder(globalIdsStore)
        classes.forEach { node ->
            index(node, builder)
        }
        val index = builder.build(location)
        indexes[key] = index

        val entity = sql {
            persistence?.locationStore?.findOrNewTx(location)
        }
        if (entity != null) {
            val out = ByteArrayOutputStream()
            serialize(index, out)
            sql {
                entity.index(key, ByteArrayInputStream(out.toByteArray()))
            }
        }
    }

    fun <T> findIndex(key: String): Index<T>? {
        return indexes[key] as? Index<T>?
    }

    override fun close() {
        indexes.clear()
    }
}
