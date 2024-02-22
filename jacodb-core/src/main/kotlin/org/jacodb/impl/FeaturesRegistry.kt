/*
 *  Copyright 2022 UnitTestBot contributors (utbot.org)
 * <p>
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * <p>
 *  http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.jacodb.impl

import kotlinx.collections.immutable.toPersistentList
import org.jacodb.api.jvm.*
import org.jacodb.impl.fs.fullAsmNode
import java.io.Closeable

class FeaturesRegistry(features: List<JcFeature<*, *>>) : Closeable {

    val features = features.toPersistentList()

    private lateinit var jcdb: JcDatabase

    fun bind(jcdb: JcDatabase) {
        this.jcdb = jcdb
    }

    fun index(location: RegisteredLocation, classes: List<ClassSource>) {
        features.forEach { feature ->
            feature.index(location, classes)
        }
    }

    private fun <REQ, RES> JcFeature<RES, REQ>.index(
        location: RegisteredLocation,
        classes: List<ClassSource>
    ) {
        val indexer = newIndexer(jcdb, location)
        classes.forEach { index(it, indexer) }
        jcdb.persistence.write {
            indexer.flush(it)
        }
    }

    fun broadcast(signal: JcInternalSignal) {
        features.forEach { it.onSignal(signal.asJcSignal(jcdb)) }
    }

    override fun close() {
    }

    private fun index(source: ClassSource, builder: ByteCodeIndexer) {
        builder.index(source.fullAsmNode)
    }
}

sealed class JcInternalSignal {

    class BeforeIndexing(val clearOnStart: Boolean) : JcInternalSignal()
    object AfterIndexing : JcInternalSignal()
    object Drop : JcInternalSignal()
    object Closed : JcInternalSignal()
    class LocationRemoved(val location: RegisteredLocation) : JcInternalSignal()

    fun asJcSignal(jcdb: JcDatabase): JcSignal {
        return when (this) {
            is BeforeIndexing -> JcSignal.BeforeIndexing(jcdb, clearOnStart)
            is AfterIndexing -> JcSignal.AfterIndexing(jcdb)
            is LocationRemoved -> JcSignal.LocationRemoved(jcdb, location)
            is Drop -> JcSignal.Drop(jcdb)
            is Closed -> JcSignal.Closed(jcdb)
        }
    }

}
