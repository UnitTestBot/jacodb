package org.utbot.jcdb.impl

import org.utbot.jcdb.api.Feature
import org.utbot.jcdb.api.JCDB
import org.utbot.jcdb.api.RegisteredLocation
import org.utbot.jcdb.impl.index.index
import org.utbot.jcdb.impl.vfs.ClassVfsItem
import java.io.Closeable

class FeaturesRegistry(private val features: List<Feature<*, *>>) : Closeable {

    private lateinit var jcdb: JCDB

    fun bind(jcdb: JCDB) {
        this.jcdb = jcdb
    }

    suspend fun index(location: RegisteredLocation, classes: Collection<ClassVfsItem>) {
        features.forEach { feature ->
            feature.index(location, classes)
        }
    }

    private suspend fun <REQ, RES> Feature<RES, REQ>.index(
        location: RegisteredLocation,
        classes: Collection<ClassVfsItem>
    ) {
        val indexer = newIndexer(jcdb, location)
        classes.forEach { index(it, indexer) }
        jcdb.persistence.write {
            indexer.flush()
        }
    }

    fun onLocationRemove(location: RegisteredLocation) {
        features.forEach {
            it.onRemoved(jcdb, location)
        }
    }

    fun forEach(action: (JCDB, Feature<*, *>) -> Unit) {
        features.forEach { action(jcdb, it) }
    }

    override fun close() {
    }

}
