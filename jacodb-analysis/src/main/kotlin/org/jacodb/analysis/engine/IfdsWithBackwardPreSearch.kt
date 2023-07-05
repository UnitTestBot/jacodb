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

import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jacodb.analysis.graph.reversed
import org.jacodb.api.JcMethod
import org.jacodb.api.analysis.JcApplicationGraph

class IfdsWithBackwardPreSearchFactory(
    private val forward: IfdsInstanceFactory,
    private val backward: IfdsInstanceFactory,
) : IfdsInstanceFactory {

    override suspend fun <UnitType> createInstance(
        graph: JcApplicationGraph,
        summary: Summary,
        unitResolver: UnitResolver<UnitType>,
        unit: UnitType,
        startMethods: List<JcMethod>,
        startFacts: Map<JcMethod, Set<DomainFact>>
    ) = coroutineScope {
        val backwardSummary = SummaryImpl()

         val job = launch {
            backward.createInstance(
                graph.reversed,
                backwardSummary,
                unitResolver,
                unit,
                startMethods,
                emptyMap()
            )
        }

        delay(20)
        job.cancelAndJoin()

        buildIfdsInstance(forward, graph, summary, unitResolver, unit) {
            startMethods.forEach {
                addStart(it)
            }

            backwardSummary.knownMethods.forEach { method ->
                backwardSummary.getCurrentFactsFiltered<SummaryEdgeFact>(method, null).forEach { summary ->
                    addStartFact(method, summary.edge.v.domainFact)
                }
            }

            startFacts.forEach { (method, facts) ->
                facts.forEach { fact ->
                    addStartFact(method, fact)
                }
            }
        }
    }
}