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

package org.jacodb.analysis.ifds

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import org.jacodb.api.JcMethod
import java.util.concurrent.ConcurrentHashMap

/**
 * A common interface for anything that should be remembered
 * and used after the analysis of some unit is completed.
 */
interface Summary {
    val method: JcMethod
}

interface SummaryEdge<out Fact> : Summary {
    val edge: Edge<Fact>

    override val method: JcMethod
        get() = edge.method
}

interface Vulnerability<out Fact> : Summary {
    val message: String
    val sink: Vertex<Fact>

    override val method: JcMethod
        get() = sink.method
}

/**
 * Contains summaries for many methods and allows to update them and subscribe for them.
 */
class SummaryStorageWithFlows<T : Summary> {
    private val summaries = ConcurrentHashMap<JcMethod, MutableSet<T>>()
    private val outFlows = ConcurrentHashMap<JcMethod, MutableSharedFlow<T>>()

    /**
     * @return a list with all methods for which there are some summaries.
     */
     val knownMethods: List<JcMethod>
        get() = summaries.keys.toList()

    private fun getFlow(method: JcMethod): MutableSharedFlow<T> {
        return outFlows.computeIfAbsent(method) {
            MutableSharedFlow(replay = Int.MAX_VALUE)
        }
    }

    /**
     * Adds a new [fact] to the storage.
     */
     fun add(fact: T) {
        val isNew = summaries.computeIfAbsent(fact.method) { ConcurrentHashMap.newKeySet() }.add(fact)
        if (isNew) {
            val flow = getFlow(fact.method)
            check(flow.tryEmit(fact))
        }
    }

    /**
     * @return a flow with all facts summarized for the given [method].
     * Already received facts, along with the facts that will be sent to this storage later,
     * will be emitted to the returned flow.
     */
     fun getFacts(method: JcMethod): SharedFlow<T> {
        return getFlow(method)
    }

    /**
     * @return a list will all facts summarized for the given [method] so far.
     */
     fun getCurrentFacts(method: JcMethod): List<T> {
        return getFacts(method).replayCache
    }
}

class SummaryStorageWithProducers<T : Summary>(
    private val useConcurrentProducer: Boolean = false,
) {
    private val summaries = ConcurrentHashMap<JcMethod, MutableSet<T>>()
    private val producers = ConcurrentHashMap<JcMethod, Producer<T>>()

    private fun getProducer(method: JcMethod): Producer<T> {
        return producers.computeIfAbsent(method) {
            if (useConcurrentProducer) {
                ConcurrentProducer()
            } else {
                SyncProducer()
            }
        }
    }

    fun add(fact: T) {
        val isNew = summaries.computeIfAbsent(fact.method) { ConcurrentHashMap.newKeySet() }.add(fact)
        if (isNew) {
            val producer = getProducer(fact.method)
            producer.produce(fact)
        }
    }

    fun subscribe(method: JcMethod, handler: (T) -> Unit) {
        val producer = getProducer(method)
        producer.subscribe(handler)
    }
}
