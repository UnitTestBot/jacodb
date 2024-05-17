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
import org.jacodb.actors.impl.routing.roundRobinRouter
import org.jacodb.analysis.ifds.IfdsContext
import org.jacodb.analysis.ifds.domain.Analyzer
import org.jacodb.analysis.ifds.domain.Chunk
import org.jacodb.analysis.ifds.domain.RunnerId
import org.jacodb.analysis.ifds.messages.AnalyzerMessage
import org.jacodb.analysis.ifds.messages.IndirectionMessage
import org.jacodb.analysis.ifds.messages.RunnerMessage
import org.jacodb.analysis.ifds.messages.StorageMessage

context(ActorContext<RunnerMessage>)
class Runner<Stmt, Fact>(
    private val parent: ActorRef<RunnerMessage>,
    private val ifdsContext: IfdsContext<Stmt>,
    private val chunk: Chunk,
    private val runnerId: RunnerId,
) : Actor<RunnerMessage> {
    private val routerFactory = roundRobinRouter(size = ifdsContext.options.workersPerRunner) {
        @Suppress("UNCHECKED_CAST")
        val analyzer = ifdsContext.getAnalyzer(runnerId) as Analyzer<Stmt, Fact>
        Worker(analyzer, this@ActorContext.self)
    }

    private val router = spawn("workers", actorFactory = routerFactory)

    private val storage = spawn("storage") {
        RunnerStorage<Stmt, Fact>(this@ActorContext.self, runnerId)
    }

    private val indirectionHandler = spawn("indirection") {
        val indirectionHandler = ifdsContext.getIndirectionHandler(runnerId)
        IndirectionHandlerActor(this@ActorContext.self, indirectionHandler)
    }

    override suspend fun receive(message: RunnerMessage) {
        when {
            ifdsContext.chunkByMessage(message) == chunk && ifdsContext.runnerIdByMessage(message) == runnerId -> {
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
