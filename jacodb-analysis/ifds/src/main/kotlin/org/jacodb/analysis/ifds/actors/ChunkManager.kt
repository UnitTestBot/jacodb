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

import org.jacodb.actors.api.Actor
import org.jacodb.actors.api.ActorContext
import org.jacodb.actors.api.ActorRef
import org.jacodb.actors.api.signal.Signal
import org.jacodb.actors.impl.routing.messageKeyRouter
import org.jacodb.analysis.ifds.IfdsContext
import org.jacodb.analysis.ifds.domain.Chunk
import org.jacodb.analysis.ifds.messages.CommonMessage
import org.jacodb.analysis.ifds.messages.NewChunk
import org.jacodb.analysis.ifds.messages.RunnerMessage

context(ActorContext<RunnerMessage>)
class ChunkManager<Stmt>(
    private val ifdsContext: IfdsContext<Stmt>,
    private val chunk: Chunk,
    private val parent: ActorRef<CommonMessage>,
) : Actor<RunnerMessage> {

    private val routerFactory = messageKeyRouter(
        ifdsContext::runnerIdByMessage
    ) { runnerId ->
        Runner<Stmt, Nothing>(this@ActorContext.self, ifdsContext, chunk, runnerId)
    }

    private val router = spawn(
        "runners",
        actorFactory = routerFactory
    )

    override suspend fun receive(message: RunnerMessage) {
        when {
            chunk == ifdsContext.chunkByMessage(message) -> router.send(message)

            else -> parent.send(message)
        }
    }

    override suspend fun receive(signal: Signal) {
        when (signal) {
            Signal.Start -> {
                parent.send(NewChunk(chunk))
            }

            Signal.PostStop -> {
                // do nothing
            }

            is Signal.Exception -> {
                logger.error(signal.exception) { "Catch exception in chunk manager:" }
            }
        }
    }
}
