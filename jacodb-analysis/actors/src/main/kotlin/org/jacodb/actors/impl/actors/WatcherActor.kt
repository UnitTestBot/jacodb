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

import kotlinx.coroutines.CompletableDeferred
import org.jacodb.actors.api.Actor
import org.jacodb.actors.api.ActorContext
import org.jacodb.actors.api.ActorPath
import org.jacodb.actors.api.ActorStatus

context(ActorContext<WatcherMessage>)
internal class WatcherActor : Actor<WatcherMessage> {
    private sealed interface Status {
        data object Idle : Status
        data class DetectingTermination(
            val computation: CompletableDeferred<Unit>,
        ) : Status
    }

    private data class State(
        var status: Status,
        var terminatedActors: Int,
        var totalSent: Long,
        var totalReceived: Long,
    )

    private val watchList = hashMapOf<ActorPath, Snapshot>()
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
                state.status = Status.DetectingTermination(message.computationFinished)
            }

            is WatcherMessage.Register -> {
                watchList[message.path] = Snapshot(status = ActorStatus.BUSY, sent = 0, received = 0)
            }

            is WatcherMessage.UpdateSnapshot -> {
                updateSnapshot(message.path, message.snapshot)
            }
        }
        val status = state.status
        if (status is Status.DetectingTermination) {
            checkTermination(status.computation)
        }
    }

    private fun checkTermination(computationFinished: CompletableDeferred<Unit>) {
        if (state.terminatedActors == watchList.size && state.totalSent == state.totalReceived) {
            logger.info { "Actors:   ${state.terminatedActors}/${watchList.size}" }
            logger.info { "Messages: ${state.totalReceived}/${state.totalSent}" }
            logger.info { "Computation finished..." }
            computationFinished.complete(Unit)
        }
    }

    private fun updateSnapshot(path: ActorPath, newSnapshot: Snapshot) {
        val currentSnapshot = watchList[path] ?: error("$this can't find the current snapshot of $path")

        if (newSnapshot.status == ActorStatus.BUSY && currentSnapshot.status == ActorStatus.IDLE) {
            state.terminatedActors--
        }
        if (newSnapshot.status == ActorStatus.IDLE && currentSnapshot.status == ActorStatus.BUSY) {
            state.terminatedActors++
        }
        state.totalSent += newSnapshot.sent - currentSnapshot.sent
        state.totalReceived += newSnapshot.received - currentSnapshot.received
        watchList[path] = newSnapshot
    }
}
