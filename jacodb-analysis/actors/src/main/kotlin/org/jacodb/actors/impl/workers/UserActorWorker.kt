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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ChannelResult
import kotlinx.coroutines.channels.onClosed
import kotlinx.coroutines.channels.onSuccess
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import org.jacodb.actors.api.Actor
import org.jacodb.actors.api.ActorPath
import org.jacodb.actors.api.ActorRef
import org.jacodb.actors.api.ActorStatus
import org.jacodb.actors.api.signal.Signal
import org.jacodb.actors.impl.actors.internal.Snapshot
import org.jacodb.actors.impl.actors.internal.WatcherMessage
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration

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
    private val admissionAvailable = Channel<Unit>(capacity = Channel.CONFLATED)

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

    override fun sendSelfWithDelay(message: Message, waitDelay: Duration) {
        sent++
        scope.launch {
            delay(waitDelay)
            receive(message)
        }
    }

    override suspend fun receive(message: Message): Boolean {
        try {
            while (true) {
                currentCoroutineContext().ensureActive()
                val admitted = channel.trySend(message)
                if (admitted.isSuccess) return true
                if (admitted.isClosed) admitted.getOrThrow()

                admissionAvailable.receive()
            }
        } finally {
            // Pass the wake-up to another waiting sender, including when this sender is canceled.
            // A failed attempt does not produce another token, so a full mailbox cannot busy-loop.
            admissionAvailable.trySend(Unit)
        }
    }

    private suspend fun receiveNotifyingSenders(): ChannelResult<Message> = coroutineScope {
        // Register the receiver before waking a rendezvous sender. The child is joined/canceled
        // with this actor loop, so close cannot leave a detached pending receive behind.
        val next = async(start = CoroutineStart.UNDISPATCHED) { channel.receiveCatching() }
        admissionAvailable.trySend(Unit)
        next.await()
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
                receiveResult = receiveNotifyingSenders()
            } else {
                admissionAvailable.trySend(Unit)
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
