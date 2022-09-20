package org.utbot.jcdb.impl

import org.utbot.jcdb.api.Feature
import org.utbot.jcdb.api.JCDB
import org.utbot.jcdb.api.JCDBFeature
import org.utbot.jcdb.api.RegisteredLocation
import org.utbot.jcdb.impl.index.index
import org.utbot.jcdb.impl.vfs.ClassVfsItem
import java.io.Closeable

class FeaturesRegistry(private val features: List<Feature<*, *>>) : Closeable {

    lateinit var jcdbFeatures: List<JCDBFeature<*, *>>

    fun bind(jcdb: JCDB) {
        jcdbFeatures = features.map { it.featureOf(jcdb) }
    }

    suspend fun index(location: RegisteredLocation, classes: Collection<ClassVfsItem>) {
        jcdbFeatures.forEach { feature ->
            feature.index(location, classes)
        }
    }

    private suspend fun <REQ, RES> JCDBFeature<RES, REQ>.index(
        location: RegisteredLocation,
        classes: Collection<ClassVfsItem>
    ) {
        val indexer = newIndexer(location)
        classes.forEach { node ->
            index(node, indexer)
        }
        persistence?.jcdbPersistence?.write {
            indexer.flush()
        }
    }

    fun <REQ, RES> findIndex(key: String): JCDBFeature<RES, REQ>? {
        return jcdbFeatures.firstOrNull { it.key == key } as? JCDBFeature<RES, REQ>?
    }

    fun onLocationRemove(location: RegisteredLocation) {
        jcdbFeatures.forEach {
            it.onLocationRemoved(location)
        }
    }

    override fun close() {
    }
}
