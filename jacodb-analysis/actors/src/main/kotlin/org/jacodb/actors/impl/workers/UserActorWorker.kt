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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.onClosed
import kotlinx.coroutines.channels.onSuccess
import kotlinx.coroutines.launch
import org.jacodb.actors.api.Actor
import org.jacodb.actors.api.ActorPath
import org.jacodb.actors.api.ActorRef
import org.jacodb.actors.api.ActorStatus
import org.jacodb.actors.api.signal.Signal
import org.jacodb.actors.impl.actors.Snapshot
import org.jacodb.actors.impl.actors.WatcherMessage
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.CoroutineContext

internal class UserActorWorker<Message>(
    path: ActorPath,
    private val channel: Channel<Message>,
    private val scope: CoroutineScope,
    private val watcher: ActorRef<WatcherMessage>,
) : ActorWorker<Message>(path) {

    private var received = 0
    private var sent = 0
    private var status = ActorStatus.BUSY
    private val working = AtomicBoolean(true)

    override fun launchLoop(actor: Actor<Message>) {
        scope.launch {
            sendInternal(watcher, WatcherMessage.Register(path))
            actor.receive(Signal.Start)
            loop(actor)
            actor.receive(Signal.PostStop)
        }
    }

    override suspend fun <TargetMessage> send(destination: ActorRef<TargetMessage>, message: TargetMessage) {
        if (destination.receive(message)) {
            sent++
        }
    }

    override suspend fun receive(message: Message): Boolean {
        channel.send(message)
        return true
    }

    private suspend fun <TargetMessage> sendInternal(to: ActorRef<TargetMessage>, message: TargetMessage) {
        to.receive(message)
    }

    private suspend fun loop(
        actor: Actor<Message>,
    ) {
        while (true) {
            var receiveResult = channel.tryReceive()
            if (receiveResult.isFailure) {
                processEmptyChannel()
                receiveResult = channel.receiveCatching()
            }
            receiveResult
                .onClosed {
                    processEmptyChannel()
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
        updateReceived()
        if (working.get()) {
            try {
                actor.receive(message)
            } catch (e: Exception) {
                actor.receive(Signal.Exception(e))
                working.set(false)
            }
        }
    }

    private suspend fun updateReceived() {
        received++

        if (status == ActorStatus.IDLE) {
            status = ActorStatus.BUSY
            watcher.receive(WatcherMessage.UpdateSnapshot(path, Snapshot(status, sent, received)))
        }
    }

    private suspend fun processEmptyChannel() {
        if (status == ActorStatus.BUSY) {
            status = ActorStatus.IDLE
            val snapshot = WatcherMessage.UpdateSnapshot(path, Snapshot(status, sent, received))
            watcher.receive(snapshot)
        }
    }

    override fun stop() {
        working.set(false)
    }

    override fun resume() {
        working.set(true)
    }
}
