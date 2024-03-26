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

import org.jacodb.actors.api.Actor
import org.jacodb.actors.api.ActorRef
import org.jacodb.actors.api.ActorStatus
import org.jacodb.actors.api.signal.Signal
import org.jacodb.actors.impl.ActorRefImpl
import org.jacodb.actors.impl.Die
import org.jacodb.actors.impl.InternalMessage
import org.jacodb.actors.impl.Message
import org.jacodb.actors.impl.UserMessage
import org.jacodb.actors.impl.actors.Snapshot
import org.jacodb.actors.impl.actors.WatcherMessage
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.onSuccess
import kotlinx.coroutines.launch
import mu.KotlinLogging.logger
import kotlin.coroutines.CoroutineContext


internal class UserActorWorker<M>(
    override val self: ActorRef<M>,
    override val channel: Channel<Message>,
    private val scope: CoroutineScope,
    private val watcher: ActorRefImpl<WatcherMessage>,
) : ActorWorker<M> {

    private var received = 0
    private var sent = 0
    private var status = ActorStatus.IDLE

    private val handler = CoroutineExceptionHandler { _, it ->
        System.err.println("$self: ${it.stackTraceToString()}")
    }

    override fun launchLoop(
        coroutineContext: CoroutineContext,
        actor: Actor<M>,
    ) {
        scope.launch(coroutineContext + handler) {
            sendInternal(watcher, WatcherMessage.Register(self))
            loop(actor)
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
        actor: Actor<M>,
    ) {
        while (true) {
            val receiveResult = channel.tryReceive()
            receiveResult
                .onSuccess { message ->
                    processMessage(actor, message)
                }
            if (receiveResult.isFailure) {
                processEmptyChannel()
                val result = channel.receive()
                processMessage(actor, result)
            }
        }
    }

    private suspend fun processMessage(
        actor: Actor<M>,
        message: Message,
    ) {
        @Suppress("UNCHECKED_CAST")
        when (message) {
            is UserMessage<*> -> processUserMessage(actor, message.message as M)
            is InternalMessage -> processInternalMessage(actor, message)
        }
    }

    private suspend fun processEmptyChannel() {
        if (status == ActorStatus.BUSY) {
            status = ActorStatus.IDLE
            val snapshot = WatcherMessage.UpdateSnapshot(self, Snapshot(status, sent, received))
            watcher.send(snapshot)
        }
    }

    private suspend fun processUserMessage(actor: Actor<M>, message: M) {
        if (actor.flag) {
            received++
        }

        if (status == ActorStatus.IDLE) {
            status = ActorStatus.BUSY
            watcher.send(WatcherMessage.UpdateSnapshot(self, Snapshot(status, sent, received)))
        }

        actor.receive(message)
    }

    private suspend fun processInternalMessage(actor: Actor<M>, message: InternalMessage) {
        when (message) {
            Die -> {
                channel.close()
                actor.receive(Signal.PostStop)
            }
        }
    }
}

internal val userActorWorkerFactory: WorkerFactory =
    { self, channel, system -> UserActorWorker(self, channel, system.scope, system.watcher) }
