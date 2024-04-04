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

package org.jacodb.ifds.actors

import org.jacodb.actors.api.Actor
import org.jacodb.actors.api.ActorContext
import org.jacodb.actors.api.ActorRef
import org.jacodb.actors.impl.routing.firstReadyRouter
import org.jacodb.ifds.domain.ChunkId
import org.jacodb.ifds.domain.IfdsContext
import org.jacodb.ifds.domain.RunnerId
import org.jacodb.ifds.messages.AnalyzerMessage
import org.jacodb.ifds.messages.CommonMessage
import org.jacodb.ifds.messages.IndirectionMessage
import org.jacodb.ifds.messages.StorageMessage

context(ActorContext<CommonMessage>)
class Runner<Stmt, Fact>(
    private val parent: ActorRef<CommonMessage>,
    private val ifdsContext: IfdsContext<Stmt, Fact>,
    private val chunkId: ChunkId,
    private val runnerId: RunnerId,
) : Actor<CommonMessage> {
    private val routerFactory = firstReadyRouter(size = 8) {
        Worker(ifdsContext.getAnalyzer(chunkId, runnerId), this@ActorContext.self)
    }

    private val router = spawn("workers", factory = routerFactory)

    private val storage = spawn("storage") {
        RunnerStorage<Stmt, Fact>(this@ActorContext.self, chunkId, runnerId)
    }

    private val indirectionHandler = spawn(
        "indirection",
        factory = ifdsContext.indirectionHandlerFactory(this@ActorContext.self, runnerId)
    )

    override suspend fun receive(message: CommonMessage) {
        when {
            ifdsContext.chunkByMessage(message) == chunkId && ifdsContext.runnerIdByMessage(message) == runnerId -> {
                @Suppress("UNCHECKED_CAST")
                when (message) {
                    is StorageMessage -> storage.send(message)
                    is AnalyzerMessage<*, *> -> router.send(message as AnalyzerMessage<Stmt, Fact>)
                    is IndirectionMessage -> indirectionHandler.send(message)
                }
            }

            else -> parent.send(message)
        }
    }
}
