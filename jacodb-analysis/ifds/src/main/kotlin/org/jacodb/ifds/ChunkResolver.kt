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

package org.jacodb.ifds

import org.jacodb.ifds.domain.Chunk
import org.jacodb.ifds.messages.AnalyzerMessage
import org.jacodb.ifds.messages.EdgeMessage
import org.jacodb.ifds.messages.IndirectionMessage
import org.jacodb.ifds.messages.NewEdge
import org.jacodb.ifds.messages.NewFinding
import org.jacodb.ifds.messages.NewSummaryEdge
import org.jacodb.ifds.messages.NotificationOnEnd
import org.jacodb.ifds.messages.NotificationOnStart
import org.jacodb.ifds.messages.CollectData
import org.jacodb.ifds.messages.ResolvedCall
import org.jacodb.ifds.messages.RunnerMessage
import org.jacodb.ifds.messages.StorageMessage
import org.jacodb.ifds.messages.SubscriptionOnEnd
import org.jacodb.ifds.messages.SubscriptionOnStart
import org.jacodb.ifds.messages.UnresolvedCall

fun interface ChunkResolver {
    fun chunkByMessage(message: RunnerMessage): Chunk
}

fun interface ChunkStrategy<Stmt> {
    fun chunkByStmt(stmt: Stmt): Chunk
}

class DefaultChunkResolver<Stmt>(
    private val chunkStrategy: ChunkStrategy<Stmt>,
) : ChunkResolver {
    @Suppress("UNCHECKED_CAST")
    override fun chunkByMessage(message: RunnerMessage): Chunk =
        when (message) {
            is AnalyzerMessage<*, *> -> {
                when (message) {
                    is EdgeMessage<*, *> -> {
                        message as EdgeMessage<Stmt, *>
                        chunkStrategy.chunkByStmt(message.edge.to.statement)
                    }

                    is NotificationOnEnd<*, *> -> {
                        message as NotificationOnEnd<Stmt, *>
                        chunkStrategy.chunkByStmt(message.summaryEdge.to.statement)
                    }

                    is NotificationOnStart<*, *> -> {
                        message as NotificationOnStart<Stmt, *>
                        chunkStrategy.chunkByStmt(message.summaryEdge.to.statement)
                    }

                    is ResolvedCall<*, *, *> -> {
                        message as ResolvedCall<Stmt, *, *>
                        chunkStrategy.chunkByStmt(message.edge.to.statement)
                    }

                    else -> {
                        error("Unexpected message: $message")
                    }
                }
            }

            is IndirectionMessage -> {
                when (message) {
                    is UnresolvedCall<*, *> -> {
                        message as UnresolvedCall<Stmt, *>
                        chunkStrategy.chunkByStmt(message.edge.to.statement)
                    }

                    else -> {
                        error("Unexpected message: $message")
                    }
                }
            }

            is StorageMessage -> {
                when (message) {
                    is NewEdge<*, *> -> {
                        message as NewEdge<Stmt, *>
                        chunkStrategy.chunkByStmt(message.edge.to.statement)
                    }

                    is NewFinding<*, *> -> {
                        message as NewFinding<Stmt, *>
                        chunkStrategy.chunkByStmt(message.finding.vertex.statement)
                    }

                    is NewSummaryEdge<*, *> -> {
                        message as NewSummaryEdge<Stmt, *>
                        chunkStrategy.chunkByStmt(message.edge.to.statement)
                    }

                    is CollectData<*, *, *> -> {
                        message as CollectData<Stmt, *, *>
                        message.chunk
                    }

                    is SubscriptionOnEnd<*, *> -> {
                        message as SubscriptionOnEnd<Stmt, *>
                        chunkStrategy.chunkByStmt(message.endVertex.statement)
                    }

                    is SubscriptionOnStart<*, *> -> {
                        message as SubscriptionOnStart<Stmt, *>
                        chunkStrategy.chunkByStmt(message.startVertex.statement)
                    }
                }
            }
        }
}
