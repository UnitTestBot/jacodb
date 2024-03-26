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

package org.jacodb.actors.impl.actors

import org.jacodb.actors.api.Actor
import org.jacodb.actors.api.ActorContext
import org.jacodb.actors.api.ActorRef
import org.jacodb.actors.api.ActorStatus
import kotlinx.coroutines.channels.Channel


context(ActorContext<WatcherMessage>)
internal class WatcherActor : Actor<WatcherMessage> {
    private sealed interface Status {
        data object Idle : Status
        data class DetectingTermination(
            val rendezvousChannel: Channel<Unit>,
        ) : Status
    }

    private data class State(
        var status: Status,
        var terminatedActors: Int,
        var totalSent: Long,
        var totalReceived: Long,
    )

    private val watchList = hashMapOf<ActorRef<*>, Snapshot>()
    private val state = State(
        status = Status.Idle,
        terminatedActors = 0,
        totalSent = 0,
        totalReceived = 0
    )

    override suspend fun receive(message: WatcherMessage) {
        when (message) {
            WatcherMessage.Idle -> {
                state.status = Status.Idle
            }

            WatcherMessage.OutOfSystemSend -> {
                state.totalSent++
            }

            is WatcherMessage.AwaitTermination -> {
                state.status = Status.DetectingTermination(message.rendezvous)
            }

            is WatcherMessage.Register -> {
                state.terminatedActors++
                watchList[message.ref] = Snapshot(status = ActorStatus.IDLE, sent = 0, received = 0)
            }

            is WatcherMessage.UpdateSnapshot -> {
                updateSnapshot(message.ref, message.snapshot)
            }
        }
        val status = state.status
        if (status is Status.DetectingTermination) {
            checkTermination(status.rendezvousChannel)
        }
    }

    private suspend fun checkTermination(rendezvousOnTermination: Channel<Unit>) {
        if (state.terminatedActors == watchList.size && state.totalSent == state.totalReceived) {
            logger.info { "Actors:   ${state.terminatedActors}/${watchList.size}" }
            logger.info { "Messages: ${state.totalReceived}/${state.totalSent}" }
            logger.info { "Computation finished..." }
            rendezvousOnTermination.send(Unit)
        }
    }

    private fun updateSnapshot(ref: ActorRef<*>, newSnapshot: Snapshot) {
        val currentSnapshot = watchList[ref] ?: error("$this can't find the current snapshot of $ref")

        if (newSnapshot.status == ActorStatus.BUSY && currentSnapshot.status == ActorStatus.IDLE) {
            state.terminatedActors--
        }
        if (newSnapshot.status == ActorStatus.IDLE && currentSnapshot.status == ActorStatus.BUSY) {
            state.terminatedActors++
        }
        state.totalSent += newSnapshot.sent - currentSnapshot.sent
        state.totalReceived += newSnapshot.received - currentSnapshot.received
        watchList[ref] = newSnapshot
    }
}
