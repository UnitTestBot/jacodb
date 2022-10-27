package org.utbot.jcdb.impl

import org.utbot.jcdb.api.ByteCodeIndexer
import org.utbot.jcdb.api.ClassSource
import org.utbot.jcdb.api.JCDB
import org.utbot.jcdb.api.JcFeature
import org.utbot.jcdb.api.JcSignal
import org.utbot.jcdb.api.RegisteredLocation
import org.utbot.jcdb.impl.fs.fullAsmNode
import java.io.Closeable

class FeaturesRegistry(private val features: List<JcFeature<*, *>>) : Closeable {

    private lateinit var jcdb: JCDB

    fun bind(jcdb: JCDB) {
        this.jcdb = jcdb
    }

    fun index(location: RegisteredLocation, classes: List<ClassSource>) {
        features.forEach { feature ->
            feature.index(location, classes)
        }
    }

    private fun <REQ, RES> JcFeature<RES, REQ>.index(
        location: RegisteredLocation,
        classes: Collection<ClassSource>
    ) {
        val indexer = newIndexer(jcdb, location)
        classes.forEach { index(it, indexer) }
        jcdb.persistence.write {
            indexer.flush()
        }
    }

    fun broadcast(signal: JcInternalSignal) {
        features.forEach { it.onSignal(signal.asJcSignal(jcdb)) }
    }

    fun forEach(action: (JCDB, JcFeature<*, *>) -> Unit) {
        features.forEach { action(jcdb, it) }
    }

    override fun close() {
    }

    private fun index(source: ClassSource, builder: ByteCodeIndexer) {
        val asmNode = source.fullAsmNode
        builder.index(asmNode)
        asmNode.methods.forEach {
            builder.index(asmNode, it)
        }
    }
}

sealed class JcInternalSignal {

    class BeforeIndexing(val clearOnStart: Boolean) : JcInternalSignal()
    object AfterIndexing : JcInternalSignal()
    object Drop : JcInternalSignal()
    class LocationRemoved(val location: RegisteredLocation) : JcInternalSignal()

    fun asJcSignal(jcdb: JCDB): JcSignal {
        return when (this) {
            is BeforeIndexing -> JcSignal.BeforeIndexing(jcdb, clearOnStart)
            is AfterIndexing -> JcSignal.AfterIndexing(jcdb)
            is LocationRemoved -> JcSignal.LocationRemoved(jcdb, location)
            is Drop -> JcSignal.Drop(jcdb)
        }
    }

}
