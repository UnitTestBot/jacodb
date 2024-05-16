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

package org.jacodb.analysis.ifds.common

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jacodb.actors.api.ActorSystem
import org.jacodb.actors.impl.ActorSystemOptions
import org.jacodb.actors.impl.system
import org.jacodb.analysis.ifds.actors.ProjectManager
import org.jacodb.analysis.ifds.domain.Edge
import org.jacodb.analysis.ifds.domain.Reason
import org.jacodb.analysis.ifds.domain.RunnerId
import org.jacodb.analysis.ifds.domain.Vertex
import org.jacodb.analysis.ifds.messages.CollectAllData
import org.jacodb.analysis.ifds.messages.CommonMessage
import org.jacodb.analysis.ifds.messages.NewEdge
import org.jacodb.analysis.ifds.result.Finding
import org.jacodb.analysis.ifds.result.IfdsComputationData
import org.jacodb.analysis.ifds.result.mergeIfdsResults
import org.jacodb.api.JcMethod
import org.jacodb.api.analysis.JcApplicationGraph
import org.jacodb.api.cfg.JcInst
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

open class JcIfdsFacade<Fact, F : Finding<JcInst, Fact>>(
    name: String,
    private val graph: JcApplicationGraph,
    private val context: JcIfdsContext<Fact>,
    private val startingRunnerId: RunnerId,
) : AutoCloseable {
    private val system: ActorSystem<CommonMessage> = system(name, DefaultSystemOptions) {
        ProjectManager(context)
    }

    open suspend fun runAnalysis(
        methods: Collection<JcMethod>,
        timeout: Duration = 60.seconds,
    ) = coroutineScope {
        for (method in methods) {
            startAnalysis(method)
        }
        val stopper = launch {
            delay(timeout)
            system.logger.info { "Timeout! Stopping the system..." }
            system.stop()
        }
        system.awaitCompletion()
        stopper.cancel()
        system.resume()
    }

    open suspend fun startAnalysis(
        method: JcMethod,
    ) {
        val analyzer = context.getAnalyzer(startingRunnerId)
        for (fact in analyzer.flowFunctions.obtainPossibleStartFacts(method)) {
            for (entryPoint in graph.entryPoints(method)) {
                val vertex = Vertex(entryPoint, fact)
                val edge = Edge(vertex, vertex)
                val newEdgeMessage = NewEdge(startingRunnerId, edge, Reason.Initial)
                system.send(newEdgeMessage)
            }
        }
    }

    suspend fun awaitAnalysis() {
        system.awaitCompletion()
    }

    open suspend fun collectFindings(): Collection<F> =
        collectComputationData().findings

    open suspend fun collectComputationData(): IfdsComputationData<JcInst, Fact, F> {
        val results = system.ask { CollectAllData(startingRunnerId, it) }

        @Suppress("UNCHECKED_CAST")
        val ifdsData = results.values as Collection<IfdsComputationData<JcInst, Fact, F>>

        return mergeIfdsResults(ifdsData)
    }

    override fun close() {
        system.close()
    }

    companion object {
        val DefaultSystemOptions = ActorSystemOptions(printStatisticsPeriod = 10.seconds)
    }
}
