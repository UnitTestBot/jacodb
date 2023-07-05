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

package org.jacodb.analysis.engine

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.filterIsInstance
import org.jacodb.api.JcMethod
import java.util.concurrent.ConcurrentHashMap

sealed interface SummaryFact

data class VulnerabilityLocation(val vulnerabilityType: String, val sink: IfdsVertex) : SummaryFact

data class SummaryEdgeFact(val edge: IfdsEdge) : SummaryFact

data class CalleeFact(val vertex: IfdsVertex, val factsAtCalleeStart: Set<IfdsVertex>) : SummaryFact

data class TraceGraphFact(val graph: TraceGraph) : SummaryFact

fun interface SummarySender {
    fun send(fact: SummaryFact)
}

interface Summary {
    fun createSender(method: JcMethod): SummarySender

    fun getFacts(method: JcMethod, startVertex: IfdsVertex?): Flow<SummaryFact>

    fun getCurrentFacts(method: JcMethod, startVertex: IfdsVertex?): List<SummaryFact>

    val knownMethods: List<JcMethod>
}

class SummaryImpl: Summary {
    private val loadedFacts: MutableMap<JcMethod, MutableSet<SummaryFact>> = ConcurrentHashMap()
    private val outFlows: MutableMap<JcMethod, MutableSharedFlow<SummaryFact>> = ConcurrentHashMap()

    override fun createSender(method: JcMethod): SummarySender {
        return SummarySender { fact ->
            loadedFacts.getOrPut(method) { ConcurrentHashMap.newKeySet() }.add(fact)
            val outFlow = outFlows.computeIfAbsent(method) { MutableSharedFlow(replay = Int.MAX_VALUE) }
            require(outFlow.tryEmit(fact))
        }
    }

    override fun getFacts(method: JcMethod, startVertex: IfdsVertex?): SharedFlow<SummaryFact> {
        return outFlows.getOrPut(method) {
            MutableSharedFlow<SummaryFact>(replay = Int.MAX_VALUE).also { flow ->
                loadedFacts[method].orEmpty().forEach { fact ->
                    require(flow.tryEmit(fact))
                }
            }
        }
    }

    override fun getCurrentFacts(method: JcMethod, startVertex: IfdsVertex?): List<SummaryFact> {
        return getFacts(method, startVertex).replayCache
    }

    override val knownMethods: List<JcMethod>
        get() = loadedFacts.keys.toList()
}

inline fun <reified T : SummaryFact> Summary.getFactsFiltered(method: JcMethod, startVertex: IfdsVertex?): Flow<T> {
    return getFacts(method, startVertex).filterIsInstance<T>()
}

inline fun <reified T : SummaryFact> Summary.getCurrentFactsFiltered(method: JcMethod, startVertex: IfdsVertex?): List<T> {
    return getCurrentFacts(method, startVertex).filterIsInstance<T>()
}