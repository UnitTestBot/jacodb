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

package org.jacodb.analysis.ifds2

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.FlowCollector
import org.jacodb.api.JcMethod

sealed interface Event

data class SubscriptionForSummaryEdges2(
    val method: JcMethod,
    val collector: FlowCollector<Edge>,
) : Event

data class SubscriptionForSummaryEdges3(
    val method: JcMethod,
    val scope: CoroutineScope,
    val handler: suspend (Edge) -> Unit,
) : Event

data class NewSummaryEdge(
    val edge: Edge,
) : Event

// TODO: replace with 'BeginAnalysis(val statement: Vertex)', where 'statement' is
//       the first instruction of the analyzed method together with a fact.
data class EdgeForOtherRunner(
    val edge: Edge,
) : Event {
    init {
        check(edge.from == edge.to) { "Edge for another runner must be a loop" }
    }
}

data class NewVulnerability(
    val vulnerability: Vulnerability,
) : Event

data class QueueEmptinessChanged(
    val runner: Runner,
    val isEmpty: Boolean,
) : Event
