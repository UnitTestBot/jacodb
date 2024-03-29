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

package org.jacodb.actors.impl.workers

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.onClosed
import kotlinx.coroutines.channels.onSuccess
import kotlinx.coroutines.launch
import org.jacodb.actors.api.Actor
import org.jacodb.actors.api.ActorRef
import org.jacodb.actors.api.ActorStatus
import org.jacodb.actors.api.signal.Signal
import org.jacodb.actors.impl.ActorRefImpl
import org.jacodb.actors.impl.actors.Snapshot
import org.jacodb.actors.impl.actors.WatcherMessage
import kotlin.coroutines.CoroutineContext


internal class UserActorWorker<Message>(
    override val self: ActorRef<Message>,
    override val channel: Channel<Message>,
    private val scope: CoroutineScope,
    private val watcher: ActorRefImpl<WatcherMessage>,
) : ActorWorker<Message> {

    private var received = 0
    private var sent = 0
    private var status = ActorStatus.IDLE
    private val job = Job()

    override fun launchLoop(
        coroutineContext: CoroutineContext,
        actor: Actor<Message>,
    ) {
        scope.launch(job) {
            sendInternal(watcher, WatcherMessage.Register(self))
            loop(actor)
            actor.receive(Signal.PostStop)
        }
    }

    override suspend fun <TargetMessage> send(ref: ActorRefImpl<TargetMessage>, message: TargetMessage) {
        sent++
        ref.send(message)
    }

    private suspend fun <TargetMessage> sendInternal(ref: ActorRefImpl<TargetMessage>, message: TargetMessage) {
        ref.send(message)
    }

    private suspend fun loop(
        actor: Actor<Message>,
    ) {
        var running = true
        while (running) {
            var receiveResult = channel.tryReceive()
            if (receiveResult.isFailure) {
                processEmptyChannel()
                receiveResult = channel.receiveCatching()
            }
            receiveResult
                .onClosed {
                    processEmptyChannel()
                    running = false
                }
                .onSuccess { message ->
                    processMessage(actor, message)
                }
        }
    }

    private suspend fun processMessage(
        actor: Actor<Message>,
        message: Message,
    ) {
        if (actor.flag) {
            received++
        }
        if (status == ActorStatus.IDLE) {
            status = ActorStatus.BUSY
            watcher.send(WatcherMessage.UpdateSnapshot(self, Snapshot(status, sent, received)))
        }
        actor.receive(message)
    }

    private suspend fun processEmptyChannel() {
        if (status == ActorStatus.BUSY) {
            status = ActorStatus.IDLE
            val snapshot = WatcherMessage.UpdateSnapshot(self, Snapshot(status, sent, received))
            watcher.send(snapshot)
        }
    }
}
