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
import org.jacodb.actors.api.ActorRef
import org.jacodb.actors.api.ActorContext
import org.jacodb.actors.impl.routing.messageKeyRouter
import org.jacodb.ifds.domain.ChunkId
import org.jacodb.ifds.domain.IfdsContext
import org.jacodb.ifds.messages.CommonMessage

context(ActorContext<CommonMessage>)
class ChunkManager<Stmt, Fact>(
    private val ifdsContext: IfdsContext<Stmt, Fact>,
    private val chunkId: ChunkId,
    private val parent: ActorRef<CommonMessage>,
) : Actor<CommonMessage> {

    private val routerFactory = messageKeyRouter(
        ifdsContext::runnerIdByMessage
    ) { runnerId ->
        Runner(this@ActorContext.self, ifdsContext, chunkId, runnerId)
    }

    private val router = spawn(
        "runners",
        factory = routerFactory
    )

    override suspend fun receive(message: CommonMessage) {
        when {
            chunkId == ifdsContext.chunkByMessage(message) -> router.send(message)

            else -> parent.send(message)
        }
    }
}
