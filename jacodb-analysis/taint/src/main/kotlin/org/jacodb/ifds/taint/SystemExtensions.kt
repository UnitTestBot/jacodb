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

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.future
import kotlinx.coroutines.launch
import org.jacodb.actors.api.ActorSystem
import org.jacodb.actors.impl.system
import org.jacodb.analysis.graph.JcApplicationGraphImpl
import org.jacodb.analysis.graph.defaultBannedPackagePrefixes
import org.jacodb.analysis.taint.ForwardTaintAnalyzer
import org.jacodb.analysis.taint.TaintDomainFact
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcMethod
import org.jacodb.api.analysis.JcApplicationGraph
import org.jacodb.api.cfg.JcInst
import org.jacodb.ifds.ChunkStrategy
import org.jacodb.ifds.ClassChunkStrategy
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
import java.util.concurrent.CompletableFuture
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

fun taintIfdsSystem(
    name: String,
    cp: JcClasspath,
    graph: JcApplicationGraph,
    bannedPackagePrefixes: List<String> = defaultBannedPackagePrefixes,
    chunkStrategy: ChunkStrategy<JcInst> = ClassChunkStrategy,
): ActorSystem<CommonMessage> {
    val context = taintIfdsContext(
        cp,
        graph,
        bannedPackagePrefixes,
        chunkStrategy
    )
    return system(name) {
        ProjectManager(context)
    }
}

suspend fun ActorSystem<CommonMessage>.runTaintAnalysis(
    methods: Collection<JcMethod>,
    timeout: Duration = 60.seconds,
): Unit = coroutineScope {
    for (method in methods) {
        startTaintAnalysis(method)
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

@OptIn(DelicateCoroutinesApi::class)
@JvmName("runTaintAnalysisAsync")
fun ActorSystem<CommonMessage>.runTaintAnalysisAsync(
    methods: Collection<JcMethod>,
    timeout: Duration = 60.seconds,
): CompletableFuture<Unit> = GlobalScope.future {
    runTaintAnalysis(methods, timeout)
}

suspend fun ActorSystem<CommonMessage>.startTaintAnalysis(method: JcMethod) {
    val cp = method.enclosingClass.classpath
    val graph = JcApplicationGraphImpl(cp, cp.usagesExt())
    val forwardTaintAnalyzer = ForwardTaintAnalyzer(ForwardRunnerId, graph)

    for (fact in forwardTaintAnalyzer.obtainPossibleStartFacts(method)) {
        for (entryPoint in graph.entryPoints(method)) {
            val vertex = Vertex(entryPoint, fact)
            val message = NewEdge(ForwardRunnerId, Edge(vertex, vertex), Reason.Initial)
            send(message)
        }
    }
}

@OptIn(DelicateCoroutinesApi::class)
fun ActorSystem<CommonMessage>.startTaintAnalysisAsync(
    method: JcMethod,
): CompletableFuture<Unit> = GlobalScope.future {
    startTaintAnalysis(method)
}

suspend fun ActorSystem<CommonMessage>.collectTaintResults(): Collection<TaintVulnerability> =
    collectTaintComputationData()
        .findings

@OptIn(DelicateCoroutinesApi::class)
fun ActorSystem<CommonMessage>.collectTaintResultsAsync(): CompletableFuture<Collection<TaintVulnerability>> =
    GlobalScope.future {
        collectTaintResults()
    }

suspend fun ActorSystem<CommonMessage>.collectTaintComputationData(): IfdsComputationData<JcInst, TaintDomainFact, TaintVulnerability> {
    val results = ask { CollectAllData(ForwardRunnerId, it) }

    @Suppress("UNCHECKED_CAST")
    val ifdsData = results.values as Collection<IfdsComputationData<JcInst, TaintDomainFact, TaintVulnerability>>

    return mergeIfdsResults(ifdsData)
}

@OptIn(DelicateCoroutinesApi::class)
fun ActorSystem<CommonMessage>.collectTaintComputationDataAsync(): CompletableFuture<IfdsComputationData<JcInst, TaintDomainFact, TaintVulnerability>> =
    GlobalScope.future {
        collectTaintComputationData()
    }
