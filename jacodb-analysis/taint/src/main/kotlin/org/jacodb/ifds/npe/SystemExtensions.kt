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

package org.jacodb.ifds.npe

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jacodb.actors.api.ActorSystem
import org.jacodb.actors.impl.system
import org.jacodb.analysis.graph.JcApplicationGraphImpl
import org.jacodb.analysis.graph.defaultBannedPackagePrefixes
import org.jacodb.ifds.taint.TaintDomainFact
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcMethod
import org.jacodb.api.analysis.JcApplicationGraph
import org.jacodb.api.cfg.JcInst
import org.jacodb.ifds.ChunkStrategy
import org.jacodb.ifds.common.ClassChunkStrategy
import org.jacodb.ifds.actors.ProjectManager
import org.jacodb.ifds.domain.Edge
import org.jacodb.ifds.domain.Reason
import org.jacodb.ifds.domain.Vertex
import org.jacodb.ifds.messages.CollectAllData
import org.jacodb.ifds.messages.CommonMessage
import org.jacodb.ifds.messages.NewEdge
import org.jacodb.ifds.result.IfdsComputationData
import org.jacodb.ifds.result.mergeIfdsResults
import org.jacodb.impl.features.usagesExt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

fun npeIfdsSystem(
    name: String,
    cp: JcClasspath,
    graph: JcApplicationGraph,
    bannedPackagePrefixes: List<String> = defaultBannedPackagePrefixes,
    chunkStrategy: ChunkStrategy<JcInst> = ClassChunkStrategy,
): ActorSystem<CommonMessage> {
    val context = npeIfdsContext(
        cp,
        graph,
        bannedPackagePrefixes,
        chunkStrategy
    )
    return system(name) {
        ProjectManager(context)
    }
}

suspend fun ActorSystem<CommonMessage>.runNpeAnalysis(
    methods: Collection<JcMethod>,
    timeout: Duration = 60.seconds,
): Unit = coroutineScope {
    for (method in methods) {
        startNpeAnalysis(method)
    }
    val stopper = launch {
        delay(timeout)
        logger.info { "Timeout! Stopping the system..." }
        stop()
    }
    awaitCompletion()
    stopper.cancel()
    resume()
}

suspend fun ActorSystem<CommonMessage>.startNpeAnalysis(method: JcMethod) {
    val cp = method.enclosingClass.classpath
    val graph = JcApplicationGraphImpl(cp, cp.usagesExt())
    val npeAnalyzer = NpeAnalyzer(SingletonRunnerId, graph)
    for (fact in npeAnalyzer.obtainPossibleStartFacts(method)) {
        for (entryPoint in graph.entryPoints(method)) {
            val vertex = Vertex(entryPoint, fact)
            val message = NewEdge(SingletonRunnerId, Edge(vertex, vertex), Reason.Initial)
            send(message)
        }
    }
}

suspend fun ActorSystem<CommonMessage>.collectNpeResults(): Collection<NpeVulnerability> =
    collectNpeComputationData()
        .findings


suspend fun ActorSystem<CommonMessage>.collectNpeComputationData(): IfdsComputationData<JcInst, TaintDomainFact, NpeVulnerability> {
    val results = ask { CollectAllData(SingletonRunnerId, it) }

    @Suppress("UNCHECKED_CAST")
    val ifdsData = results.values as Collection<IfdsComputationData<JcInst, TaintDomainFact, NpeVulnerability>>

    return mergeIfdsResults(ifdsData)
}
