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

import org.jacodb.api.JcMethod
import org.jacodb.api.analysis.JcApplicationGraph

interface IfdsInstanceFactory {
    suspend fun <UnitType> createInstance(
        graph: JcApplicationGraph,
        summary: Summary,
        unitResolver: UnitResolver<UnitType>,
        unit: UnitType,
        startMethods: List<JcMethod>,
        startFacts: Map<JcMethod, Set<DomainFact>>
    )
}

class IfdsInstanceBuilder {
    private val startMethods: MutableList<JcMethod> = mutableListOf()
    private val startFacts: MutableMap<JcMethod, MutableSet<DomainFact>> = mutableMapOf()

    fun addStart(method: JcMethod) {
        startMethods.add(method)
    }

    fun addStartFact(method: JcMethod, fact: DomainFact) {
        startFacts.getOrPut(method) { mutableSetOf() }.add(fact)
    }

    suspend fun <UnitType> build(
        factory: IfdsInstanceFactory,
        graph: JcApplicationGraph,
        summary: Summary,
        unitResolver: UnitResolver<UnitType>,
        unit: UnitType
    ) {
        return factory.createInstance(graph, summary, unitResolver, unit, startMethods, startFacts)
    }
}

suspend fun <UnitType> buildIfdsInstance(
    factory: IfdsInstanceFactory,
    graph: JcApplicationGraph,
    summary: Summary,
    unitResolver: UnitResolver<UnitType>,
    unit: UnitType,
    actions: suspend IfdsInstanceBuilder.() -> Unit
) {
    val builder = IfdsInstanceBuilder()
    builder.actions()
    builder.build(factory, graph, summary, unitResolver, unit)
}