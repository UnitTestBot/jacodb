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

package org.jacodb.analysis.ifds.actors

import kotlinx.coroutines.CompletableDeferred
import org.jacodb.actors.api.Actor
import org.jacodb.actors.api.ActorContext
import org.jacodb.actors.impl.routing.messageKeyRouter
import org.jacodb.analysis.ifds.IfdsContext
import org.jacodb.analysis.ifds.domain.Chunk
import org.jacodb.analysis.ifds.messages.CollectAllData
import org.jacodb.analysis.ifds.messages.CollectData
import org.jacodb.analysis.ifds.messages.CommonMessage
import org.jacodb.analysis.ifds.messages.NewChunk
import org.jacodb.analysis.ifds.messages.ProjectMessage
import org.jacodb.analysis.ifds.messages.RunnerMessage
import org.jacodb.analysis.ifds.result.Finding
import org.jacodb.analysis.ifds.result.IfdsComputationData

context(ActorContext<CommonMessage>)
class ProjectManager<Stmt>(
    private val ifdsContext: IfdsContext<Stmt>,
) : Actor<CommonMessage> {

    private val routerFactory = messageKeyRouter(
        keyExtractor = ifdsContext::chunkByMessage
    ) { chunk -> ChunkManager(ifdsContext, chunk, this@ActorContext.self) }

    private val router = spawn("chunks", actorFactory = routerFactory)

    private val chunks = hashSetOf<Chunk>()

    override suspend fun receive(message: CommonMessage) {
        when (message) {
            is RunnerMessage -> {
                router.send(message)
            }

            is ProjectMessage -> {
                processProjectMessage(message)
            }
        }
    }

    private suspend fun processProjectMessage(message: ProjectMessage) {
        when (message) {
            is NewChunk -> {
                chunks.add(message.chunk)
            }

            is CollectAllData -> {
                val results = hashMapOf<Chunk, IfdsComputationData<*, *, *>>()
                for (chunk in chunks) {
                    val ready = CompletableDeferred<IfdsComputationData<Stmt, Any?, Finding<Stmt, Any?>>>()
                    val msg = CollectData(chunk, message.runnerId, ready)
                    router.send(msg)
                    val data = ready.await()
                    results[chunk] = data
                }
                message.result.complete(results)
            }
        }
    }
}
