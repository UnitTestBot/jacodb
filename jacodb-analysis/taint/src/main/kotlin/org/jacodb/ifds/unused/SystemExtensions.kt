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

package org.jacodb.ifds.unused

import org.jacodb.actors.api.ActorSystem
import org.jacodb.analysis.graph.JcApplicationGraphImpl
import org.jacodb.analysis.unused.UnusedVariable
import org.jacodb.analysis.unused.UnusedVariableAnalyzer
import org.jacodb.analysis.unused.UnusedVariableDomainFact
import org.jacodb.analysis.unused.UnusedVariableVulnerability
import org.jacodb.analysis.unused.UnusedVariableZeroFact
import org.jacodb.analysis.unused.isUsedAt
import org.jacodb.api.JcMethod
import org.jacodb.api.cfg.JcInst
import org.jacodb.ifds.domain.Edge
import org.jacodb.ifds.domain.Reason
import org.jacodb.ifds.domain.Vertex
import org.jacodb.ifds.messages.CollectAll
import org.jacodb.ifds.messages.CommonMessage
import org.jacodb.ifds.messages.NewEdge
import org.jacodb.ifds.result.IfdsComputationData
import org.jacodb.ifds.result.mergeIfdsResults
import org.jacodb.impl.features.usagesExt

suspend fun ActorSystem<CommonMessage>.startUnusedAnalysis(method: JcMethod) {
    val cp = method.enclosingClass.classpath
    val graph = JcApplicationGraphImpl(cp, cp.usagesExt())
    val unusedAnalyzer = UnusedVariableAnalyzer(graph)

    for (fact in unusedAnalyzer.flowFunctions.obtainPossibleStartFacts(method)) {
        for (entryPoint in graph.entryPoints(method)) {
            val vertex = Vertex(entryPoint, fact)
            val message = NewEdge(SingletonRunnerId, Edge(vertex, vertex), Reason.Initial)
            send(message)
        }
    }
}

suspend fun ActorSystem<CommonMessage>.collectUnusedResult(): List<UnusedVariableVulnerability> {
    val data = collectUnusedComputationData()

    val allFacts = data.facts

    val used = hashMapOf<JcInst, Boolean>()
    for ((inst, facts) in allFacts) {
        for (fact in facts) {
            if (fact is UnusedVariable) {
                used.putIfAbsent(fact.initStatement, false)
                if (fact.variable.isUsedAt(inst)) {
                    used[fact.initStatement] = true
                }
            }

        }
    }
    val vulnerabilities = used.filterValues { !it }.keys.map {
        UnusedVariableVulnerability(
            message = "Assigned value is unused",
            sink = Vertex(it, UnusedVariableZeroFact)
        )
    }
    return vulnerabilities
}

suspend fun ActorSystem<CommonMessage>.collectUnusedComputationData(): IfdsComputationData<JcInst, UnusedVariableDomainFact, UnusedVulnerability> {
    val results = ask {
        CollectAll(
            SingletonRunnerId,
            it
        )
    }

    @Suppress("UNCHECKED_CAST")
    val mergedData =
        mergeIfdsResults(results.values as Collection<IfdsComputationData<JcInst, UnusedVariableDomainFact, UnusedVulnerability>>)
    return mergedData
}
