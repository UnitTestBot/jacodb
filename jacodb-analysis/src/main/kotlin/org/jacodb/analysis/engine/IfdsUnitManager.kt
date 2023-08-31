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


interface IfdsUnitManager<UnitType> {
    suspend fun handleEvent(event: IfdsUnitRunnerEvent, runner: IfdsUnitRunner<UnitType>)
}


// TODO: provide visitor for this interface
sealed interface IfdsUnitRunnerEvent

data class QueueEmptinessChanged(val isEmpty: Boolean) : IfdsUnitRunnerEvent
data class SubscriptionForSummaries(val method: JcMethod, val collector: FlowCollector<IfdsEdge>) : IfdsUnitRunnerEvent

sealed interface AnalysisDependentEvent : IfdsUnitRunnerEvent

data class NewSummaryFact(val fact: SummaryFact) : AnalysisDependentEvent
data class EdgeForOtherRunnerQuery(val edge: IfdsEdge) : AnalysisDependentEvent