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

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jacodb.actors.api.ActorSystem
import org.jacodb.actors.impl.system
import org.jacodb.analysis.graph.JcApplicationGraphImpl
import org.jacodb.analysis.graph.defaultBannedPackagePrefixes
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

fun unusedIfdsSystem(
    name: String,
    cp: JcClasspath,
    graph: JcApplicationGraph,
    bannedPackagePrefixes: List<String> = defaultBannedPackagePrefixes,
    chunkStrategy: ChunkStrategy<JcInst> = ClassChunkStrategy,
): ActorSystem<CommonMessage> {
    val context = unusedIfdsContext(
        cp,
        graph,
        bannedPackagePrefixes,
        chunkStrategy
    )
    return system(name) {
        ProjectManager(context)
    }
}

suspend fun ActorSystem<CommonMessage>.runUnusedAnalysis(
    methods: Collection<JcMethod>,
    timeout: Duration = 60.seconds,
): Unit = coroutineScope {
    for (method in methods) {
        startUnusedAnalysis(method)
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

suspend fun ActorSystem<CommonMessage>.startUnusedAnalysis(method: JcMethod) {
    val cp = method.enclosingClass.classpath
    val graph = JcApplicationGraphImpl(cp, cp.usagesExt())
    val unusedAnalyzer = UnusedVariableAnalyzer(SingletonRunnerId, graph)

    for (fact in unusedAnalyzer.obtainPossibleStartFacts(method)) {
        for (entryPoint in graph.entryPoints(method)) {
            val vertex = Vertex(entryPoint, fact)
            val message = NewEdge(SingletonRunnerId, Edge(vertex, vertex), Reason.Initial)
            send(message)
        }
    }
}

suspend fun ActorSystem<CommonMessage>.collectUnusedResults(): Collection<UnusedVulnerability> {
    val data = collectUnusedComputationData()

    val allFacts = data.factsByStmt

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
        UnusedVulnerability(
            message = "Assigned value is unused",
            sink = Vertex(it, UnusedVariableZeroFact)
        )
    }
    return vulnerabilities
}

suspend fun ActorSystem<CommonMessage>.collectUnusedComputationData(): IfdsComputationData<JcInst, UnusedVariableDomainFact, UnusedVulnerability> {
    val results = ask {
        CollectAllData(
            SingletonRunnerId,
            it
        )
    }

    @Suppress("UNCHECKED_CAST")
    val mergedData =
        mergeIfdsResults(results.values as Collection<IfdsComputationData<JcInst, UnusedVariableDomainFact, UnusedVulnerability>>)
    return mergedData
}
