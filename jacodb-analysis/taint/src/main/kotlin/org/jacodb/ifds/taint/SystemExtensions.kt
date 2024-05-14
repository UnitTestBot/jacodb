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

package org.jacodb.ifds.taint

import org.jacodb.actors.api.ActorSystem
import org.jacodb.analysis.graph.JcApplicationGraphImpl
import org.jacodb.analysis.taint.TaintAnalyzer
import org.jacodb.analysis.taint.TaintDomainFact
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

suspend fun ActorSystem<CommonMessage>.startTaintAnalysis(method: JcMethod) {
    val cp = method.enclosingClass.classpath
    val graph = JcApplicationGraphImpl(cp, cp.usagesExt())
    val taintAnalyzer = TaintAnalyzer(graph)

    for (fact in taintAnalyzer.flowFunctions.obtainPossibleStartFacts(method)) {
        for (entryPoint in graph.entryPoints(method)) {
            val vertex = Vertex(entryPoint, fact)
            val message = NewEdge(ForwardRunnerId, Edge(vertex, vertex), Reason.Initial)
            send(message)
        }
    }
}

suspend fun ActorSystem<CommonMessage>.collectTaintResults(): Collection<TaintVulnerability> =
    collectTaintComputationData()
        .results

suspend fun ActorSystem<CommonMessage>.collectTaintComputationData(): IfdsComputationData<JcInst, TaintDomainFact, TaintVulnerability> {
    val results = ask { CollectAll(ForwardRunnerId, it) }

    @Suppress("UNCHECKED_CAST")
    val ifdsData = results.values as Collection<IfdsComputationData<JcInst, TaintDomainFact, TaintVulnerability>>

    return mergeIfdsResults(ifdsData)
}
