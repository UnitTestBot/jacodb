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
import org.jacodb.ifds.domain.Edge
import org.jacodb.ifds.domain.Reason
import org.jacodb.ifds.domain.RunnerId
import org.jacodb.ifds.domain.Vertex
import org.jacodb.ifds.messages.AnalyzerMessage
import org.jacodb.ifds.messages.CollectData
import org.jacodb.ifds.messages.EdgeMessage
import org.jacodb.ifds.messages.NewEdge
import org.jacodb.ifds.messages.NewFinding
import org.jacodb.ifds.messages.NewSummaryEdge
import org.jacodb.ifds.messages.NotificationOnEnd
import org.jacodb.ifds.messages.NotificationOnStart
import org.jacodb.ifds.messages.RunnerMessage
import org.jacodb.ifds.messages.StorageMessage
import org.jacodb.ifds.messages.SubscriptionOnEnd
import org.jacodb.ifds.messages.SubscriptionOnStart
import org.jacodb.ifds.result.Finding
import org.jacodb.ifds.result.IfdsComputationData

context(ActorContext<StorageMessage>)
class RunnerStorage<Stmt, Fact>(
    private val parent: ActorRef<RunnerMessage>,
    private val runnerId: RunnerId,
) : Actor<StorageMessage> {
    data class SavedSubscription<Stmt, Fact>(
        val edge: Edge<Stmt, Fact>,
        val subscriber: RunnerId,
    )

    private val startSubscribers =
        HashMap<Vertex<Stmt, Fact>, HashSet<SavedSubscription<Stmt, Fact>>>()
    private val endSubscribers =
        HashMap<Vertex<Stmt, Fact>, HashSet<SavedSubscription<Stmt, Fact>>>()

    private val edges = hashSetOf<Edge<Stmt, Fact>>()
    private val reasons = hashMapOf<Edge<Stmt, Fact>, HashSet<Reason<Stmt, Fact>>>()

    private val summaryEdges = hashSetOf<Edge<Stmt, Fact>>()
    private val summaryEdgesByStart = hashMapOf<Vertex<Stmt, Fact>, HashSet<Edge<Stmt, Fact>>>()
    private val summaryEdgesByEnd = hashMapOf<Vertex<Stmt, Fact>, HashSet<Edge<Stmt, Fact>>>()

    private val findings = hashSetOf<Finding<Stmt, Fact>>()

    override suspend fun receive(message: StorageMessage) {
        when (message) {
            is NewEdge<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                message as NewEdge<Stmt, Fact>

                val edge = message.edge

                reasons
                    .computeIfAbsent(edge) { hashSetOf() }
                    .add(message.reason)

                if (edges.add(edge)) {
                    // new edge
                    parent.send(EdgeMessage(runnerId, edge))
                }
            }

            is NewSummaryEdge<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                message as NewSummaryEdge<Stmt, Fact>

                val edge = message.edge

                if (summaryEdges.add(edge)) {
                    summaryEdgesByStart
                        .computeIfAbsent(edge.from) { hashSetOf() }
                        .add(edge)
                    summaryEdgesByEnd
                        .computeIfAbsent(edge.to) { hashSetOf() }
                        .add(edge)

                    // subscriptions
                    sendNotificationsOnExistingSubscribers(
                        startSubscribers[edge.from].orEmpty()
                    ) { (subscribingEdge, subscriber) ->
                        NotificationOnStart(
                            subscriber,
                            runnerId,
                            edge,
                            subscribingEdge
                        )

                    }
                    sendNotificationsOnExistingSubscribers(
                        endSubscribers[edge.to].orEmpty()
                    ) { (subscribingEdge, subscriber) ->
                        NotificationOnEnd(
                            subscriber,
                            runnerId,
                            edge,
                            subscribingEdge
                        )
                    }
                }
            }

            is SubscriptionOnStart<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                message as SubscriptionOnStart<Stmt, Fact>

                val savedSubscription = SavedSubscription(message.subscribingEdge, message.subscriber)

                sendNotificationsOnExistingSummaryEdges(
                    summaryEdgesByStart[message.startVertex].orEmpty()
                ) { summaryEdge ->
                    NotificationOnStart(
                        savedSubscription.subscriber,
                        runnerId,
                        summaryEdge,
                        savedSubscription.edge
                    )
                }

                startSubscribers
                    .computeIfAbsent(message.startVertex) { hashSetOf() }
                    .add(savedSubscription)
            }

            is SubscriptionOnEnd<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                message as SubscriptionOnEnd<Stmt, Fact>

                val savedSubscription = SavedSubscription(message.subscribingEdge, message.subscriber)

                sendNotificationsOnExistingSummaryEdges(
                    summaryEdgesByEnd[message.endVertex].orEmpty()
                ) { summaryEdge ->
                    NotificationOnEnd(
                        savedSubscription.subscriber,
                        runnerId,
                        summaryEdge,
                        savedSubscription.edge
                    )
                }

                endSubscribers
                    .computeIfAbsent(message.endVertex) { hashSetOf() }
                    .add(savedSubscription)
            }

            is NewFinding<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                message as NewFinding<Stmt, Fact>
                findings.add(message.finding)
            }

            is CollectData<*, *, *> -> {
                @Suppress("UNCHECKED_CAST")
                message as CollectData<Stmt, Fact, Finding<Stmt, Fact>>
                val data = IfdsComputationData(
                    edges.groupByTo(hashMapOf()) { it.to },
                    edges.groupByTo(hashMapOf(), { it.to.statement }) { it.to.fact },
                    reasons.toMap(hashMapOf()),
                    findings.toHashSet()
                )
                message.data.complete(data)
            }
        }
    }

    private suspend inline fun sendNotificationsOnExistingSummaryEdges(
        summaries: Set<Edge<Stmt, Fact>>,
        notificationBySummary: (Edge<Stmt, Fact>) -> AnalyzerMessage<Stmt, Fact>,
    ) {
        for (summaryEdge in summaries) {
            val notification = notificationBySummary(summaryEdge)
            parent.send(notification)
        }
    }

    private suspend inline fun sendNotificationsOnExistingSubscribers(
        currentEdgeSubscribers: Set<SavedSubscription<Stmt, Fact>>,
        notificationBySavedSubscription: (SavedSubscription<Stmt, Fact>) -> AnalyzerMessage<Stmt, Fact>,
    ) {
        for (subscription in currentEdgeSubscribers) {
            val notification = notificationBySavedSubscription(subscription)
            parent.send(notification)
        }
    }
}
