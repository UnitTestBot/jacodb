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

import org.jacodb.analysis.graph.reversed
import org.jacodb.api.JcMethod
import org.jacodb.api.analysis.JcApplicationGraph

class IfdsWithBackwardPreSearchFactory(
    private val forward: IfdsInstanceFactory,
    private val backward: IfdsInstanceFactory,
) : IfdsInstanceFactory() {

    override fun <UnitType> createInstance(
        graph: JcApplicationGraph,
        context: AnalysisContext,
        unitResolver: UnitResolver<UnitType>,
        unit: UnitType,
        startMethods: List<JcMethod>,
        startFacts: Map<JcMethod, Set<DomainFact>>
    ): Map<JcMethod, IfdsMethodSummary> {
        val backwardResults = backward.createInstance(graph.reversed, context, unitResolver, unit, startMethods, emptyMap())

        return buildIfdsInstance(forward, graph, context, unitResolver, unit) {
            startMethods.forEach {
                addStart(it)
            }

            backwardResults.forEach { (method, summary) ->
                summary.factsAtExits.values.flatten().forEach {
                    addStartFact(method, it.domainFact)
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