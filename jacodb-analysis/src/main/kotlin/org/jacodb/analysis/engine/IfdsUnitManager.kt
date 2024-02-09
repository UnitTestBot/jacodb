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

import kotlinx.coroutines.flow.FlowCollector
import org.jacodb.api.JcMethod

/**
 * Implementations of this interface manage one or more runners and should be responsible for:
 * - communication between different runners, i.e. they
 * should submit received [EdgeForOtherRunnerQuery] to proper runners via [IfdsUnitRunner.submitNewEdge] call
 * - providing runners with summaries for other units
 * - saving the [NewSummaryFact]s produced by runners
 * - managing lifecycles of the launched runners
 */
interface IfdsUnitManager<UnitType> {
    suspend fun handleEvent(event: IfdsUnitRunnerEvent, runner: IfdsUnitRunner<UnitType>)
}

// TODO: provide visitor for this interface
sealed interface IfdsUnitRunnerEvent

data class QueueEmptinessChanged(val isEmpty: Boolean) : IfdsUnitRunnerEvent

/**
 * @property method the method for which summary edges the subscription is queried
 * @property collector the [FlowCollector] to which queried summary edges should be sent to,
 * somewhat similar to a callback
 */
data class SubscriptionForSummaryEdges(val method: JcMethod, val collector: FlowCollector<IfdsEdge>) :
    IfdsUnitRunnerEvent

/**
 * A common interface for all events that are allowed to be produced by [Analyzer]
 * (all others may be produced only in runners directly)
 */
sealed interface AnalysisDependentEvent : IfdsUnitRunnerEvent

data class NewSummaryFact(val fact: SummaryFact) : AnalysisDependentEvent
data class EdgeForOtherRunnerQuery(val edge: IfdsEdge) : AnalysisDependentEvent
