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

    fun has(feature: JcFeature<*,*>): Boolean {
        return features.contains(feature)
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

    fun forEach(action: (JCDB, JcFeature<*, *>) -> Unit) {
        features.forEach { action(jcdb, it) }
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
